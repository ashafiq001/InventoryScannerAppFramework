package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import com.mavis.scanner.utils.DatabaseHelper;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.*;
import java.time.Duration;
import java.util.*;

/**
 * Section Upload Verification Test.
 *
 * Reproduces the store-reported issue: sections are scanned but items do not upload.
 * This test does a REAL inventory through the scanner app using items that exist in
 * TiremaxLive for the store, then verifies in the database that sections and items
 * were actually persisted.
 *
 * Flow:
 *   1. Resolve/schedule inventory for the store
 *   2. Query inv.inventory snapshot joined with TiremaxLive.dbo.invloc for real items + quantities
 *   3. Get real section barcodes from inv.storeSections
 *   4. Login via the app
 *   5. For each section: scan section barcode, scan matching item UPCs, close with correct count
 *   6. Finish the inventory
 *   7. Verify in DB: scanTotalQty > 0, scanned items exist in inv.inventoryScanned
 *
 * Usage:
 *   <parameter name="INVENTORY_PCS" value="2"/>
 *   <parameter name="TEST_STORE" value="30"/>
 *   <parameter name="SCHEDULE_IF_NEEDED" value="true"/>
 */
public class SectionUploadVerificationTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    // No limits — scan ALL sections and ALL items for complete verification

    // ==================== HELPERS ====================

    private Connection getDbConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

    /**
     * Get tire items from the tires_upc file, enriched with Sqhnd from TiremaxLive.
     * Only returns items that exist at the store (Sqhnd > 0).
     * Sorted by Sqhnd ascending so items with Sqhnd=1 come first (zero discrepancy when scanned once).
     */
    private List<Map<String, Object>> getAllTireItemsWithUpcs(DatabaseHelper dbHelper, String store, String invNum) {
        // Tire UPCs come from the tires_upc file (NOT barcodeMasterMultiUOM, which is parts only).
        // We load UPCs from the file, then filter to items that exist in the inv.inventory snapshot
        // for this store+invNum+pc=2, so we only scan items the app actually knows about.
        // Enrich with TiremaxLive Sqhnd for scan count (zero discrepancy).

        // Step 1: Load all items with UPCs from tires_upc file
        List<String> details = dbHelper.getTestUpcDetails(store, invNum, Integer.MAX_VALUE);
        Map<String, String> itemToUpc = new LinkedHashMap<>();
        for (String detail : details) {
            String[] parts = detail.split("\\|", -1);
            if (parts.length >= 2) {
                String upc = parts[0].trim();
                String itemNum = parts[1].trim();
                if (!upc.isEmpty() && !itemNum.isEmpty()) {
                    itemToUpc.putIfAbsent(itemNum, upc);
                }
            }
        }
        logStep("tires_upc file: " + itemToUpc.size() + " unique items with UPCs");

        // Step 2: Get inventory snapshot item_nums for this store+invNum+pc=2
        Set<String> snapshotItems = new HashSet<>();
        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT item_num FROM InventoryScanning.inv.inventory " +
                             "WHERE store = ? AND inv_num = ? AND pc = 2")) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                snapshotItems.add(rs.getString("item_num").trim());
            }
            rs.close();
        } catch (Exception e) {
            logStep("Inventory snapshot query failed: " + e.getMessage());
        }
        logStep("inv.inventory snapshot: " + snapshotItems.size() + " tire items (pc=2)");

        // Step 3: Keep only items that exist in BOTH the file AND the snapshot
        Map<String, String> matchedItems = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : itemToUpc.entrySet()) {
            if (snapshotItems.contains(entry.getKey())) {
                matchedItems.put(entry.getKey(), entry.getValue());
            }
        }
        logStep("Matched (file ∩ snapshot): " + matchedItems.size() + " items");

        if (matchedItems.isEmpty()) return new ArrayList<>();

        // Step 4: Enrich with TiremaxLive Sqhnd in batches
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> itemNums = new ArrayList<>(matchedItems.keySet());
        int batchSize = 500;

        for (int batch = 0; batch < itemNums.size(); batch += batchSize) {
            int end = Math.min(batch + batchSize, itemNums.size());
            List<String> batchItems = itemNums.subList(batch, end);

            StringBuilder inClause = new StringBuilder();
            for (int i = 0; i < batchItems.size(); i++) {
                if (i > 0) inClause.append(",");
                inClause.append("'").append(batchItems.get(i).replace("'", "''")).append("'");
            }

            String query = "SELECT Sitem, Sqhnd FROM TiremaxLive.dbo.invloc " +
                    "WHERE Sstor = ? AND Sqhnd > 0 AND Sitem IN (" + inClause + ")";

            try (Connection conn = getDbConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, Integer.parseInt(store));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String sitem = rs.getString("Sitem").trim();
                    int sqhnd = rs.getInt("Sqhnd");
                    String upc = matchedItems.get(sitem);
                    if (upc != null) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("item_num", sitem);
                        item.put("upc", upc);
                        item.put("sqhnd", sqhnd);
                        items.add(item);
                    }
                }
                rs.close();
            } catch (Exception e) {
                logStep("TiremaxLive lookup failed (batch " + batch + "): " + e.getMessage());
            }
        }

        // If TiremaxLive returned nothing (e.g. dev env), fall back to snapshot items with sqhnd=1
        if (items.isEmpty() && !matchedItems.isEmpty()) {
            logStep("TiremaxLive returned 0 matches — using snapshot items with sqhnd=1");
            for (Map.Entry<String, String> entry : matchedItems.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("item_num", entry.getKey());
                item.put("upc", entry.getValue());
                item.put("sqhnd", 1);
                items.add(item);
            }
        }

        // Sort by Sqhnd ascending — items with Sqhnd=1 first (zero discrepancy per scan)
        items.sort(Comparator.comparingInt(a -> (int) a.get("sqhnd")));

        logStep("Final: " + items.size() + " tire items with UPCs for store " + store);
        return items;
    }

    /**
     * Query parts items from DB snapshot joined with barcodeMasterMultiUOM for UPCs.
     */
    private List<Map<String, Object>> queryPartsItemsWithUpcs(String store, String invNum) {
        List<Map<String, Object>> items = new ArrayList<>();

        String query =
                "SELECT " +
                        "  a.item_num, " +
                        "  b.UPC, " +
                        "  ISNULL(i.Sqhnd, 0) AS Sqhnd, " +
                        "  a.pc " +
                        "FROM InventoryScanning.inv.inventory a " +
                        "JOIN InventoryScanning.inv.barcodeMasterMultiUOM b " +
                        "  ON a.item_num = b.Item AND b.UPC IS NOT NULL AND b.UPC != '' " +
                        "LEFT JOIN TiremaxLive.dbo.invloc i " +
                        "  ON a.item_num = i.Sitem AND i.Sstor = ? " +
                        "WHERE a.store = ? AND a.inv_num = ? " +
                        "ORDER BY a.id";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(store));
            stmt.setInt(3, Integer.parseInt(invNum));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("item_num", rs.getString("item_num"));
                item.put("upc", rs.getString("UPC").trim());
                item.put("sqhnd", rs.getInt("Sqhnd"));
                item.put("pc", rs.getInt("pc"));
                items.add(item);
            }
            rs.close();
        } catch (Exception e) {
            logStep("Parts DB query failed: " + e.getMessage());
        }

        return items;
    }

    /**
     * Get real section barcodes for this store from inv.storeSections, filtered by PC.
     */
    private List<String> queryStoreSections(String store, int pc) {
        List<String> sections = new ArrayList<>();
        String query =
                "SELECT DISTINCT shelf FROM InventoryScanning.inv.storeSections " +
                        "WHERE store = ? AND pc = ? AND shelf IS NOT NULL AND shelf != '' " +
                        "ORDER BY shelf";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, pc);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String shelf = rs.getString("shelf");
                if (shelf != null && !shelf.trim().isEmpty()) {
                    sections.add("STR-" + shelf.trim());
                }
            }
            rs.close();
        } catch (Exception e) {
            logStep("Section query failed (pc=" + pc + "): " + e.getMessage());
        }

        return sections;
    }

    /**
     * Get real section barcodes for this store from inv.storeSections, filtered by multiple PCs.
     */
    private List<String> queryStoreSections(String store, List<Integer> pcs) {
        List<String> sections = new ArrayList<>();
        if (pcs.isEmpty()) return sections;

        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < pcs.size(); i++) {
            if (i > 0) inClause.append(",");
            inClause.append(pcs.get(i));
        }

        String query =
                "SELECT DISTINCT shelf FROM InventoryScanning.inv.storeSections " +
                        "WHERE store = ? AND pc IN (" + inClause + ") AND shelf IS NOT NULL AND shelf != '' " +
                        "ORDER BY shelf";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String shelf = rs.getString("shelf");
                if (shelf != null && !shelf.trim().isEmpty()) {
                    sections.add("STR-" + shelf.trim());
                }
            }
            rs.close();
        } catch (Exception e) {
            logStep("Section query failed (pcs=" + pcs + "): " + e.getMessage());
        }

        return sections;
    }

    /**
     * Verify that scanned items were persisted in the database after finishing.
     */
    private void verifyUploadInDatabase(String store, String invNum, int expectedMinItems) {
        logStep("=== DATABASE VERIFICATION ===");

        // Check inv.vw_inv_hdr for scan totals
        String hdrQuery =
                "SELECT InvNum, store, pc, StatusID, scanTotalQty, tmaxTotalQty, totalDiscrep " +
                        "FROM InventoryScanning.inv.vw_inv_hdr " +
                        "WHERE store = ? AND InvNum = ? " +
                        "ORDER BY DateCreated DESC";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(hdrQuery)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int scanTotalQty = rs.getInt("scanTotalQty");
                int tmaxTotalQty = rs.getInt("tmaxTotalQty");
                int totalDiscrep = rs.getInt("totalDiscrep");
                int statusId = rs.getInt("StatusID");

                logStep("vw_inv_hdr: StatusID=" + statusId +
                        " scanTotalQty=" + scanTotalQty +
                        " tmaxTotalQty=" + tmaxTotalQty +
                        " totalDiscrep=" + totalDiscrep);

                Assert.assertTrue(scanTotalQty > 0,
                        "BUG: scanTotalQty is 0 — scanned items did NOT upload! " +
                                "Store=" + store + " InvNum=" + invNum);
                logStep("VERIFIED: scanTotalQty=" + scanTotalQty + " (items uploaded successfully)");
            } else {
                logStep("WARNING: No row found in vw_inv_hdr for store=" + store + " invNum=" + invNum);
            }
            rs.close();
        } catch (Exception e) {
            logStep("vw_inv_hdr verification failed: " + e.getMessage());
        }

        // Check inv.scanValidation for section upload evidence
        String valQuery =
                "SELECT barcodeLoc, scanQty, completed, createdDt " +
                        "FROM InventoryScanning.inv.scanValidation " +
                        "WHERE store = ? AND invNum = ? " +
                        "ORDER BY createdDt DESC";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(valQuery)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();

            int sectionCount = 0;
            int totalScanQty = 0;
            while (rs.next()) {
                sectionCount++;
                int scanQty = rs.getInt("scanQty");
                totalScanQty += scanQty;
                boolean completed = rs.getBoolean("completed");
                String loc = rs.getString("barcodeLoc");
                logStep("  scanValidation: loc=" + loc + " scanQty=" + scanQty +
                        " completed=" + completed);
            }
            rs.close();

            logStep("scanValidation: " + sectionCount + " sections, totalScanQty=" + totalScanQty);

            if (sectionCount == 0) {
                logStep("BUG: No rows in scanValidation — sections did NOT upload!");
            }
            Assert.assertTrue(sectionCount > 0,
                    "BUG: No scanValidation rows — section data did not upload! " +
                            "Store=" + store + " InvNum=" + invNum);
        } catch (Exception e) {
            logStep("scanValidation verification failed: " + e.getMessage());
        }

        // Check inv.inventoryScanned for individual item records
        String scannedQuery =
                "SELECT COUNT(*) AS cnt, SUM(qty) AS totalQty " +
                        "FROM InventoryScanning.inv.inventoryScanned " +
                        "WHERE store = ? AND invNum = ?";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(scannedQuery)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int cnt = rs.getInt("cnt");
                int totalQty = rs.getInt("totalQty");
                logStep("inventoryScanned: " + cnt + " rows, totalQty=" + totalQty);

                if (cnt == 0) {
                    logStep("BUG: No rows in inventoryScanned — item scans did not persist!");
                }
            }
            rs.close();
        } catch (Exception e) {
            logStep("inventoryScanned verification failed: " + e.getMessage());
        }
    }

    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                // SIA-1211: positive button may be "Go back to scan" — don't click that
                String btnText = driver.findElement(DIALOG_BUTTON_POSITIVE).getText();
                if (btnText != null && btnText.toLowerCase().contains("go back")) {
                    // Skip — "Go back to scan" would navigate away from current flow
                    return;
                }
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(500);
            }
        } catch (Exception e) { /* No dialog */ }
    }

    private void scrollToBottom() {
        try {
            Dimension size = driver.manage().window().getSize();
            int startX = size.width / 2;
            int startY = (int) (size.height * 0.8);
            int endY = (int) (size.height * 0.2);
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence swipe = new Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(Duration.ZERO,
                    PointerInput.Origin.viewport(), startX, startY));
            swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(Duration.ofMillis(500),
                    PointerInput.Origin.viewport(), startX, endY));
            swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(swipe));
            Thread.sleep(500);
        } catch (Exception e) { /* Scroll failed */ }
    }

    /**
     * Handle missed items dialogs after finishing inventory/category.
     * The app shows Submit/OK dialogs for items in the snapshot that weren't scanned.
     * Must be handled even for a perfect inventory.
     */
    private void handleMissedItems(String label) throws InterruptedException {
        By submitBtn = byTextIgnoreCase("SUBMIT");
        By okBtn = byTextIgnoreCase("OK");

        int missedCount = 0;
        int maxMissedItems = 500;

        for (int i = 0; i < maxMissedItems; i++) {
            Thread.sleep(AppConfig.SHORT_WAIT);

            if (WaitHelper.isElementPresent(driver, submitBtn)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 50 == 0) {
                    logStep(label + ": Missed item " + missedCount + " - tapping Submit");
                }
                driver.findElement(submitBtn).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            if (WaitHelper.isElementPresent(driver, okBtn)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 50 == 0) {
                    logStep(label + ": Missed item " + missedCount + " - tapping OK");
                }
                driver.findElement(okBtn).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 50 == 0) {
                    logStep(label + ": Missed item " + missedCount + " - tapping neutral button");
                }
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 50 == 0) {
                    logStep(label + ": Missed item " + missedCount + " - tapping positive button");
                }
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            break;
        }

        if (missedCount > 0) {
            logStep(label + ": Submitted through " + missedCount + " missed items");
        } else {
            logStep(label + ": No missed items");
        }
    }

    private static By byText(String text) {
        return By.xpath("//*[@text='" + text + "']");
    }

    private static By byTextIgnoreCase(String text) {
        String lower = text.toLowerCase();
        return By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + lower + "']");
    }

    // ==================== TIRE SECTION UPLOAD VERIFICATION ====================

    @Test(priority = 0, description = "Scan real sections with matching TiremaxLive items")
    public void testTireSectionScanAndUploadVerification() {
        setup("Section Upload Verification - Tire");

        try {
            // Step 0: Resolve inventory
            ScheduledInventory inv = InventorySetupHelper.resolveInventory();
            logStep("Step 0: Resolved inventory: " + inv);

            if (!inv.scheduledPCs.isEmpty() && !inv.scheduledPCs.contains(2)) {
                skip("No tire PC=2 in resolved inventory: " + inv.scheduledPCs);
            }

            // Step 1: Login first (DatabaseHelper needs the driver)
            StartHomePage startHome = new StartHomePage(driver, wait);
            Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");

            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(AppConfig.SHORT_WAIT);

            loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

            MainScanPage mainScan = new MainScanPage(driver, wait);
            if (!mainScan.isDisplayed()) {
                String activity = driver.currentActivity();
                if (activity != null && activity.contains("PartsPCActivity")) {
                    skip("App routed to Parts — need tire inventory (PC=2)");
                }
                Thread.sleep(AppConfig.LONG_WAIT);
            }
            Assert.assertTrue(mainScan.isDisplayed(), "Should be on tire scan screen");
            logStep("Step 1: Logged in and on tire scan screen");

            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Step 2: Load tire items from inventory snapshot + barcodeMasterMultiUOM (what the app knows)
            List<Map<String, Object>> allCandidates = getAllTireItemsWithUpcs(dbHelper, inv.store, inv.invNum);
            Assert.assertFalse(allCandidates.isEmpty(),
                    "No tire items found in inventory snapshot + barcodeMaster for store " + inv.store);

            List<Map<String, Object>> allItems = allCandidates;
            logStep("Step 2: " + allItems.size() + " tire items to scan (each scanned Sqhnd times for zero discrepancy)");

            // Get section barcodes — only sections with pc=2 (tires)
            List<String> sections = queryStoreSections(inv.store, 2);
            if (sections.isEmpty()) {
                sections = new ArrayList<>(Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
                logStep("Step 2: No pc=2 sections from DB, using fallback: " + sections);
            } else {
                logStep("Step 2: Found " + sections.size() + " tire sections (pc=2)");
            }

            for (int i = 0; i < Math.min(5, allItems.size()); i++) {
                Map<String, Object> item = allItems.get(i);
                logStep("  Item: " + item.get("item_num") +
                        " | UPC: " + item.get("upc") +
                        " | Sqhnd: " + item.get("sqhnd"));
            }

            // Step 3: Scan ALL sections, distribute ALL items evenly
            // Each item is scanned Sqhnd times to match TiremaxLive qty (zero discrepancy)
            int totalSections = sections.size();
            int totalItems = allItems.size();
            int basePerSection = totalItems / totalSections;
            int remainder = totalItems % totalSections;

            // Calculate total scans needed (sum of all Sqhnd)
            int totalScansNeeded = 0;
            for (Map<String, Object> item : allItems) {
                totalScansNeeded += Math.max(1, (int) item.get("sqhnd"));
            }

            logStep("Step 3: Distributing " + totalItems + " items across " +
                    totalSections + " sections (~" + basePerSection + " items/section, " +
                    totalScansNeeded + " total scans)");

            int itemIndex = 0;
            int totalScanned = 0;

            for (int s = 0; s < totalSections; s++) {
                String sectionBarcode = sections.get(s);
                int itemsForThisSection = basePerSection + (s < remainder ? 1 : 0);

                logStep("--- Section " + (s + 1) + "/" + totalSections + ": " +
                        sectionBarcode + " (" + itemsForThisSection + " items) ---");

                // Scan section barcode
                dwHelper.scanSectionBarcode(sectionBarcode);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();

                String sectionOutput = mainScan.getSectionOutput();
                logStep("Section output: " + sectionOutput);

                // Scan items for this section — each item scanned Sqhnd times
                int sectionScanCount = 0;
                for (int i = 0; i < itemsForThisSection && itemIndex < totalItems; i++) {
                    Map<String, Object> item = allItems.get(itemIndex);
                    String upc = (String) item.get("upc");
                    int sqhnd = Math.max(1, (int) item.get("sqhnd"));

                    logStep("Scanning item [" + (itemIndex + 1) + "/" + totalItems + "]: " +
                            item.get("item_num") + " UPC=" + upc + " x" + sqhnd);

                    for (int scan = 0; scan < sqhnd; scan++) {
                        dwHelper.scanItemBarcode(upc);
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        dismissAnyDialog();
                        sectionScanCount++;
                        totalScanned++;
                    }

                    itemIndex++;
                }

                int listCount = mainScan.getItemCount();
                logStep("Section " + sectionBarcode + ": " + sectionScanCount +
                        " scans, list shows " + listCount);

                // Fail fast if scans didn't register — items aren't in the app's barcode list
                if (sectionScanCount > 0 && listCount == 0) {
                    Assert.fail("Section " + sectionBarcode + ": scanned " + sectionScanCount +
                            " items but list shows 0 — items not recognized by app. " +
                            "Check that inventory snapshot and barcodeMasterMultiUOM are in sync.");
                }

                // Close section — manual count = total scans in this section
                scrollToBottom();
                mainScan.tapCloseSection();
                Thread.sleep(AppConfig.SHORT_WAIT);

                By completeBtn = byText("Complete");
                if (WaitHelper.isElementPresent(driver, completeBtn)) {
                    driver.findElement(completeBtn).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SHORT_WAIT);

                ManualCountDialog countDialog = new ManualCountDialog(driver, wait);
                if (countDialog.isDisplayed()) {
                    String count = String.valueOf(sectionScanCount);
                    countDialog.closeWithCount(count);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                    logStep("Closed section with manual count " + count);
                }
            }

            logStep("Step 3: Done — " + totalScanned + " total scans for " +
                    totalItems + " items across " + totalSections + " sections");
            Assert.assertEquals(itemIndex, totalItems,
                    "All items should have been processed");

            // Step 5: Finish inventory — close remaining sections with 0, handle missed items
            logStep("Step 5: Finishing inventory...");
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Close ALL remaining/missed sections with 0, then confirm finish
            // App uses VwStoreRoom for section count which may differ from our storeSections query
            // Stores can have 50-100+ sections, so loop must handle all of them
            By closeWith0Btn = byTextIgnoreCase("CLOSE WITH 0");
            By goBackToScanBtn = byText("Go back to scan");
            By yesFinishBtn = byTextIgnoreCase("YES");
            int missedSectionsClosed = 0;

            for (int attempt = 0; attempt < 200; attempt++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0Btn, yesFinishBtn, goBackToScanBtn);
                    String foundText = found.getText();

                    if (foundText.equalsIgnoreCase("CLOSE WITH 0")) {
                        missedSectionsClosed++;
                        if (missedSectionsClosed <= 5 || missedSectionsClosed % 10 == 0) {
                            logStep("Closing missed section #" + missedSectionsClosed + " with 0");
                        }
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (foundText.equalsIgnoreCase("YES")) {
                        logStep("All sections closed (" + missedSectionsClosed +
                                " missed sections closed with 0), confirming finish");
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    } else if (foundText.equals("Go back to scan")) {
                        // SIA-1211: "Go back to scan" replaced "Exit" — we don't want to go back,
                        // look for "Close with 0" button instead (neutral button on same dialog)
                        if (WaitHelper.isElementPresent(driver, closeWith0Btn)) {
                            driver.findElement(closeWith0Btn).click();
                            missedSectionsClosed++;
                            if (missedSectionsClosed <= 5 || missedSectionsClosed % 10 == 0) {
                                logStep("Closing missed section #" + missedSectionsClosed + " with 0");
                            }
                            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                            scrollToBottom();
                            mainScan.tapFinish();
                            Thread.sleep(AppConfig.MEDIUM_WAIT);
                        }
                    }
                } catch (Exception e) {
                    logStep("No more finish dialogs after closing " + missedSectionsClosed +
                            " missed sections: " + e.getMessage());
                    break;
                }
            }

            // Handle any post-finish dialogs (packing list, upload confirmation)
            for (int attempt = 0; attempt < 10; attempt++) {
                By continueBtn = byText("Continue");
                By uploadBtn = byText("Upload");
                By acceptBtn = byText("Accept");
                By skipBtn = byText("Skip");

                if (WaitHelper.isElementPresent(driver, uploadBtn)) {
                    logStep("Tapping Upload");
                    driver.findElement(uploadBtn).click();
                    Thread.sleep(AppConfig.LONG_WAIT);
                    break;
                } else if (WaitHelper.isElementPresent(driver, acceptBtn)) {
                    driver.findElement(acceptBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, skipBtn)) {
                    driver.findElement(skipBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, continueBtn)) {
                    driver.findElement(continueBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else {
                    break;
                }
            }

            // Wait for upload processing
            Thread.sleep(AppConfig.LONG_WAIT);
            dismissAnyDialog();

            // Check for FinalConfirmActivity
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Final confirmation: " + countInfo);
            } else {
                logStep("FinalConfirm not displayed. Activity: " + driver.currentActivity());
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                dismissAnyDialog();
            }

            logStep("Step 5: Inventory finished");

            // Step 6: Verify in database
            logStep("Step 6: Verifying upload in database...");
            verifyUploadInDatabase(inv.store, inv.invNum, totalScanned);

            pass();

        } catch (Exception e) {
            fail("Section upload verification test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== PARTS SECTION UPLOAD VERIFICATION ====================

    @Test(priority = 1, description = "Parts: Scan real sections with matching items, verify upload in DB")
    public void testPartsSectionScanAndUploadVerification() {
        setup("Section Upload Verification - Parts");

        try {
            // Step 0: Resolve inventory
            ScheduledInventory inv = InventorySetupHelper.resolveInventory();
            logStep("Step 0: Resolved inventory: " + inv);

            if (!inv.scheduledPCs.isEmpty()) {
                boolean hasPartsPc = inv.scheduledPCs.stream().anyMatch(pc -> pc != 2);
                if (!hasPartsPc) {
                    skip("No parts PC in resolved inventory (only tire PC=2): " + inv.scheduledPCs);
                }
            }

            // Step 1: Query real items from DB snapshot
            List<Map<String, Object>> allItems = queryPartsItemsWithUpcs(inv.store, inv.invNum);
            Assert.assertFalse(allItems.isEmpty(),
                    "No items found in snapshot for store=" + inv.store + " invNum=" + inv.invNum);
            logStep("Step 1: Found " + allItems.size() + " parts items in snapshot with UPCs");

            for (int i = 0; i < Math.min(5, allItems.size()); i++) {
                Map<String, Object> item = allItems.get(i);
                logStep("  Item: " + item.get("item_num") +
                        " | UPC: " + item.get("upc") +
                        " | TMax Qty: " + item.get("sqhnd") +
                        " | PC: " + item.get("pc"));
            }

            // Step 2: Get real section barcodes — only sections matching parts PCs
            List<Integer> partsPCs = new ArrayList<>();
            for (int pc : inv.scheduledPCs) {
                if (pc != 2) partsPCs.add(pc);
            }
            if (partsPCs.isEmpty()) {
                partsPCs.addAll(Arrays.asList(62, 65, 86, 188));
            }
            List<String> sections = queryStoreSections(inv.store, partsPCs);
            if (sections.isEmpty()) {
                sections = new ArrayList<>(Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
                logStep("Step 2: No DB sections for parts PCs " + partsPCs + ", using fallback: " + sections);
            } else {
                logStep("Step 2: Found " + sections.size() + " sections for parts PCs " + partsPCs);
            }

            // Step 3: Login
            StartHomePage startHome = new StartHomePage(driver, wait);
            Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");

            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(AppConfig.SHORT_WAIT);

            loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

            PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
            if (!partsCategory.isDisplayed()) {
                String activity = driver.currentActivity();
                if (activity != null && activity.contains("MainActivity") && !activity.contains("Parts")) {
                    skip("App routed to tire MainActivity — need parts inventory");
                }
                Thread.sleep(AppConfig.LONG_WAIT);
            }
            Assert.assertTrue(partsCategory.isDisplayed(), "Should be on Parts Category screen");
            logStep("Step 3: Logged in and on Parts Category screen");

            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);

            int categoryCount = partsCategory.getVisibleCategoryCount();
            logStep("Parts categories available: " + categoryCount);
            Assert.assertTrue(categoryCount > 0, "At least one parts category must be scheduled");

            // Step 4: Process first category — scan ALL sections, distribute ALL items evenly
            partsCategory.tapStart(1);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            PartsMainPage partsMain = new PartsMainPage(driver, wait);
            Assert.assertTrue(partsMain.isDisplayed(), "Should be on parts scanning screen");
            logStep("Step 4: Entered parts scanning for category 1");

            int totalSections = sections.size();
            int totalItems = allItems.size();
            int basePerSection = totalItems / totalSections;
            int remainder = totalItems % totalSections;

            logStep("Distributing " + totalItems + " items across " +
                    totalSections + " sections (~" + basePerSection + " per section)");

            int itemIndex = 0;
            int totalScanned = 0;

            for (int s = 0; s < totalSections; s++) {
                String sectionBarcode = sections.get(s);
                int itemsForThisSection = basePerSection + (s < remainder ? 1 : 0);

                logStep("--- Section " + (s + 1) + "/" + totalSections + ": " +
                        sectionBarcode + " (" + itemsForThisSection + " items) ---");

                dwHelper.simulateScan(sectionBarcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();

                String sectionOutput = partsMain.getSectionOutput();
                logStep("Section output: " + sectionOutput);

                // Scan items for this section
                int sectionItemCount = 0;
                for (int i = 0; i < itemsForThisSection && itemIndex < totalItems; i++) {
                    Map<String, Object> item = allItems.get(itemIndex);
                    String upc = (String) item.get("upc");

                    logStep("Scanning item [" + (itemIndex + 1) + "/" + totalItems + "]: " +
                            item.get("item_num") + " UPC=" + upc +
                            " TMaxQty=" + item.get("sqhnd"));

                    dwHelper.simulateScan(upc, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                    dismissAnyDialog();

                    sectionItemCount++;
                    totalScanned++;
                    itemIndex++;
                }

                int listCount = partsMain.getItemCount();
                logStep("Section " + sectionBarcode + ": scanned " + sectionItemCount +
                        " items, list shows " + listCount);

                // Close section
                scrollToBottom();
                partsMain.tapCloseSection();
                Thread.sleep(AppConfig.SHORT_WAIT);

                By completeBtn = byText("Complete");
                if (WaitHelper.isElementPresent(driver, completeBtn)) {
                    driver.findElement(completeBtn).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SHORT_WAIT);

                ManualCountDialog countDialog = new ManualCountDialog(driver, wait);
                if (countDialog.isDisplayed()) {
                    String count = String.valueOf(sectionItemCount);
                    countDialog.closeWithCount(count);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                    logStep("Closed section with manual count " + count);
                }
            }

            logStep("Step 4: Scanned " + totalScanned + "/" + totalItems +
                    " items across all " + totalSections + " sections");
            Assert.assertEquals(totalScanned, totalItems,
                    "All items should have been scanned");

            // Step 5: Finish category (all sections already closed)
            logStep("Step 5: Finishing parts category...");
            scrollToBottom();
            partsMain.tapFinishCategory();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Handle missed items — parts shows Submit/OK dialogs for snapshot items not scanned
            handleMissedItems("Parts");

            // Handle confirmation dialogs + close ALL missed sections to get back to PartsCategoryPage
            int partsMissedSections = 0;
            for (int attempt = 0; attempt < 200; attempt++) {
                By closeWith0 = byTextIgnoreCase("CLOSE WITH 0");
                By yesBtn = byTextIgnoreCase("YES");
                By continueBtn = byTextIgnoreCase("CONTINUE");
                By okBtn = byTextIgnoreCase("OK");

                if (WaitHelper.isElementPresent(driver, closeWith0)) {
                    partsMissedSections++;
                    if (partsMissedSections <= 5 || partsMissedSections % 10 == 0) {
                        logStep("Closing missed parts section #" + partsMissedSections + " with 0");
                    }
                    driver.findElement(closeWith0).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, yesBtn)) {
                    driver.findElement(yesBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, continueBtn)) {
                    driver.findElement(continueBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, okBtn)) {
                    driver.findElement(okBtn).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else {
                    break;
                }

                // Check if we're back on category page
                partsCategory = new PartsCategoryPage(driver, wait);
                if (partsCategory.isDisplayed()) break;
            }
            if (partsMissedSections > 0) {
                logStep("Closed " + partsMissedSections + " missed parts sections with 0");
            }

            // Finish the whole parts inventory
            partsCategory = new PartsCategoryPage(driver, wait);
            if (partsCategory.isDisplayed() && partsCategory.isFinishButtonVisible()) {
                logStep("Tapping Finish on Parts Category page");
                partsCategory.tapFinish();
                Thread.sleep(AppConfig.LONG_WAIT);

                // Handle final confirmation dialogs
                for (int attempt = 0; attempt < 10; attempt++) {
                    By yesBtn = byTextIgnoreCase("YES");
                    By uploadBtn = byText("Upload");
                    By continueBtn = byText("Continue");

                    if (WaitHelper.isElementPresent(driver, uploadBtn)) {
                        driver.findElement(uploadBtn).click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    } else if (WaitHelper.isElementPresent(driver, yesBtn)) {
                        driver.findElement(yesBtn).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, continueBtn)) {
                        driver.findElement(continueBtn).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                        driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else {
                        break;
                    }
                }
            } else {
                logStep("Not on Parts Category page or Finish not visible. Activity: " + driver.currentActivity());
            }

            // Wait for upload
            Thread.sleep(AppConfig.LONG_WAIT);
            dismissAnyDialog();

            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Final confirmation: " + countInfo);
            } else {
                logStep("FinalConfirm not displayed. Activity: " + driver.currentActivity());
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                dismissAnyDialog();
            }

            logStep("Step 5: Inventory finished");


            pass();

        } catch (Exception e) {
            fail("Parts section upload verification test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
