package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.AddItemDialog;
import com.mavis.scanner.pages.dialogs.BoxedOilDialog;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import com.mavis.scanner.pages.dialogs.MultiItemDialog;
import com.mavis.scanner.utils.DatabaseHelper;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.sql.*;
import java.util.*;

/**
 * End-to-End inventory flow test.
 *
 * Full lifecycle: Launch -> Login -> Scan Section -> Add Items -> Close Section -> Finish
 *
 * Items are added via the "Add Item" dialog using real item numbers from the tires_upc file.
 * The manual count at section close reflects the actual number of items added.
 */
public class InventoryEndToEndTest extends BaseTest {

    // AlertDialog button locators (Android standard)
    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    // Dynamically resolved inventory (store, invCode, PCs) from vw_inv_hdr / spBuildInventory
    private ScheduledInventory scheduledInventory;

    @Test(priority = 1, description = "E2E: Complete parts inventory flow across all scheduled PCs")
    public void testCompletePartsInventoryFlow() {
        scheduledInventory = InventorySetupHelper.resolveInventory();
        activeInvNum=scheduledInventory.invNum;
        activeInvCode=scheduledInventory.invCode;
        setup("E2E - Complete Parts Inventory Flow");

        try {
            // ===== STEP 0: Resolve store + invCode from database =====
            String store = scheduledInventory.store;
            String invCode = scheduledInventory.invCode;
            logStep("Step 0: Resolved inventory: " + scheduledInventory);
            InventorySetupHelper.reportInventoryStatusDistribution();

            // ===== STEP 1: Launch and navigate to Login =====
            StartHomePage startHome = new StartHomePage(driver, wait);
            Assert.assertTrue(startHome.isDisplayed(), "App should launch to StartHome");
            logStep("Step 1: App launched, StartHome displayed");

            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(AppConfig.SHORT_WAIT);
            Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");
            logStep("Step 1: Navigated to Login screen");

            // ===== STEP 2: Login with dynamically resolved store + invCode =====
            loginPage.login(store, AppConfig.TEST_EMPLOYEE, invCode);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            logStep("Step 2: Logged in with Store=" + store + " InvCode=" + invCode +
                    " PCs=" + scheduledInventory.scheduledPCs);

            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);

            // Expect to land on PartsCategoryPage for parts inventory
            PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
            Assert.assertTrue(partsCategory.isDisplayed(),
                    "Should land on Parts Category screen. Activity: " + driver.currentActivity());
            logStep("Step 2: Landed on Parts Category screen");

            // Run the full parts flow
            runPartsScanFlow(partsCategory, dwHelper);

            pass();

        } catch (Exception e) {
            fail("E2E parts inventory flow failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 0, description = "E2E: Complete tire inventory flow from login to confirmation")
    public void testCompleteTireInventoryFlow() {
        // Resolve inventory BEFORE driver init — spBuildInventory can take minutes
        // and the Appium session would time out (newCommandTimeout=300s) waiting.
        scheduledInventory = InventorySetupHelper.resolveInventory();
        activeInvNum = scheduledInventory.invNum;
        activeInvCode = scheduledInventory.invCode;
        logStep("Step 0: Resolved inventory: " + scheduledInventory);

        setup("E2E - Complete Tire Inventory Flow");

        try {
            DataWedgeHelper dwHelper;

            String store = scheduledInventory.store;
            String invCode = scheduledInventory.invCode;

            // ===== STEP 1: Launch and navigate to Login =====
            StartHomePage startHome = new StartHomePage(driver, wait);
            Assert.assertTrue(startHome.isDisplayed(), "App should launch to StartHome");
            logStep("Step 1: App launched, StartHome displayed");

            LoginPage loginPage = startHome.tapStartInventory();
            Thread.sleep(AppConfig.SHORT_WAIT);
            Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");
            logStep("Step 1: Navigated to Login screen");

            // ===== STEP 2: Login with dynamically resolved store + invCode =====
            loginPage.login(store, AppConfig.TEST_EMPLOYEE, invCode);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            logStep("Step 2: Logged in with Store=" + store + " InvCode=" + invCode +
                    " Employee=" + AppConfig.TEST_EMPLOYEE);

            dwHelper = new DataWedgeHelper(driver);

            // Determine which screen we landed on
            MainScanPage mainScan = new MainScanPage(driver, wait);
            PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);

            if (mainScan.isDisplayed()) {
                logStep("Step 2: Landed on Tire Scanning screen");
                runTireScanFlow(mainScan, dwHelper);
            } else if (partsCategory.isDisplayed()) {
                logStep("Step 2: Landed on Parts Category screen");
                runPartsScanFlow(partsCategory, dwHelper);
            } else {
                String activity = driver.currentActivity();
                logStep("Current activity: " + activity);
                fail("Did not land on expected screen after login. Activity: " + activity);
            }

            pass();

        } catch (Exception e) {
            fail("E2E tire inventory flow failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    private void runTireScanFlow(MainScanPage mainScan, DataWedgeHelper dwHelper) throws InterruptedException {
        String store = scheduledInventory.store;
        String invCode = scheduledInventory.invCode;
        String invNum = scheduledInventory.invNum;

        // ===== STEP 3: Load tire items from spBuildBarcodeMasterFile ∩ inventory snapshot =====
        List<Map<String, Object>> allItems = getAllTireItemsWithUpcs(store, invNum);
        Assert.assertFalse(allItems.isEmpty(),
                "No tire items found in inventory snapshot + barcodeMaster for store " + store);
        logStep("Step 3: " + allItems.size() + " tire items available");

        // Get section barcodes — only sections with pc=2 (tires)
        List<String> sections = queryStoreSections(store, 2);
        if (sections.isEmpty()) {
            sections = new ArrayList<>(Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
            logStep("Step 3: No pc=2 sections from DB, using fallback: " + sections);
        } else {
            logStep("Step 3: Found " + sections.size() + " tire sections (pc=2)");
        }

        // Use first 5 items for scanning, next 5 for manual add
        List<Map<String, Object>> scanItems = allItems.subList(0, Math.min(5, allItems.size()));
        List<Map<String, Object>> addItems = allItems.size() > 5
                ? allItems.subList(5, Math.min(10, allItems.size()))
                : new ArrayList<>();

        // ===== STEP 4: Scan section barcode =====
        String sectionBarcode = sections.get(0);
        dwHelper.scanSectionBarcode(sectionBarcode);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissAnyDialog();

        String sectionOutput = mainScan.getSectionOutput();
        logStep("Step 4: Scanned section " + sectionBarcode + ", output: " + sectionOutput);

        // ===== STEP 5: Scan 5 item UPC barcodes via DataWedge =====
        int scannedCount = 0;
        for (int i = 0; i < scanItems.size(); i++) {
            Map<String, Object> item = scanItems.get(i);
            String upc = (String) item.get("upc");
            try {
                logStep("Step 5: Scanning UPC [" + (i + 1) + "/" + scanItems.size() + "]: " +
                        item.get("item_num") + " UPC=" + upc);
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                handlePostScanDialog();
                scannedCount++;
            } catch (Exception e) {
                logStep("Step 5: Scan failed for UPC " + upc + ": " + e.getMessage());
            }
        }
        logStep("Step 5: Scanned " + scannedCount + " UPC barcodes");

        // ===== STEP 6: Add 5 items via Add Item dialog =====
        int totalItemsAdded = 0;
        for (int i = 0; i < addItems.size(); i++) {
            Map<String, Object> item = addItems.get(i);
            String itemNumber = (String) item.get("item_num");

            logStep("Step 6: Adding item [" + (i + 1) + "/" + addItems.size() + "]: " + itemNumber + " qty 1");
            try {
                boolean added = addItemViaDialog(mainScan, itemNumber, "1");
                if (added) {
                    totalItemsAdded++;
                    logStep("Step 6: Item added. Items in list: " + mainScan.getItemCount());
                } else {
                    logStep("Step 6: Item " + itemNumber + " was not added");
                }
                Thread.sleep(AppConfig.SHORT_WAIT);
            } catch (Exception e) {
                logStep("Step 6: Failed to add item " + itemNumber + ": " + e.getMessage());
            }
        }

        int itemCount = mainScan.getItemCount();
        logStep("Step 6: Total items in section: " + itemCount +
                " (scanned " + scannedCount + " + added " + totalItemsAdded + ")");

        // ===== STEP 7: Close the section =====
        String manualCount = String.valueOf(itemCount > 0 ? itemCount : (scannedCount + totalItemsAdded));
        if (manualCount.equals("0")) manualCount = "1";

        logStep("Step 7: Closing section (manual count: " + manualCount + ")...");
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
            countDialog.closeWithCount(manualCount);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Step 7: Section closed with manual count " + manualCount);
        }

        logStep("Step 7: Done — " + scannedCount + " scanned + " + totalItemsAdded + " added in section " + sectionBarcode);

        // ===== STEP 5: Finish inventory =====
        logStep("Step 5: Finishing inventory...");
        scrollToBottom();
        mainScan.tapFinish();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // Close ALL remaining/missed sections with 0, then confirm finish
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

        // Handle post-finish dialogs (packing list, upload confirmation)
        for (int attempt = 0; attempt < 10; attempt++) {
            By continueBtn = byText("Continue");
            By uploadBtn = byText("Upload");
            By acceptBtn = byText("Accept");
            By skipBtn = byText("Skip");

            if (WaitHelper.isElementPresent(driver, uploadBtn)) {
                logStep("Step 5: Tapping Upload");
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
            logStep("Step 5: Final confirmation: " + countInfo);
            confirmPage.tapLogout();
            logStep("Step 5: Logged out");
        } else {
            logStep("Step 5: Upload may still be processing. Activity: " + driver.currentActivity());
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            dismissAnyDialog();
            if (confirmPage.isDisplayed()) {
                confirmPage.tapLogout();
                logStep("Step 5: Logged out after extended wait");
            }
        }
    }

    /**
     * Add an item via the Add Item dialog.
     * Flow: Tap Add Item -> "Lookup by Item Number" -> enter item number + qty -> Submit
     *
     * @return true if the item was successfully submitted
     */
    private boolean addItemViaDialog(MainScanPage mainScan, String itemNumber, String qty) throws InterruptedException {
        mainScan.tapAddItem();
        Thread.sleep(AppConfig.SHORT_WAIT);

        // Step 1: Handle chooser dialog
        By lookupByItemBtn = byText("Lookup by Item Number");

        if (WaitHelper.isElementPresent(driver, lookupByItemBtn)) {
            driver.findElement(lookupByItemBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        } else {
            logStep("  No chooser dialog appeared");
            return false;
        }

        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // Step 2: Wait for item entry dialog
        By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
        By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");

        boolean found = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (WaitHelper.isElementPresent(driver, addItemField)) {
                found = true;
                break;
            }
            Thread.sleep(1000);
        }

        if (!found) {
            logStep("  Item entry dialog did not appear");
            dismissAnyDialog();
            return false;
        }

        // Step 3: Enter item number and quantity
        WaitHelper.waitAndType(wait, addItemField, itemNumber);
        WaitHelper.waitAndType(wait, addItemQty, qty);

        // Step 4: Tap Submit
        By submitBtn = byText("Submit");
        if (WaitHelper.isElementPresent(driver, submitBtn)) {
            driver.findElement(submitBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
        }

        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        return true;
    }

    /**
     * Full parts inventory E2E flow.
     *
     * Flow per PC: PartsCategoryPage -> select PC -> MainActivityParts -> scan section ->
     *   scan items -> close section -> finish PC -> handle missed items -> back to PartsCategoryPage
     * After all PCs: Finish -> upload -> FinalConfirmActivity -> logout
     */
    private void runPartsScanFlow(PartsCategoryPage partsCategory, DataWedgeHelper dwHelper) throws InterruptedException {
        DatabaseHelper dbHelper = new DatabaseHelper(driver);

        // ===== STEP 3: Enumerate scheduled PCs =====
        int categoryCount = partsCategory.getVisibleCategoryCount();
        logStep("Step 3: " + categoryCount + " product categories scheduled");

        String[] categoryLabels = new String[categoryCount + 1];
        for (int i = 1; i <= categoryCount; i++) {
            categoryLabels[i] = partsCategory.getCategoryLabel(i);
            logStep("  PC slot " + i + ": " + categoryLabels[i]);
        }

        Assert.assertTrue(categoryCount > 0, "At least one PC should be scheduled");

        // Load all available sections once and track which ones have been used
        List<String> allSections = dbHelper.getSectionBarcodes();
        if (allSections.isEmpty()) {
            allSections = new java.util.ArrayList<>(java.util.Arrays.asList(AppConfig.FALLBACK_SECTION_BARCODES));
        }
        logStep("Step 3: " + allSections.size() + " section barcodes available");
        java.util.Set<String> usedSections = new java.util.HashSet<>();

        // ===== STEP 4-N: Process each PC =====
        for (int pcSlot = 1; pcSlot <= categoryCount; pcSlot++) {
            String pcLabel = categoryLabels[pcSlot];

            // Skip already completed PCs
            if (partsCategory.isCategoryCompleted(pcSlot)) {
                logStep("PC " + pcSlot + " (" + pcLabel + "): Already completed, skipping");
                continue;
            }

            logStep("===== Processing PC " + pcSlot + "/" + categoryCount + ": " + pcLabel + " =====");

            // Resolve the numeric PC code from the label (e.g., "Oil Filters" -> "86")
            String pcCode = AppConfig.getPcCodeFromLabel(pcLabel);
            logStep("PC " + pcSlot + ": Resolved PC code: " + pcCode);

            // Tap Start/Add to enter MainActivityParts
            partsCategory.tapStart(pcSlot);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            PartsMainPage partsMain = new PartsMainPage(driver, wait);
            if (!partsMain.isDisplayed()) {
                logStep("PC " + pcSlot + ": MainActivityParts did not appear, skipping");
                continue;
            }
            logStep("PC " + pcSlot + ": Entered parts scanning screen");

            // --- Scan a section barcode (use one not yet used by a previous PC) ---
            boolean sectionOpened = false;
            String openedSection = null;
            for (String sectionBarcode : allSections) {
                if (usedSections.contains(sectionBarcode)) {
                    logStep("PC " + pcSlot + ": Skipping section " + sectionBarcode + " (already used by previous PC)");
                    continue;
                }
                logStep("PC " + pcSlot + ": Scanning section " + sectionBarcode);
                dwHelper.simulateScan(sectionBarcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();

                String sectionOutput = partsMain.getSectionOutput();
                logStep("PC " + pcSlot + ": Section output: " + sectionOutput);

                // Verify the section actually opened (output should no longer say "Scan Section")
                if (sectionOutput != null && !sectionOutput.toLowerCase().contains("scan section")) {
                    sectionOpened = true;
                    openedSection = sectionBarcode;
                    usedSections.add(sectionBarcode);
                    logStep("PC " + pcSlot + ": Section " + sectionBarcode + " opened successfully");
                    break;
                }
                logStep("PC " + pcSlot + ": Section " + sectionBarcode + " did not open, trying next...");
            }

            if (!sectionOpened) {
                logStep("PC " + pcSlot + ": No section could be opened, skipping to Finish PC");
                scrollToBottom();
                partsMain.tapFinishCategory();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
                handleMissedItems(pcSlot, pcLabel);
                partsCategory = new PartsCategoryPage(driver, wait);
                continue;
            }

            // --- Fetch UPCs for this specific PC ---
            // For brakes/rotors (PC 66), read from parts_upc file; otherwise use barcodeMasterMultiUOM
            List<String> upcCodes;
            if ("66".equals(pcCode)) {
                upcCodes = dbHelper.getUpcsFromPartsFile("66", 5);
                logStep("PC " + pcSlot + ": Loaded " + upcCodes.size() + " UPCs for PC 66 (brakes/rotors) from parts_upc file");
                if (upcCodes.isEmpty()) {
                    upcCodes = dbHelper.getTestUpcsByPc(pcCode, 5);
                    logStep("PC " + pcSlot + ": Fallback - loaded " + upcCodes.size() + " UPCs from barcodeMasterMultiUOM");
                }
            } else if (pcCode != null) {
                upcCodes = dbHelper.getTestUpcsByPc(pcCode, 5);
                logStep("PC " + pcSlot + ": Loaded " + upcCodes.size() + " UPCs for PC " + pcCode + " from barcodeMasterMultiUOM");
            } else {
                upcCodes = dbHelper.getTestUpcs(scheduledInventory.store, scheduledInventory.invCode, 5);
                logStep("PC " + pcSlot + ": Could not resolve PC code, using general UPCs");
            }

            // --- Scan items via DataWedge ---
            int scannedCount = 0;

            for (int i = 0; i < Math.min(3, upcCodes.size()); i++) {
                String upc = upcCodes.get(i);
                try {
                    logStep("PC " + pcSlot + ": Scanning UPC [" + (i + 1) + "]: " + upc);
                    dwHelper.simulateScan(upc, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                    // Handle special dialogs that may appear after scanning
                    handlePostScanDialog();

                    scannedCount++;
                    Thread.sleep(AppConfig.SHORT_WAIT);
                } catch (Exception e) {
                    logStep("PC " + pcSlot + ": Scan failed for UPC " + upc + ": " + e.getMessage());
                }
            }
            logStep("PC " + pcSlot + ": Scanned " + scannedCount + " items via DataWedge");

            // --- Add extra items for boxed PCs (Oil/AC) via additional scans ---
            boolean isBoxedPc = "188".equals(pcCode) || "62".equals(pcCode);
            int manualAdded = 0;
            if (isBoxedPc) {
                // Oil/AC: Scan additional UPCs which trigger BoxedOilDialog (Full/3-4/Half/1-4)
                List<String> boxedUpcs = dbHelper.getTestUpcsByPc(pcCode, 5);
                for (int i = scannedCount; i < Math.min(scannedCount + 2, boxedUpcs.size()); i++) {
                    try {
                        logStep("PC " + pcSlot + ": Adding boxed item [" + (i + 1) + "] via scan");
                        dwHelper.simulateScan(boxedUpcs.get(i), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
                        if (oilDialog.isDisplayed()) {
                            oilDialog.selectFull();
                            manualAdded++;
                            logStep("PC " + pcSlot + ": Boxed item added (Full)");
                        } else {
                            dismissAnyDialog();
                        }
                        Thread.sleep(AppConfig.SHORT_WAIT);
                    } catch (Exception e) {
                        logStep("PC " + pcSlot + ": Boxed add failed: " + e.getMessage());
                    }
                }
            }

            int totalItems = partsMain.getItemCount();
            logStep("PC " + pcSlot + ": Total items: " + totalItems +
                    " (scanned " + scannedCount + " + manual " + manualAdded + ")");

            // --- Close the current section ---
            closePartsSection(partsMain, pcSlot, scannedCount + manualAdded);

            // --- Finish this PC ---
            // tapFinishCategory may prompt about remaining unclosed sections.
            // Keep closing with 0 and retrying until we're back on PartsCategoryPage.
            logStep("PC " + pcSlot + ": Finishing category...");
            boolean backOnCategoryPage = false;
            java.util.Set<String> closedSectionOutputs = new java.util.HashSet<>();

            for (int finishAttempt = 0; finishAttempt < 8; finishAttempt++) {

                // Try tapping Finish PC
                try {
                    scrollToBottom();
                    partsMain = new PartsMainPage(driver, wait);
                    if (partsMain.isDisplayed()) {
                        partsMain.tapFinishCategory();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    }
                } catch (Exception e) {
                    logStep("PC " + pcSlot + ": tapFinishCategory failed: " + e.getMessage());
                }

                // Handle any confirmation dialog after Finish PC
                // Check for various button texts: Yes, Continue, OK, Close with 0
                for (int dlgAttempt = 0; dlgAttempt < 3; dlgAttempt++) {
                    By yesBtn = byTextIgnoreCase("YES");
                    By continueBtn = byTextIgnoreCase("CONTINUE");
                    By okBtn = byTextIgnoreCase("OK");
                    By closeWith0 = byTextIgnoreCase("CLOSE WITH 0");

                    if (WaitHelper.isElementPresent(driver, closeWith0)) {
                        logStep("PC " + pcSlot + ": Closing remaining section with 0");
                        driver.findElement(closeWith0).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, yesBtn)) {
                        logStep("PC " + pcSlot + ": Confirming finish (Yes)");
                        driver.findElement(yesBtn).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, continueBtn)) {
                        logStep("PC " + pcSlot + ": Confirming finish (Continue)");
                        driver.findElement(continueBtn).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, okBtn)) {
                        logStep("PC " + pcSlot + ": Confirming finish (OK)");
                        driver.findElement(okBtn).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                        logStep("PC " + pcSlot + ": Confirming finish (neutral button)");
                        driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                        logStep("PC " + pcSlot + ": Confirming finish (positive button)");
                        driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else {
                        break; // No more dialogs
                    }
                }

                // Handle missed items
                handleMissedItems(pcSlot, pcLabel);

                // Check if we landed on PartsCategoryPage
                partsCategory = new PartsCategoryPage(driver, wait);
                if (partsCategory.isDisplayed()) {
                    backOnCategoryPage = true;
                    break;
                }

                // Still on MainActivityParts — check for remaining sections
                partsMain = new PartsMainPage(driver, wait);
                if (!partsMain.isDisplayed()) {
                    // Check if app crashed (landed on home screen / launcher)
                    try {
                        String activity = driver.currentActivity();
                        if (activity != null && activity.toLowerCase().contains("launcher")) {
                            logStep("PC " + pcSlot + ": App appears to have crashed (activity: " + activity + "). Breaking out of finish loop.");
                            break;
                        }
                    } catch (Exception actEx) {
                        logStep("PC " + pcSlot + ": Cannot determine current activity, instrumentation may have crashed. Breaking out of finish loop.");
                        break;
                    }
                    dismissAnyDialog();
                    Thread.sleep(AppConfig.SHORT_WAIT);
                    partsCategory = new PartsCategoryPage(driver, wait);
                    if (partsCategory.isDisplayed()) {
                        backOnCategoryPage = true;
                        break;
                    }
                    continue;
                }

                String secOutput = partsMain.getSectionOutput();
                logStep("PC " + pcSlot + ": Still on parts screen. Section: " + secOutput);

                // If section shows "Scan section barcode to start" — no open section, just retry finish
                if (secOutput.toLowerCase().contains("scan section")) {
                    continue;
                }

                // If we already closed this exact section output, we're stuck — press back to escape
                if (closedSectionOutputs.contains(secOutput)) {
                    logStep("PC " + pcSlot + ": Already closed this section, pressing back to escape");
                    driver.navigate().back();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                    // Dismiss any "are you sure" dialog
                    dismissAnyDialog();
                    Thread.sleep(AppConfig.SHORT_WAIT);
                    partsCategory = new PartsCategoryPage(driver, wait);
                    if (partsCategory.isDisplayed()) {
                        backOnCategoryPage = true;
                    }
                    break;
                }

                // Close the remaining section with 0 and track it
                closedSectionOutputs.add(secOutput);
                closePartsSection(partsMain, pcSlot, 0);
            }

            if (backOnCategoryPage) {
                boolean completed = partsCategory.isCategoryCompleted(pcSlot);
                logStep("PC " + pcSlot + ": Returned to category screen. Completed: " + completed);
            } else {
                String currentActivity = "";
                try {
                    currentActivity = driver.currentActivity();
                } catch (Exception e) {
                    currentActivity = "(unable to get activity: " + e.getMessage() + ")";
                }
                logStep("PC " + pcSlot + ": Could not return to PartsCategoryPage. Activity: " + currentActivity);

                // Try to navigate back, but handle UiAutomator2 crash gracefully
                try {
                    driver.navigate().back();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                    dismissAnyDialog();
                    Thread.sleep(AppConfig.SHORT_WAIT);
                } catch (Exception backEx) {
                    logStep("PC " + pcSlot + ": navigate().back() failed (instrumentation may have crashed): " + backEx.getMessage());
                    // Try to recover by relaunching the app
                    try {
                        logStep("PC " + pcSlot + ": Attempting app relaunch to recover...");
                        driver.activateApp(AppConfig.APP_PACKAGE);
                        Thread.sleep(AppConfig.LONG_WAIT);
                    } catch (Exception relaunchEx) {
                        logStep("PC " + pcSlot + ": App relaunch also failed: " + relaunchEx.getMessage());
                    }
                }
                partsCategory = new PartsCategoryPage(driver, wait);
            }
        }

        // ===== FINAL: Finish all parts and upload =====
        logStep("===== All PCs processed, finishing parts inventory =====");
        partsCategory = new PartsCategoryPage(driver, wait);

        if (!partsCategory.isDisplayed()) {
            logStep("Not on PartsCategoryPage, cannot finish. Activity: " + driver.currentActivity());
            return;
        }

        // Log completion status for each PC
        for (int i = 1; i <= categoryCount; i++) {
            logStep("  PC " + i + " (" + categoryLabels[i] + "): completed=" + partsCategory.isCategoryCompleted(i));
        }

        // Tap Finish to initiate upload
        try {
            if (partsCategory.isFinishButtonVisible()) {
                partsCategory.tapFinish();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
                logStep("Tapped Finish Parts");
            } else {
                logStep("Finish button not visible yet - some PCs may be incomplete");
                // Try tapping logout instead
                partsCategory.tapLogout();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
                return;
            }

            // Handle "Missing PCs" dialog if not all PCs were completed
            // Options: "I don't have any" / "Go to scan [Category]" / "Exit"
            By dontHaveBtn = byTextIgnoreCase("I DON'T HAVE ANY");
            if (WaitHelper.isElementPresent(driver, dontHaveBtn)) {
                logStep("Missing PCs dialog appeared - selecting 'I don't have any'");
                driver.findElement(dontHaveBtn).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
            }

            // Handle upload confirmation
            By yesBtn = byTextIgnoreCase("YES");
            if (WaitHelper.isElementPresent(driver, yesBtn)) {
                logStep("Confirming upload");
                driver.findElement(yesBtn).click();
                Thread.sleep(AppConfig.LONG_WAIT);
            }

            // Wait for upload processing
            logStep("Waiting for upload to complete...");
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            dismissAnyDialog();

            // Check for FinalConfirmActivity
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                String invInfo = confirmPage.getInventoryInfo();
                logStep("Final confirmation: " + countInfo);
                logStep("Inventory info: " + invInfo);
                confirmPage.tapLogout();
                logStep("Logged out successfully");
            } else {
                logStep("Upload may still be processing. Activity: " + driver.currentActivity());
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                dismissAnyDialog();
                confirmPage = new FinalConfirmPage(driver, wait);
                if (confirmPage.isDisplayed()) {
                    logStep("Final confirmation: " + confirmPage.getCountInfo());
                    confirmPage.tapLogout();
                    logStep("Logged out after extended wait");
                }
            }
        } catch (Exception e) {
            logStep("Finish/upload failed: " + e.getMessage());
        }
    }

    /**
     * Handle post-scan dialogs that may appear depending on item type:
     * - BoxedOilDialog (PC 188/62): Select fill level (Full/3-4/Half/1-4)
     * - MultiItemDialog (PC 66): Select which item when UPC maps to multiple
     * - Quantity dialog (Wipers/Batteries/TPMS/Filters/Rotor): Enter qty and submit
     * - Standard confirmation dialog: Dismiss
     */
    private void handlePostScanDialog() throws InterruptedException {
        Thread.sleep(AppConfig.SHORT_WAIT);

        // Check for Boxed Oil dialog (PC 188/62)
        BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
        if (oilDialog.isDisplayed()) {
            logStep("  Boxed Oil dialog - selecting Full");
            oilDialog.selectFull();
            Thread.sleep(AppConfig.SHORT_WAIT);
            return;
        }

        // Check for Multi-Item dialog (PC 66 at warehouses)
        MultiItemDialog multiDialog = new MultiItemDialog(driver, wait);
        if (multiDialog.isDisplayed()) {
            logStep("  Multi-Item dialog - selecting Item 1: " + multiDialog.getItem1Text());
            multiDialog.selectItem1();
            Thread.sleep(AppConfig.SHORT_WAIT);
            // After selecting item, a quantity dialog may appear — fall through to qty check
        }

        // Check for quantity dialog (Wipers, Batteries, TPMS, Filters, Rotor/BrakePad)
        // Retry a few times — the dialog/EditText may take a moment to render
        By qtyField = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
        By qtyField2 = By.id("com.mavis.inventory_barcode_scanner:id/editTextQty");
        By anyEditText = By.className("android.widget.EditText");

        By foundQtyField = null;
        for (int qtyAttempt = 0; qtyAttempt < 3; qtyAttempt++) {
            if (WaitHelper.isElementPresent(driver, qtyField)) {
                foundQtyField = qtyField;
                break;
            } else if (WaitHelper.isElementPresent(driver, qtyField2)) {
                foundQtyField = qtyField2;
                break;
            } else if (WaitHelper.isElementPresent(driver, anyEditText)) {
                foundQtyField = anyEditText;
                break;
            }
            // Wait a bit before retrying — dialog may still be rendering
            Thread.sleep(800);
        }

        if (foundQtyField != null) {
            // Read the pre-populated quantity (e.g. TPMS defaults to box qty like 20)
            // Only enter "1" if the field is empty
            WebElement qtyElement = driver.findElement(foundQtyField);
            String existingQty = qtyElement.getText();
            if (existingQty == null || existingQty.trim().isEmpty()) {
                existingQty = qtyElement.getAttribute("text");
            }
            if (existingQty != null && !existingQty.trim().isEmpty() && !existingQty.trim().equals("0")) {
                logStep("  Quantity dialog - using pre-filled qty: " + existingQty.trim());
            } else {
                logStep("  Quantity dialog - field empty, entering qty 1");
                qtyElement.clear();
                qtyElement.sendKeys("1");
            }
            Thread.sleep(500);
            // Tap Submit/OK: try neutral button first (Submit), then positive (OK)
            By submitBtn = byTextIgnoreCase("SUBMIT");
            By okBtn = byTextIgnoreCase("OK");
            if (WaitHelper.isElementPresent(driver, submitBtn)) {
                driver.findElement(submitBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            } else if (WaitHelper.isElementPresent(driver, okBtn)) {
                driver.findElement(okBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.SHORT_WAIT);
            return;
        }

        // No quantity field — this may be a simple confirmation dialog (e.g. Batteries).
        // Try neutral button first (typically "Add"/"Submit"), fallback to positive ("OK").
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            logStep("  Confirmed scan (neutral button)");
            Thread.sleep(500);
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            logStep("  Confirmed scan (positive button)");
            Thread.sleep(500);
        }
    }

    /**
     * Close the current section on PartsMainPage with a manual count.
     */
    private void closePartsSection(PartsMainPage partsMain, int pcSlot, int itemCount) throws InterruptedException {
        String manualCount = String.valueOf(Math.max(itemCount, 0));

        logStep("PC " + pcSlot + ": Closing section (manual count: " + manualCount + ")");
        try {
            scrollToBottom();
            partsMain.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Handle completion confirmation dialog
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
                countDialog.closeWithCount(manualCount);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("PC " + pcSlot + ": Section closed with count " + manualCount);
            }
        } catch (Exception e) {
            logStep("PC " + pcSlot + ": Close section failed: " + e.getMessage());
        }
    }

    /**
     * Handle the missed items dialog loop after completing a PC.
     *
     * The app shows missed items one at a time ("Missing items: 1 of X").
     * Keep hitting Submit/OK on each dialog until the missed item list is exhausted
     * and the dialog disappears.
     */
    private void handleMissedItems(int pcSlot, String pcLabel) throws InterruptedException {
        By submitBtn = byTextIgnoreCase("SUBMIT");
        By okBtn = byTextIgnoreCase("OK");

        int missedCount = 0;
        int maxMissedItems = 200; // safety limit

        for (int i = 0; i < maxMissedItems; i++) {
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Check for Boxed Oil dialog (PC 188/62) — has different buttons
            BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
            if (oilDialog.isDisplayed()) {
                missedCount++;
                logStep("PC " + pcSlot + ": Missed item " + missedCount + " (boxed) - selecting Full");
                oilDialog.selectFull();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            // Try Submit button first
            if (WaitHelper.isElementPresent(driver, submitBtn)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 25 == 0) {
                    logStep("PC " + pcSlot + ": Missed item " + missedCount + " - tapping Submit");
                }
                driver.findElement(submitBtn).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            // Try OK button
            if (WaitHelper.isElementPresent(driver, okBtn)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 25 == 0) {
                    logStep("PC " + pcSlot + ": Missed item " + missedCount + " - tapping OK");
                }
                driver.findElement(okBtn).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            // Try neutral button (button3) as fallback
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 25 == 0) {
                    logStep("PC " + pcSlot + ": Missed item " + missedCount + " - tapping dialog button");
                }
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            // Try positive button (button1) as last resort
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                missedCount++;
                if (missedCount <= 5 || missedCount % 25 == 0) {
                    logStep("PC " + pcSlot + ": Missed item " + missedCount + " - tapping positive button");
                }
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                continue;
            }

            // No dialog found — missed items list is done
            break;
        }

        if (missedCount > 0) {
            logStep("PC " + pcSlot + " (" + pcLabel + "): Submitted through " + missedCount + " missed items");
        } else {
            logStep("PC " + pcSlot + " (" + pcLabel + "): No missed items");
        }
    }

    /**
     * Add an item via the Add Item dialog on PartsMainPage.
     * Flow: Tap Add Item -> "Lookup by Item Number" -> enter item number + qty -> Submit
     */
    private boolean addPartsItemViaDialog(PartsMainPage partsMain, String itemNumber, String qty) throws InterruptedException {
        partsMain.tapAddItem();
        Thread.sleep(AppConfig.SHORT_WAIT);

        // Handle chooser dialog
        By lookupByItemBtn = byText("Lookup by Item Number");
        By enterByItemBtn = byTextIgnoreCase("ENTER ITEM BY ITEM NUMBER");

        if (WaitHelper.isElementPresent(driver, lookupByItemBtn)) {
            driver.findElement(lookupByItemBtn).click();
        } else if (WaitHelper.isElementPresent(driver, enterByItemBtn)) {
            driver.findElement(enterByItemBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        } else {
            logStep("  No chooser dialog appeared");
            return false;
        }

        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // Wait for AddItemDialog
        AddItemDialog addDialog = new AddItemDialog(driver, wait);
        for (int attempt = 0; attempt < 5; attempt++) {
            if (addDialog.isDisplayed()) break;
            Thread.sleep(1000);
        }

        if (!addDialog.isDisplayed()) {
            logStep("  Item entry dialog did not appear");
            dismissAnyDialog();
            return false;
        }

        addDialog.addItem(itemNumber, qty);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

        // Handle post-add dialogs (boxed oil, multi-item, etc.)
        handlePostScanDialog();

        return true;
    }

    /**
     * Dismiss any visible AlertDialog.
     */
    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                logStep("Dismissed dialog (positive button)");
                Thread.sleep(500);
            }
        } catch (Exception e) {
            // No dialog to dismiss
        }
    }

    // ==================== DB HELPERS ====================

    private Connection getDbConnection() throws Exception {
        String url = String.format(
                "jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=true;trustServerCertificate=true;",
                AppConfig.DB_SERVER, AppConfig.DB_PORT, AppConfig.DB_INVENTORY);
        return DriverManager.getConnection(url, AppConfig.DB_USERNAME, AppConfig.DB_PASSWORD);
    }

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
        logStep("BarcodeMaster (spBuildBarcodeMasterFile): " + masterItemToUpc.size() + " unique tire items with UPCs");

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

        Map<String, String> matchedItems = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : masterItemToUpc.entrySet()) {
            if (snapshotItems.contains(entry.getKey())) {
                matchedItems.put(entry.getKey(), entry.getValue());
            }
        }
        logStep("Matched (barcodeMaster ∩ snapshot): " + matchedItems.size() + " items");

        if (matchedItems.isEmpty()) return new ArrayList<>();

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

        items.sort(Comparator.comparingInt(a -> (int) a.get("sqhnd")));

        logStep("Final: " + items.size() + " tire items with UPCs for store " + store);
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
     * Parse the total section count from section output text.
     * Handles formats like "1 / 26", "1/26", "Section: STR-1000 1/26".
     */
    private int parseTotalSections(String sectionText) {
        if (sectionText == null || sectionText.isEmpty()) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")
                .matcher(sectionText);
        if (m.find()) {
            return Integer.parseInt(m.group(2));
        }
        return 0;
    }

    /**
     * Create a text-based locator (XPath).
     */
    private static By byText(String text) {
        return By.xpath("//*[@text='" + text + "']");
    }

    /**
     * Create a case-insensitive text-based locator (XPath).
     */
    private static By byTextIgnoreCase(String text) {
        String lower = text.toLowerCase();
        return By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + lower + "']");
    }

    /**
     * Create a locator matching elements whose text contains the given substring.
     */
    private static By byTextContains(String text) {
        return By.xpath("//*[contains(@text,'" + text + "')]");
    }

    /**
     * Scroll to bottom to make buttons visible.
     */
    private void scrollToBottom() {
        try {
            org.openqa.selenium.Dimension size = driver.manage().window().getSize();
            int startX = size.width / 2;
            int startY = (int) (size.height * 0.8);
            int endY = (int) (size.height * 0.2);

            org.openqa.selenium.interactions.PointerInput finger =
                    new org.openqa.selenium.interactions.PointerInput(
                            org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
            org.openqa.selenium.interactions.Sequence swipe =
                    new org.openqa.selenium.interactions.Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(java.time.Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, startY));
            swipe.addAction(finger.createPointerDown(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(java.time.Duration.ofMillis(500),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, endY));
            swipe.addAction(finger.createPointerUp(org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(java.util.Collections.singletonList(swipe));
            Thread.sleep(500);
        } catch (Exception e) {
            // Scroll failed, buttons may already be visible
        }
    }
}
