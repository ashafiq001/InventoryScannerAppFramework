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

import java.util.List;

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
        setup("E2E - Complete Parts Inventory Flow");

        try {
            // ===== STEP 0: Resolve store + invCode from database =====
            scheduledInventory = InventorySetupHelper.resolveInventory();
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
        setup("E2E - Complete Tire Inventory Flow");

        try {
            DataWedgeHelper dwHelper;

            // ===== STEP 0: Resolve store + invCode from database =====
            scheduledInventory = InventorySetupHelper.resolveInventory();
            String store = scheduledInventory.store;
            String invCode = scheduledInventory.invCode;
            logStep("Step 0: Resolved inventory: " + scheduledInventory);

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
        // ===== STEP 3: Load item data from tires_upc file =====
        DatabaseHelper dbHelper = new DatabaseHelper(driver);
        List<String> upcDetails = new java.util.ArrayList<>();
        List<String> upcCodes = new java.util.ArrayList<>();

        String store = scheduledInventory.store;
        String invCode = scheduledInventory.invCode;

        try {
            upcDetails = dbHelper.getTestUpcDetails(store, invCode, 10);
            upcCodes = dbHelper.getTestUpcs(store, invCode, 5);
            int masterCount = dbHelper.getMasterListCount();
            logStep("Step 3: Loaded " + masterCount + " items from master list");
            logStep("Step 3: Got " + upcCodes.size() + " UPCs for scanning, " + upcDetails.size() + " items for adding");
        } catch (Exception e) {
            logStep("Step 3: Failed to load item data: " + e.getMessage());
        }

        // ===== STEP 4: Scan a section barcode =====
        dwHelper.scanSectionBarcode("STR-1000");
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissAnyDialog();

        String sectionOutput = mainScan.getSectionOutput();
        logStep("Step 4: Scanned section STR-1000, output: " + sectionOutput);

        // ===== STEP 5: Scan 5 item UPC barcodes via DataWedge =====
        int scannedCount = 0;
        for (int i = 0; i < Math.min(5, upcCodes.size()); i++) {
            String upc = upcCodes.get(i);
            try {
                logStep("Step 5: Scanning UPC [" + (i + 1) + "/5]: " + upc);
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();

                int currentCount = mainScan.getItemCount();
                logStep("Step 5: Items after scan: " + currentCount);
                scannedCount++;
                Thread.sleep(AppConfig.SHORT_WAIT);
            } catch (Exception e) {
                logStep("Step 5: Scan failed for UPC " + upc + ": " + e.getMessage());
            }
        }
        logStep("Step 5: Scanned " + scannedCount + " UPC barcodes");

        // ===== STEP 6: Add 5 items via Add Item dialog =====
        // Use items starting from index 5 (different from scanned ones)
        int totalItemsAdded = 0;
        for (int itemIdx = 0; itemIdx < 5; itemIdx++) {
            // Parse item number from detail string (format: UPC|Item|Description|Size)
            String itemNumber;
            int detailIdx = itemIdx + 5; // offset to avoid duplicating scanned items

            if (detailIdx < upcDetails.size()) {
                String[] parts = upcDetails.get(detailIdx).split("\\|");
                itemNumber = (parts.length > 1 && !parts[1].isEmpty()) ? parts[1] : dbHelper.getValidItemNumber();
            } else if (itemIdx < upcDetails.size()) {
                String[] parts = upcDetails.get(itemIdx).split("\\|");
                itemNumber = (parts.length > 1 && !parts[1].isEmpty()) ? parts[1] : dbHelper.getValidItemNumber();
            } else {
                itemNumber = dbHelper.getValidItemNumber();
            }

            logStep("Step 6: Adding item [" + (itemIdx + 1) + "/5]: " + itemNumber + " qty 1");
            try {
                boolean added = addItemViaDialog(mainScan, itemNumber, "1");
                if (added) {
                    totalItemsAdded++;
                    int currentCount = mainScan.getItemCount();
                    logStep("Step 6: Item added. Items in list: " + currentCount);
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
        // Manual count = actual items in the list
        String manualCount = String.valueOf(itemCount > 0 ? itemCount : (scannedCount + totalItemsAdded));
        if (manualCount.equals("0")) manualCount = "1";

        logStep("Step 7: Closing section (manual count: " + manualCount + ")...");
        try {
            scrollToBottom();

            mainScan.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Handle "Completing current section" dialog
            By completeBtn = byText("Complete");
            if (WaitHelper.isElementPresent(driver, completeBtn)) {
                driver.findElement(completeBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.SHORT_WAIT);

            // ManualCountDialog - enter the actual count
            ManualCountDialog countDialog = new ManualCountDialog(driver, wait);
            if (countDialog.isDisplayed()) {
                countDialog.closeWithCount(manualCount);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Step 7: Section closed with manual count " + manualCount);
            } else {
                logStep("Step 7: Manual count dialog did not appear");
            }
        } catch (Exception e) {
            logStep("Step 7: Close section failed: " + e.getMessage());
        }

        // ===== STEP 8: View Summary =====
        try {
            mainScan.tapSummary();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Step 8: Viewed summary");
            dismissAnyDialog();
        } catch (Exception e) {
            logStep("Step 8: Summary failed: " + e.getMessage());
        }

        // ===== STEP 9: Log current state =====
        String finalSectionOutput = mainScan.getSectionOutput();
        logStep("Step 9: Final section output: " + finalSectionOutput);

        // ===== STEP 10: Finish inventory =====
        // Flow: Tap Finish -> Close remaining sections with 0 -> Confirm upload -> Handle packing list -> Final confirm
        try {
            // Parse total section count from the section output (e.g. "1 / 26" or "Section: STR-1000 1/26")
            String sectionText = mainScan.getSectionOutput();
            int totalSections = parseTotalSections(sectionText);
            int closedByUs = 1; // We closed STR-1000 in step 7
            int remainingSections = Math.max(totalSections - closedByUs, 0);
            logStep("Step 10: Total sections: " + totalSections + ", remaining to close: " + remainingSections);

            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Step 10: Tapped Finish");

            // Close each remaining open section with 0
            // Flow: Finish -> "CLOSE WITH 0" -> dialog closes -> Finish again -> repeat until empty
            By closeWith0Btn = byTextIgnoreCase("CLOSE WITH 0");
            By yesFinishBtn = byTextIgnoreCase("YES");
            int closedSections = 0;

            for (int i = 0; i < remainingSections; i++) {
                try {
                    // Wait for "Close with 0" or "Yes" (all sections closed)
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 15,
                            closeWith0Btn, yesFinishBtn);
                    String foundText = found.getText();

                    if (foundText.equalsIgnoreCase("CLOSE WITH 0")) {
                        logStep("Step 10: Closing open section (" + (closedSections + 1) + "/" + remainingSections + ")...");
                        found.click();
                        closedSections++;
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                        // Dialog closes, back on main screen - tap Finish again for the next section
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else {
                        // "Yes" appeared - all sections are closed
                        logStep("Step 10: Finish dialog appeared, all sections already closed");
                        break;
                    }
                } catch (Exception e) {
                    logStep("Step 10: No dialog appeared after Finish (section " + (i + 1) + "): " + e.getMessage());
                    break;
                }
            }

            if (closedSections > 0) {
                logStep("Step 10: Closed " + closedSections + "/" + remainingSections + " remaining sections with 0");
            }

            // Handle "Finish whole inventory - Are you sure?" dialog
            // "Yes" = button3 (neutral), "Exit" = button1 (positive)
            try {
                org.openqa.selenium.WebElement yesBtn = WaitHelper.waitForAny(driver, 10,
                        yesFinishBtn, DIALOG_BUTTON_NEUTRAL);
                logStep("Step 10: Confirming 'Finish whole inventory'");
                yesBtn.click();
                Thread.sleep(AppConfig.LONG_WAIT); // Wait for upload to process
            } catch (Exception e) {
                logStep("Step 10: Finish confirmation dialog did not appear: " + e.getMessage());
            }

            // Handle packing list wizard if it appears
            // "Continue" = button3 (neutral), "Exit" = button1 (positive)
            By continueBtn = byText("Continue");
            if (WaitHelper.isElementPresent(driver, continueBtn)) {
                logStep("Step 10: Packing list wizard appeared, tapping Continue");
                driver.findElement(continueBtn).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);

                // The wizard may show individual items - keep dismissing with available buttons
                for (int attempt = 0; attempt < 10; attempt++) {
                    By uploadBtn = byText("Upload");
                    By acceptBtn = byText("Accept");
                    By skipBtn = byText("Skip");
                    continueBtn = byText("Continue");

                    if (WaitHelper.isElementPresent(driver, uploadBtn)) {
                        logStep("Step 10: Tapping Upload");
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

            // Wait for upload/processing to complete
            Thread.sleep(AppConfig.LONG_WAIT);

            // Dismiss any remaining dialogs (upload status, errors, etc.)
            dismissAnyDialog();
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Check for FinalConfirmActivity
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Step 10: Final confirmation: " + countInfo);
                confirmPage.tapLogout();
                logStep("Step 10: Logged out");
            } else {
                logStep("Step 10: Upload may still be processing. Activity: " + driver.currentActivity());
                // Wait longer for upload
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                dismissAnyDialog();
                if (confirmPage.isDisplayed()) {
                    confirmPage.tapLogout();
                    logStep("Step 10: Logged out after extended wait");
                }
            }
        } catch (Exception e) {
            logStep("Step 10: Finish failed: " + e.getMessage());
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

            // --- Fetch UPCs for this specific PC from barcodeMasterMultiUOM ---
            List<String> upcCodes;
            if (pcCode != null) {
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
                logStep("PC " + pcSlot + ": Could not return to PartsCategoryPage. Activity: " + driver.currentActivity());
                driver.navigate().back();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
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
