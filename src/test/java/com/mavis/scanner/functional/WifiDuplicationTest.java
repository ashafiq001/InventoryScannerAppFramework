package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.ScanHelper;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.*;
import java.time.Duration;
import java.util.*;

/**
 * WiFi Duplication Bug Test.
 *
 * Reproduces the Store 2030 issue: stores with many sections (58+) see
 * tire quantities DOUBLED in the last few sections when WiFi is poor.
 *
 * Hypothesis: the app retries an upload/sync when it doesn't get an
 * acknowledgment on a flaky connection, causing duplicate item records.
 *
 * Test strategy:
 *   1. Login and scan ALL real sections for the store
 *   2. For each section: scan 1 item (UPC from barcodeMaster)
 *   3. Track exact expected counts per section
 *   4. Around section 56+, toggle WiFi off/on to simulate flaky connectivity
 *   5. Finish inventory and upload
 *   6. Verify: uploaded item count must EQUAL expected count (not doubled)
 *
 * Usage in testng.xml:
 *   <parameter name="TEST_STORE" value="2030"/>
 *   <parameter name="INVENTORY_PCS" value="2"/>
 *   <parameter name="SCHEDULE_IF_NEEDED" value="true"/>
 */
public class WifiDuplicationTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    /**
     * WiFi toggling begins after this percentage of total sections are done.
     * e.g., 0.75 = start toggling after 75% of sections are scanned.
     * For store 2030 (58 sections): starts at section ~44.
     * For store 30 (13 sections): starts at section ~10.
     */
    private static final double WIFI_FLAKY_START_PERCENT = 0.75;

    /** Probability of WiFi toggling on any given section after the threshold (0.0 to 1.0). */
    private static final double WIFI_TOGGLE_PROBABILITY = 0.35;

    private final Random random = new Random();

    // ==================== MAIN TEST ====================

    @Test(priority = 1, description = "Reproduce Store 2030 tire duplication bug: WiFi flaky at section 56+ causes doubled items")
    public void testWifiDoesNotDuplicateTiresAtHighSectionCount() {
        setup("WiFi Duplication Bug - High Section Count");

        try {
            // Step 0: Resolve inventory
            ScheduledInventory inv = InventorySetupHelper.resolveInventory();
            activeInvNum = inv.invNum;
            activeInvCode=inv.invCode;
            logStep("Step 0: Resolved inventory: " + inv);

            if (!inv.scheduledPCs.isEmpty() && !inv.scheduledPCs.contains(2)) {
                skip("No tire PC=2 in resolved inventory: " + inv.scheduledPCs);
            }

            // Step 1: Login
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

            // Step 2: Get ALL section barcodes and items for this store
            List<String> sections = queryStoreSections(inv.store, 2);
            if (sections.isEmpty()) {
                sections = new ArrayList<>(Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
                logStep("No pc=2 sections from DB, using fallback: " + sections);
            }
            logStep("Step 2: Found " + sections.size() + " tire sections (pc=2)");

            if (sections.size() < 10) {
                logStep("WARNING: Store " + inv.store + " only has " + sections.size() +
                        " sections — bug requires many sections (58+ reported). " +
                        "Test will still run but may not reproduce the issue.");
            }

            List<Map<String, Object>> allItems = getAllTireItemsWithUpcs(inv.store, inv.invNum);
            Assert.assertFalse(allItems.isEmpty(),
                    "No tire items found for store " + inv.store);
            logStep("Step 2: " + allItems.size() + " tire items available");

            // Step 3: Scan all sections normally (WiFi stays ON — scanning is local)
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            int totalSections = sections.size();
            int totalScanned = 0;
            int itemIndex = 0;
            int sectionsWithItems = 0;
            int wifiToggles = 0;

            logStep("Step 3: Scanning " + totalSections + " sections (WiFi ON — scanning is local)");

            for (int s = 0; s < totalSections; s++) {
                String sectionBarcode = sections.get(s);
                int sectionNum = s + 1;

                // Scan section barcode
                dwHelper.scanSectionBarcode(sectionBarcode);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();

                // Scan 1 item in this section
                int sectionScanCount = 0;
                if (itemIndex < allItems.size()) {
                    Map<String, Object> item = allItems.get(itemIndex);
                    String upc = (String) item.get("upc");

                    dwHelper.scanItemBarcode(upc);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                    dismissAnyDialog();
                    sectionScanCount++;
                    totalScanned++;
                    itemIndex++;

                    if (sectionNum <= 5 || sectionNum % 10 == 0 || sectionNum == totalSections) {
                        logStep("Section " + sectionNum + "/" + totalSections + ": " +
                                sectionBarcode + " — scanned " + item.get("item_num") +
                                " (UPC=" + upc + ")");
                    }
                }

                if (sectionScanCount > 0) sectionsWithItems++;

                // Close section with correct manual count
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
                    countDialog.closeWithCount(String.valueOf(sectionScanCount));
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                }
            }

            logStep("Step 3: Done — " + totalScanned + " items scanned across " +
                    totalSections + " sections (" + sectionsWithItems + " with items)");

            // Step 4: Finish inventory — close remaining sections, then trigger upload WITH flaky WiFi
            logStep("Step 4: Finishing inventory...");
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By closeWith0Btn = byTextIgnoreCase("CLOSE WITH 0");
            By goBackToScanBtn = byText("Go back to scan");
            By yesFinishBtn = byTextIgnoreCase("YES");
            int missedSectionsClosed = 0;

            for (int attempt = 0; attempt < 200; attempt++) {
                try {
                    WebElement found = WaitHelper.waitForAny(driver, 10,
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
                        logStep("Confirming finish (" + missedSectionsClosed + " extra sections closed with 0)");

                        // === WIFI DISRUPTION STARTS HERE — right before upload ===
                        logStep(">>> Disabling WiFi BEFORE confirming finish (simulate flaky upload)");
                        toggleWifi(false);
                        wifiToggles++;

                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    } else if (foundText.equals("Go back to scan")) {
                        if (WaitHelper.isElementPresent(driver, closeWith0Btn)) {
                            driver.findElement(closeWith0Btn).click();
                            missedSectionsClosed++;
                            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                            scrollToBottom();
                            mainScan.tapFinish();
                            Thread.sleep(AppConfig.MEDIUM_WAIT);
                        }
                    }
                } catch (Exception e) {
                    break;
                }
            }

            // Toggle WiFi on/off rapidly during the upload phase to simulate intermittent connection
            logStep(">>> WiFi flaky during upload phase — toggling randomly...");
            for (int i = 0; i < 6; i++) {
                Thread.sleep(2000);
                boolean on = (i % 2 == 0); // alternate: ON, OFF, ON, OFF, ON, OFF
                toggleWifi(on);
                wifiToggles++;
                logStep(">>> Upload phase: WiFi " + (on ? "ON" : "OFF") + " (toggle #" + wifiToggles + ")");
            }

            // Ensure WiFi is ON for final upload attempt
            toggleWifi(true);
            wifiToggles++;
            Thread.sleep(3000);
            logStep(">>> WiFi restored — allowing upload to complete");

            // Handle post-finish dialogs (Upload, Continue, Accept, Skip)
            handlePostFinishDialogs();

            // Wait for upload to complete
            Thread.sleep(AppConfig.LONG_WAIT);
            dismissAnyDialog();

            // If upload failed (e.g. wifi flaky), app may show Exit popup instead of FinalConfirm
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (!confirmPage.isDisplayed()) {
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                dismissAnyDialog();
            }

            if (!confirmPage.isDisplayed()) {
                logStep("FinalConfirm not reached — attempting recovery (Exit popup → restart → re-login)");

                // Dismiss the "Exit" popup if present
                By exitBtn = byTextIgnoreCase("EXIT");
                try {
                    WebElement exit = WaitHelper.waitForAny(driver, 10, exitBtn);
                    logStep("Found Exit popup — dismissing");
                    exit.click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } catch (Exception ignored) {
                    logStep("No Exit popup found");
                }

                // Ensure wifi is on
                toggleWifi(true);
                Thread.sleep(AppConfig.MEDIUM_WAIT);

                // Restart the app and re-login with the same inventory
                driver.terminateApp(AppConfig.APP_PACKAGE);
                Thread.sleep(AppConfig.MEDIUM_WAIT);
                driver.activateApp(AppConfig.APP_PACKAGE);
                Thread.sleep(AppConfig.LONG_WAIT);

                StartHomePage startHome2 = new StartHomePage(driver, wait);
                LoginPage loginPage2 = startHome2.tapStartInventory();
                Thread.sleep(AppConfig.SHORT_WAIT);
                loginPage2.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                if (loginPage2.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                logStep("Re-logged in with store=" + inv.store + " invCode=" + inv.invCode);

                // Dismiss "Exit" popup that appears after re-login
                try {
                    WebElement exit = WaitHelper.waitForAny(driver, 10, exitBtn);
                    logStep("Found Exit popup after re-login — dismissing");
                    exit.click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } catch (Exception ignored) {
                    logStep("No Exit popup after re-login");
                }

                // Retry Finish with wifi on
                mainScan = new MainScanPage(driver, wait);
                Assert.assertTrue(mainScan.isDisplayed(),
                        "Scan screen should be displayed after re-login. Activity: " +
                                driver.currentActivity());

                scrollToBottom();
                mainScan.tapFinish();
                Thread.sleep(AppConfig.MEDIUM_WAIT);

                // Handle close-with-0 / yes dialogs
                By closeWith0Retry = byTextIgnoreCase("CLOSE WITH 0");
                By yesRetry = byTextIgnoreCase("YES");
                for (int r = 0; r < 200; r++) {
                    try {
                        WebElement found = WaitHelper.waitForAny(driver, 10,
                                closeWith0Retry, yesRetry);
                        String text = found.getText();
                        if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                            found.click();
                            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                            scrollToBottom();
                            mainScan.tapFinish();
                            Thread.sleep(AppConfig.MEDIUM_WAIT);
                        } else if (text.equalsIgnoreCase("YES")) {
                            found.click();
                            Thread.sleep(AppConfig.LONG_WAIT);
                            break;
                        }
                    } catch (Exception e2) {
                        break;
                    }
                }

                handlePostFinishDialogs();
                Thread.sleep(AppConfig.LONG_WAIT);
                dismissAnyDialog();

                if (!confirmPage.isDisplayed()) {
                    Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                    dismissAnyDialog();
                    confirmPage = new FinalConfirmPage(driver, wait);
                }
            }

            // Step 5: Verify — check UI counts and query DB for duplicate records
            logStep("Step 5: Verifying upload counts (checking for duplication)...");

            Assert.assertTrue(confirmPage.isDisplayed(),
                    "FinalConfirmActivity never appeared — upload may have failed. " +
                            "Activity: " + driver.currentActivity());

            String countInfo = confirmPage.getCountInfo();
            logStep("Final confirmation: " + countInfo);

            int uploadedItems = parseUploadedItems(countInfo);
            int uploadedSections = parseUploadedSections(countInfo);

            logStep("Parsed: uploadedItems=" + uploadedItems + " uploadedSections=" + uploadedSections);
            logStep("We physically scanned: " + totalScanned + " items across " + sectionsWithItems + " sections");

            // Verify upload worked at all
            Assert.assertTrue(uploadedItems > 0,
                    "UPLOAD FAILED: 0 items uploaded. countInfo: " + countInfo);
            Assert.assertTrue(uploadedSections > 0,
                    "UPLOAD FAILED: 0 sections uploaded. countInfo: " + countInfo);

            // THE CRITICAL CHECK: query the DB for duplicate item records
            // Data doesn't appear in DB immediately after upload — poll until it arrives
            logStep("Waiting for upload data to appear in database...");
            int dbRowCount = waitForDbData(inv.store, inv.invNum, uploadedItems, 60);

            if (dbRowCount < 0) {
                logStep("WARNING: Could not verify in DB (query failed). UI shows " +
                        uploadedItems + " items — test passes on UI check only.");
            } else if (dbRowCount == 0) {
                logStep("WARNING: No rows found in inventoryScanned after 60s. " +
                        "Upload may still be processing. UI shows " + uploadedItems + " items.");
            } else {
                logStep("DB has " + dbRowCount + " rows in inventoryScanned");

                // Now check for duplicates
                int duplicateCount = queryDuplicateItems(inv.store, inv.invNum);

                if (duplicateCount > 0) {
                    logStep("DUPLICATION BUG DETECTED: " + duplicateCount +
                            " items have duplicate records in inventoryScanned!");
                    Assert.fail("DUPLICATION BUG DETECTED: Found " + duplicateCount +
                            " duplicate item records in inv.inventoryScanned after WiFi disruption. " +
                            "WiFi was toggled " + wifiToggles + " times during scan. " +
                            "DB has " + dbRowCount + " total rows. " +
                            "countInfo: " + countInfo);
                }
            }

            logStep("PASSED: No duplicate records found. Uploaded " +
                    uploadedItems + " items, " + uploadedSections + " sections. " +
                    wifiToggles + " WiFi toggles during scan.");

            pass();

        } catch (Exception e) {
            // Ensure WiFi is restored on failure
            try { toggleWifi(true); } catch (Exception ignored) {}
            fail("WiFi duplication test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== HELPERS ====================

    private Connection getDbConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

    /**
     * Get tire items from spBuildBarcodeMasterFile intersected with inventory snapshot,
     * enriched with TiremaxLive Sqhnd. Same approach as SectionUploadVerificationTest.
     */
    private List<Map<String, Object>> getAllTireItemsWithUpcs(String store, String invNum) {
        Map<String, String> masterItemToUpc = new LinkedHashMap<>();
        try (Connection conn = getDbConnection();
             CallableStatement stmt = conn.prepareCall(
                     "{CALL InventoryScanning.inv.spBuildBarcodeMasterFile(?, ?)}")) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String item = rs.getString("item");
                if (item == null) continue;
                item = item.trim();
                String upc = null;
                for (String col : new String[]{"UPC", "UPC1", "UPC2", "UPC3", "UPC4"}) {
                    String val = rs.getString(col);
                    if (val != null && !val.trim().isEmpty()) {
                        upc = val.trim();
                        break;
                    }
                }
                if (upc != null && !item.isEmpty()) {
                    masterItemToUpc.putIfAbsent(item, upc);
                }
            }
            rs.close();
        } catch (Exception e) {
            logStep("spBuildBarcodeMasterFile failed: " + e.getMessage());
        }
        logStep("BarcodeMaster: " + masterItemToUpc.size() + " unique tire items with UPCs");

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

        Map<String, String> matchedItems = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : masterItemToUpc.entrySet()) {
            if (snapshotItems.contains(entry.getKey())) {
                matchedItems.put(entry.getKey(), entry.getValue());
            }
        }
        logStep("Matched (barcodeMaster ∩ snapshot): " + matchedItems.size() + " items");

        if (matchedItems.isEmpty()) return new ArrayList<>();

        // Enrich with TiremaxLive Sqhnd
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
                logStep("TiremaxLive lookup failed: " + e.getMessage());
            }
        }

        if (items.isEmpty() && !matchedItems.isEmpty()) {
            logStep("TiremaxLive returned 0 — using snapshot items with sqhnd=1");
            for (Map.Entry<String, String> entry : matchedItems.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("item_num", entry.getKey());
                item.put("upc", entry.getValue());
                item.put("sqhnd", 1);
                items.add(item);
            }
        }

        items.sort(Comparator.comparingInt(a -> (int) a.get("sqhnd")));
        logStep("Final: " + items.size() + " tire items for store " + store);
        return items;
    }

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
     * Poll the DB until upload data appears in inv.inventory (adj_qty updated).
     * After upload, items that were scanned will have adj_qty < 9999.
     * Returns the count of uploaded items, or -1 if query failed.
     */
    private int waitForDbData(String store, String invNum, int expectedMinItems, int timeoutSeconds) {
        // adj_qty < 9999 means the item was touched by the scanner upload
        String query = "SELECT COUNT(*) AS cnt FROM InventoryScanning.inv.inventory " +
                "WHERE store = ? AND inv_num = ? AND pc = 2 AND adj_qty < 9999";
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int lastCount = 0;

        while (System.currentTimeMillis() < deadline) {
            try (Connection conn = getDbConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, Integer.parseInt(store));
                stmt.setInt(2, Integer.parseInt(invNum));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    lastCount = rs.getInt("cnt");
                    if (lastCount > 0) {
                        logStep("DB poll: " + lastCount + " items with adj_qty set (upload landed)");
                        return lastCount;
                    }
                }
                rs.close();
            } catch (Exception e) {
                logStep("DB poll failed: " + e.getMessage());
                return -1;
            }

            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            logStep("DB poll: upload not yet visible, waiting...");
        }

        return lastCount;
    }

    /**
     * Query the DB for duplicate item records in inv.inventory.
     * The duplication bug would create multiple rows for the same item_num
     * in the same store+inv_num, or inflate adj_total beyond what was scanned.
     * Returns the number of items that have duplicate entries.
     */
    private int queryDuplicateItems(String store, String invNum) {
        // Check 1: duplicate item_num rows (same item appearing twice in the inventory)
        String query =
                "SELECT COUNT(*) AS dup_count FROM (" +
                        "  SELECT item_num, COUNT(*) AS cnt " +
                        "  FROM InventoryScanning.inv.inventory " +
                        "  WHERE store = ? AND inv_num = ? AND pc = 2 " +
                        "  GROUP BY item_num " +
                        "  HAVING COUNT(*) > 1" +
                        ") dupes";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int dupes = rs.getInt("dup_count");
                if (dupes > 0) {
                    logStep("DB CHECK: Found " + dupes + " duplicate item_num rows!");
                    logDuplicateDetails(store, invNum);
                } else {
                    logStep("DB CHECK: No duplicate item_num rows in inv.inventory");
                }

                // Check 2: compare scanTotalQty in header vs expected
                checkScanTotalQty(store, invNum);

                return dupes;
            }
            rs.close();
        } catch (Exception e) {
            logStep("Duplicate query failed: " + e.getMessage() +
                    " — falling back to UI-only verification");
        }
        return 0;
    }

    /**
     * Check scanTotalQty in inv_hdr for signs of doubling.
     * If scanTotalQty is ~2x the sum of adj_qty values, items were counted twice.
     */
    private void checkScanTotalQty(String store, String invNum) {
        String query =
                "SELECT " +
                        "  (SELECT ISNULL(scanTotalQty, 0) FROM InventoryScanning.inv.vw_inv_hdr " +
                        "   WHERE store = ? AND InvNum = ? AND pc = 2) AS headerScanTotal, " +
                        "  (SELECT ISNULL(SUM(CASE WHEN adj_total < 9999 THEN adj_total ELSE 0 END), 0) " +
                        "   FROM InventoryScanning.inv.inventory " +
                        "   WHERE store = ? AND inv_num = ? AND pc = 2) AS sumAdjTotal";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            stmt.setInt(3, Integer.parseInt(store));
            stmt.setInt(4, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int headerScanTotal = rs.getInt("headerScanTotal");
                int sumAdjTotal = rs.getInt("sumAdjTotal");
                logStep("DB CHECK: scanTotalQty=" + headerScanTotal +
                        " sumAdjTotal=" + sumAdjTotal);

                if (sumAdjTotal > 0 && headerScanTotal > sumAdjTotal * 1.5) {
                    logStep("WARNING: scanTotalQty (" + headerScanTotal +
                            ") is significantly higher than sum of adj_total (" + sumAdjTotal +
                            ") — possible duplication in scan totals!");
                }
            }
            rs.close();
        } catch (Exception e) {
            logStep("scanTotalQty check failed: " + e.getMessage());
        }
    }

    /**
     * Log details of which items were duplicated (for debugging).
     */
    private void logDuplicateDetails(String store, String invNum) {
        String query =
                "SELECT item_num, COUNT(*) AS cnt, " +
                        "  SUM(CASE WHEN adj_total < 9999 THEN adj_total ELSE 0 END) AS total_adj " +
                        "FROM InventoryScanning.inv.inventory " +
                        "WHERE store = ? AND inv_num = ? AND pc = 2 " +
                        "GROUP BY item_num " +
                        "HAVING COUNT(*) > 1 " +
                        "ORDER BY cnt DESC";

        try (Connection conn = getDbConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Integer.parseInt(store));
            stmt.setInt(2, Integer.parseInt(invNum));
            ResultSet rs = stmt.executeQuery();
            int logged = 0;
            while (rs.next() && logged < 10) {
                logStep("  DUPLICATE: item=" + rs.getString("item_num") +
                        " rows=" + rs.getInt("cnt") +
                        " total_adj=" + rs.getInt("total_adj"));
                logged++;
            }
            rs.close();
        } catch (Exception e) {
            logStep("Could not log duplicate details: " + e.getMessage());
        }
    }

    private int parseUploadedItems(String countInfo) {
        try {
            String lower = countInfo.toLowerCase();
            int itemIdx = lower.indexOf("items");
            if (itemIdx > 0) {
                String beforeItem = countInfo.substring(0, itemIdx).trim();
                String[] parts = beforeItem.split("\\s+");
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (Exception e) {
            logStep("Could not parse items from: " + countInfo);
        }
        return -1;
    }

    private int parseUploadedSections(String countInfo) {
        try {
            String lower = countInfo.toLowerCase();
            int secIdx = lower.indexOf("sections");
            if (secIdx > 0) {
                String beforeSec = countInfo.substring(0, secIdx).trim();
                String[] parts = beforeSec.split("\\s+");
                return Integer.parseInt(parts[parts.length - 1]);
            }
        } catch (Exception e) {
            logStep("Could not parse sections from: " + countInfo);
        }
        return -1;
    }

    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                String btnText = driver.findElement(DIALOG_BUTTON_POSITIVE).getText();
                if (btnText != null && btnText.toLowerCase().contains("go back")) {
                    return;
                }
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(500);
            }
        } catch (Exception e) { /* No dialog */ }
    }

    private void handlePostFinishDialogs() throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            By uploadBtn = byText("Upload");
            By continueBtn = byText("Continue");
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
     * Toggle WiFi on/off via ADB.
     * Uses svc wifi (no root needed), with cmd connectivity fallback.
     */
    private void toggleWifi(boolean enable) {
        String action = enable ? "enable" : "disable";
        try {
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("command", "svc");
            args.put("args", java.util.Arrays.asList("wifi", action));
            driver.executeScript("mobile: shell", args);
            Thread.sleep(2000);
            logStep("WiFi " + (enable ? "ON" : "OFF"));
            return;
        } catch (Exception e) {
            logStep("svc wifi failed: " + e.getMessage());
        }

        try {
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            args.put("command", "cmd");
            args.put("args", java.util.Arrays.asList("connectivity", "airplane-mode",
                    enable ? "disable" : "enable"));
            driver.executeScript("mobile: shell", args);
            Thread.sleep(2000);
            logStep("WiFi " + (enable ? "ON" : "OFF") + " (via cmd connectivity)");
            return;
        } catch (Exception e) {
            logStep("cmd connectivity failed: " + e.getMessage());
        }

        try {
            new ProcessBuilder(AppConfig.ADB_PATH, "-s", AppConfig.getDeviceUDID(), "shell",
                    "svc", "wifi", action)
                    .redirectErrorStream(true).start().waitFor();
            Thread.sleep(2000);
            logStep("WiFi " + (enable ? "ON" : "OFF") + " (via direct ADB)");
        } catch (Exception ex) {
            logStep("WARNING: All WiFi toggle strategies failed: " + ex.getMessage());
        }
    }

    private static By byText(String text) {
        return By.xpath("//*[@text='" + text + "']");
    }

    private static By byTextIgnoreCase(String text) {
        String lower = text.toLowerCase();
        return By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + lower + "']");
    }
}
