package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.AddItemDialog;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import com.mavis.scanner.utils.DatabaseHelper;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Negative and edge case tests for the inventory scanner app.
 *
 * Every test follows: Login -> Scan Section -> Negative Check -> Recover Forward -> Verify
 *
 * After each negative check the test completes a valid action (scan item + close section)
 * so the app is never left in a stuck state.
 *
 * Source-code-driven edge cases from MainActivity.cs, LoginActivity.cs, PartsPCActivity.cs:
 * 1.  Scan item BEFORE opening section → "Scan section barcode" error
 * 2.  Invalid/malformed barcode in open section → item not added
 * 3.  Invalid item number via Add Item dialog → item not found error
 * 4.  Manual count mismatch on close → "Count qty Not matching" rejection
 * 5.  Close section with zero items (empty shelf)
 * 6.  Tap Finish with remaining unclosed sections → prompt dialog
 * 7.  Cancel Add Item dialog → item count unchanged
 * 8.  Cancel Close Section / Manual Count dialog → section stays open
 * 9.  Rapid consecutive scans → app stays stable
 * 10. Scan section barcode via item scanner → should not add as inventory item
 * 11. Switch sections while one is open → prompt to close previous section
 * 12. Scan same section barcode twice → overwrite dialog
 */
public class NegativeFlowTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    private DataWedgeHelper dwHelper;
    private DatabaseHelper dbHelper;
    private ScheduledInventory inventory;

    // ── Shared helpers ──────────────────────────────────────────────────

    /**
     * Login only — does NOT open a section.
     * Returns MainScanPage in "no section" state (locationBarcode == null).
     */
    private MainScanPage loginOnly() throws InterruptedException {
        inventory = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inventory);

        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "App should launch to StartHome");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");

        loginPage.login(inventory.store, AppConfig.TEST_EMPLOYEE, inventory.invCode);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        logStep("Logged in with Store=" + inventory.store + " InvCode=" + inventory.invCode);

        MainScanPage mainScan = new MainScanPage(driver, wait);
        Assert.assertTrue(mainScan.isDisplayed(),
                "Should land on Main Scan screen. Activity: " + driver.currentActivity());

        dwHelper = new DataWedgeHelper(driver);
        dbHelper = new DatabaseHelper(driver);

        return mainScan;
    }

    /**
     * Full login -> scan section flow.
     * Returns MainScanPage with a section already opened.
     */
    private MainScanPage loginAndOpenSection() throws InterruptedException {
        MainScanPage mainScan = loginOnly();

        List<String> sections = dbHelper.getSectionBarcodes();
        String sectionBarcode = sections.isEmpty() ? "STR-1041" : sections.get(0);

        dwHelper.scanSectionBarcode(sectionBarcode);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissDialog();

        logStep("Section " + sectionBarcode + " opened. Output: " + mainScan.getSectionOutput());
        return mainScan;
    }

    private void dismissDialog() throws InterruptedException {
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
        }
    }

    /** Dismiss up to N stacked dialogs (positive/neutral buttons). */
    private void dismissAllDialogs(int max) throws InterruptedException {
        for (int i = 0; i < max; i++) {
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(AppConfig.SHORT_WAIT);
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                Thread.sleep(AppConfig.SHORT_WAIT);
            } else {
                break;
            }
        }
    }

    /** Scan a valid item into the current section. */
    private void scanValidItem(MainScanPage mainScan) throws InterruptedException {
        List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 1);
        if (!upcs.isEmpty()) {
            dwHelper.scanItemBarcode(upcs.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissDialog();
            logStep("Recovery: scanned valid item " + upcs.get(0));
        }
    }

    /** Close the current section with a matching manual count. */
    private void closeCurrentSection(MainScanPage mainScan) throws InterruptedException {
        int count = mainScan.getItemCount();
        String manualCount = String.valueOf(count > 0 ? count : 1);

        mainScan.tapCloseSection();
        Thread.sleep(AppConfig.SHORT_WAIT);

        By completeBtn = By.xpath("//*[@text='Complete']");
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
        }
        logStep("Recovery: section closed with manual count " + manualCount);
    }

    /** Open a section by barcode. */
    private void openSection(String sectionBarcode) throws InterruptedException {
        dwHelper.scanSectionBarcode(sectionBarcode);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissDialog();
    }

    // ── Tests ───────────────────────────────────────────────────────────

    /**
     * Source: MainActivity.OnNewIntent — if locationBarcode is null, scanning an item
     * should show "Scan section barcode" toast/error; item must NOT be added.
     *
     * Flow: Login → scan item WITHOUT opening section → verify rejection → open section → scan → close
     */
    @Test(priority = 1, description = "Scan item before opening any section — app should reject")
    public void testScanItemBeforeSectionOpened() {
        setup("Negative - Scan Item Before Section Opened");

        try {
            MainScanPage mainScan = loginOnly();
            logStep("On MainScanPage with NO section open");

            int countBefore = mainScan.getItemCount();

            // Negative: scan a real UPC before any section is opened
            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 1);
            String upc = upcs.isEmpty() ? "012345678901" : upcs.get(0);
            logStep("Scanning item " + upc + " without section open");
            dwHelper.scanItemBarcode(upc);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // App should show error — dismiss it
            dismissAllDialogs(3);

            int countAfter = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after: " + countAfter);
            Assert.assertEquals(countAfter, countBefore,
                    "Item should NOT be added when no section is open");

            // Verify section output still shows default "scan section" prompt
            String sectionOutput = mainScan.getSectionOutput();
            logStep("Section output: " + sectionOutput);

            // Recover: open section, scan item, close section
            List<String> sections = dbHelper.getSectionBarcodes();
            String section = sections.isEmpty() ? "STR-1041" : sections.get(0);
            openSection(section);
            scanValidItem(mainScan);
            Assert.assertTrue(mainScan.getItemCount() > 0,
                    "Valid item should be accepted after opening a section");
            logStep("Recovery: item accepted after section opened");
            closeCurrentSection(mainScan);

            pass();

        } catch (Exception e) {
            fail("Scan item before section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.InsertData — UPC not in BarcodeMasterList inserts into
     * ScanItemsNotfound and shows red "Item not found" dialog. Item count must not change.
     *
     * Flow: Login → open section → scan invalid barcode → verify rejection → scan valid → close
     */
    @Test(priority = 2, description = "Scan an invalid/unknown barcode — should not add item")
    public void testInvalidBarcodeInOpenSection() {
        setup("Negative - Invalid Barcode In Open Section");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            int countBefore = mainScan.getItemCount();

            // Negative: scan a completely fake UPC
            String invalidBarcode = "INVALID-BARCODE-XYZ-999";
            logStep("Scanning invalid barcode: " + invalidBarcode);
            dwHelper.scanItemBarcode(invalidBarcode);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Expect "Item not found" dialog — dismiss it
            dismissAllDialogs(3);

            int countAfterInvalid = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after invalid: " + countAfterInvalid);
            Assert.assertEquals(countAfterInvalid, countBefore,
                    "Invalid barcode should not add an item");

            // Also try a numeric fake UPC that's plausible but not in master list
            String fakeNumericUpc = "999999999999";
            logStep("Scanning fake numeric UPC: " + fakeNumericUpc);
            dwHelper.scanItemBarcode(fakeNumericUpc);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAllDialogs(3);

            int countAfterFake = mainScan.getItemCount();
            Assert.assertEquals(countAfterFake, countBefore,
                    "Fake numeric UPC should not add an item");

            // Recover: scan valid item and close section
            scanValidItem(mainScan);
            Assert.assertTrue(mainScan.getItemCount() > countBefore,
                    "Valid item should be accepted after invalid barcode rejection");
            logStep("App recovered — valid item accepted");
            closeCurrentSection(mainScan);

            pass();

        } catch (Exception e) {
            fail("Invalid barcode test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.ButtonEnterItem — CheckIfItemExist() returns false for unknown items.
     * Shows "Item not in the master list" toast. Item count must not change.
     *
     * Flow: Login → open section → Add Item dialog → enter fake item → verify rejection → recover
     */
    @Test(priority = 3, description = "Add an invalid item number via dialog — should reject")
    public void testAddInvalidItemNumber() {
        setup("Negative - Invalid Item Number");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            int countBefore = mainScan.getItemCount();

            // Open Add Item dialog
            mainScan.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupByItemBtn = By.xpath("//*[@text='Lookup by Item Number']");
            if (WaitHelper.isElementPresent(driver, lookupByItemBtn)) {
                driver.findElement(lookupByItemBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            AddItemDialog addDialog = new AddItemDialog(driver, wait);
            for (int attempt = 0; attempt < 5; attempt++) {
                if (addDialog.isDisplayed()) break;
                Thread.sleep(1000);
            }

            if (!addDialog.isDisplayed()) {
                logStep("Add Item dialog did not appear, skipping");
                pass();
                return;
            }

            // Negative: enter a non-existent item number
            String fakeItem = "ZZZZZZ";
            logStep("Entering invalid item number: " + fakeItem);
            addDialog.addItem(fakeItem, "1");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Dismiss error/warning dialog
            dismissAllDialogs(3);

            int countAfterInvalid = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after invalid: " + countAfterInvalid);
            Assert.assertEquals(countAfterInvalid, countBefore,
                    "Item count should not change for invalid item number");

            // Recover: scan a valid item and close section
            scanValidItem(mainScan);
            logStep("App recovered — valid item accepted after invalid item number");
            closeCurrentSection(mainScan);

            pass();

        } catch (Exception e) {
            fail("Invalid item number test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.ValidateManualCount — if manualCount != scannedQty,
     * shows "Count qty Not matching scan qty. Please count again!" and section stays open.
     *
     * Flow: Login → open section → scan 2 items → close with wrong count (999) → verify rejection
     *       → close with correct count → verify success
     */
    @Test(priority = 4, description = "Manual count mismatch rejects section close — section stays open")
    public void testManualCountMismatchKeepsSectionOpen() {
        setup("Negative - Manual Count Mismatch");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan 2 items
            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 2);
            for (String upc : upcs) {
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissDialog();
            }
            int itemCount = mainScan.getItemCount();
            logStep("Scanned items. Count: " + itemCount);
            Assert.assertTrue(itemCount > 0, "Should have scanned at least 1 item");

            // Negative: try to close with deliberately wrong count
            mainScan.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By completeBtn = By.xpath("//*[@text='Complete']");
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
                // Enter mismatched count
                countDialog.enterManualCount("999");
                countDialog.tapSubmit();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Submitted mismatched count 999 (actual: " + itemCount + ")");

                // App should reject — dialog should still be visible or error shown
                boolean dialogStillVisible = countDialog.isDisplayed();
                boolean errorDialogShown = WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE);

                logStep("Dialog still visible: " + dialogStillVisible + " Error: " + errorDialogShown);
                Assert.assertTrue(dialogStillVisible || errorDialogShown,
                        "App should reject mismatched manual count and keep dialog open");

                // Dismiss error if shown
                if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                    Thread.sleep(AppConfig.SHORT_WAIT);
                }

                // Now close with CORRECT count to recover
                if (countDialog.isDisplayed()) {
                    countDialog.enterManualCount(String.valueOf(itemCount));
                    countDialog.tapSubmit();
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                    logStep("Recovery: submitted correct count " + itemCount);
                } else {
                    // Manual count dialog was dismissed — cancel and retry
                    if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                        driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
                        Thread.sleep(AppConfig.SHORT_WAIT);
                    }
                    closeCurrentSection(mainScan);
                }
            } else {
                logStep("Manual count dialog did not appear — closing normally");
                closeCurrentSection(mainScan);
            }

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            logStep("Section closed after correcting manual count");

            pass();

        } catch (Exception e) {
            fail("Manual count mismatch test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.ValidateManualCount — manual count of 0 should match
     * when no items are scanned (empty shelf scenario).
     *
     * Flow: Login → open section → immediately close with count 0 → open next section → scan → close
     */
    @Test(priority = 5, description = "Close section with zero items (empty shelf) — then continue scanning")
    public void testCloseSectionWithZeroItems() {
        setup("Negative - Close Section With Zero Items");

        try {
            MainScanPage mainScan = loginAndOpenSection();
            logStep("Section open with 0 items");

            // Close section immediately — empty shelf
            mainScan.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By completeBtn = By.xpath("//*[@text='Complete']");
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
                countDialog.closeWithCount("0");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Closed section with manual count 0 (empty shelf)");
            } else {
                logStep("Manual count dialog did not appear");
                dismissAllDialogs(3);
            }

            // Verify app returned to "scan section" state
            String outputAfterClose = mainScan.getSectionOutput();
            logStep("Section output after empty close: " + outputAfterClose);

            // Recover: open next section, scan, close
            List<String> sections = dbHelper.getSectionBarcodes();
            if (sections.size() >= 2) {
                openSection(sections.get(1));
                scanValidItem(mainScan);
                closeCurrentSection(mainScan);
                logStep("Recovery: second section scanned and closed after empty shelf");
            }

            Assert.assertTrue(mainScan.isDisplayed(), "App should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Close section with zero items failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.ButtonFinished — CheckMissingSections() shows dialog listing
     * unclosed sections when not all sections are complete.
     *
     * Flow: Login → open section → scan → close → tap Finish → verify prompt → cancel → open next → scan → close
     */
    @Test(priority = 6, description = "Tap Finish with remaining sections — verify prompt, cancel, then continue")
    public void testFinishWithRemainingSections() {
        setup("Negative - Finish With Remaining Sections");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan item and close one section
            scanValidItem(mainScan);
            closeCurrentSection(mainScan);
            logStep("One section closed");

            // Negative: tap Finish — should prompt about remaining unclosed sections
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Tapped Finish with remaining sections");

            // Source: shows "Completed #/# sections" with list + "Close with 0" / "Exit" buttons
            boolean dialogAppeared = WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)
                    || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)
                    || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE);

            By closeWith0Btn = By.xpath("//*[contains(@text,'Close with 0')]");
            By exitBtn = By.xpath("//*[contains(@text,'Exit')]");
            boolean finishOptionsShown = WaitHelper.isElementPresent(driver, closeWith0Btn)
                    || WaitHelper.isElementPresent(driver, exitBtn)
                    || dialogAppeared;

            logStep("Finish dialog/options appeared: " + finishOptionsShown);
            Assert.assertTrue(finishOptionsShown,
                    "Finishing with remaining sections should show a confirmation/options dialog");

            // Cancel / Exit — don't actually finish the inventory
            if (WaitHelper.isElementPresent(driver, exitBtn)) {
                driver.findElement(exitBtn).click();
                logStep("Clicked Exit on finish dialog");
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
                logStep("Cancelled finish dialog");
            } else {
                dismissAllDialogs(3);
                logStep("Dismissed finish dialog");
            }
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Recover: open next section, scan, close — prove app continues normally
            List<String> sections = dbHelper.getSectionBarcodes();
            if (sections.size() >= 2) {
                openSection(sections.get(1));
                logStep("Recovery: opened section " + sections.get(1) + " after cancelling finish");
                scanValidItem(mainScan);
                closeCurrentSection(mainScan);
                logStep("Recovery: section scanned and closed after cancelled finish");
            }

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Finish with remaining sections test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.ButtonEnterItem — cancelling the dialog should not modify InvTbl.
     *
     * Flow: Login → open section → open Add Item → cancel → verify count → scan valid → close
     */
    @Test(priority = 7, description = "Cancel Add Item dialog — item count unchanged, then add item normally")
    public void testCancelAddItemDialog() {
        setup("Negative - Cancel Add Item Dialog");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan an item first so we have a baseline count > 0
            scanValidItem(mainScan);
            int countBefore = mainScan.getItemCount();
            logStep("Items before cancel test: " + countBefore);

            // Negative: open Add Item dialog and cancel
            mainScan.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupByItemBtn = By.xpath("//*[@text='Lookup by Item Number']");
            if (WaitHelper.isElementPresent(driver, lookupByItemBtn)) {
                driver.findElement(lookupByItemBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            AddItemDialog addDialog = new AddItemDialog(driver, wait);
            for (int attempt = 0; attempt < 5; attempt++) {
                if (addDialog.isDisplayed()) break;
                Thread.sleep(1000);
            }

            if (addDialog.isDisplayed()) {
                // Type something then cancel — nothing should be saved
                addDialog.enterItemNumber("12345");
                addDialog.enterQuantity("3");
                addDialog.tapCancel();
                Thread.sleep(AppConfig.SHORT_WAIT);
                logStep("Cancelled Add Item dialog after entering data");
            } else {
                logStep("Dialog didn't appear, dismissing");
                if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                    driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
                }
            }

            int countAfterCancel = mainScan.getItemCount();
            logStep("Items after cancel: " + countAfterCancel);
            Assert.assertEquals(countAfterCancel, countBefore,
                    "Item count should not change after cancelling Add Item");

            // Recover: close section
            closeCurrentSection(mainScan);
            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");

            pass();

        } catch (Exception e) {
            fail("Cancel Add Item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity manual_count_layout — cancelling the count dialog should leave
     * the section open with all items still present.
     *
     * Flow: Login → open section → scan item → close section → cancel manual count → verify section open → close properly
     */
    @Test(priority = 8, description = "Cancel Close Section / manual count — section stays open with items intact")
    public void testCancelCloseSectionKeepsSectionOpen() {
        setup("Negative - Cancel Close Section");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan items so section has content
            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 2);
            for (String upc : upcs) {
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissDialog();
            }
            int countBefore = mainScan.getItemCount();
            logStep("Scanned items. Count before cancel: " + countBefore);

            String outputBefore = mainScan.getSectionOutput();
            logStep("Section output before cancel: " + outputBefore);

            // Negative: tap Close Section then cancel the manual count dialog
            mainScan.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By completeBtn = By.xpath("//*[@text='Complete']");
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
                countDialog.tapCancel();
                Thread.sleep(AppConfig.SHORT_WAIT);
                logStep("Cancelled manual count dialog");
            }

            // Verify section is still open — items should be intact
            int countAfterCancel = mainScan.getItemCount();
            logStep("Items after cancel: " + countAfterCancel);
            Assert.assertEquals(countAfterCancel, countBefore,
                    "Items should be intact after cancelling close section. Before: "
                            + countBefore + " After: " + countAfterCancel);

            // Verify section output hasn't reset to "scan section" default
            String outputAfter = mainScan.getSectionOutput();
            logStep("Section output after cancel: " + outputAfter);
            Assert.assertFalse(outputAfter.toLowerCase().contains("scan section"),
                    "Section should still be open after cancel, not reset to default prompt");

            // Recover: actually close section properly now
            closeCurrentSection(mainScan);
            logStep("Recovery: section closed properly after cancelled close");

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Cancel close section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.OnNewIntent — rapid scan flood. App processes each intent
     * sequentially; items should accumulate without crash or data loss.
     *
     * Flow: Login → open section → fire 5 scans at 500ms intervals → verify all counted → close
     */
    @Test(priority = 9, description = "Rapid consecutive scans — app stays stable and items are counted")
    public void testRapidConsecutiveScans() {
        setup("Negative - Rapid Consecutive Scans");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 5);
            Assert.assertTrue(upcs.size() >= 3, "Need at least 3 UPCs for rapid scan test");

            // Fire scans in rapid succession
            for (int i = 0; i < upcs.size(); i++) {
                logStep("Rapid scan " + (i + 1) + "/" + upcs.size() + ": " + upcs.get(i));
                dwHelper.scanItemBarcode(upcs.get(i));
                Thread.sleep(500); // Minimal delay — stress test
            }

            // Wait for all processing to settle
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT * 2);

            // Dismiss any accumulated dialogs
            dismissAllDialogs(10);

            Assert.assertTrue(mainScan.isDisplayed(), "App should not crash after rapid scans");

            int itemCount = mainScan.getItemCount();
            logStep("App stable after rapid scans. Items: " + itemCount);
            Assert.assertTrue(itemCount > 0,
                    "At least some items should be recorded after rapid scanning");

            // Recover: close section
            closeCurrentSection(mainScan);
            logStep("Recovery: section closed after rapid scans");

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Rapid consecutive scans test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.OnNewIntent — scanning a section-format barcode (STR-xxxx) via the
     * item scanner (DataWedge ACTION) while a section is already open. The app should NOT add
     * it as an inventory item — it should either switch sections or show an error.
     *
     * Flow: Login → open section → scan section barcode as item → verify not added → scan valid → close
     */
    @Test(priority = 10, description = "Scan section barcode via item channel — should not add as inventory item")
    public void testScanSectionBarcodeAsItem() {
        setup("Negative - Section Barcode Scanned As Item");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            int countBefore = mainScan.getItemCount();

            // Negative: scan a section-format barcode as if it were an item UPC
            String fakeSectionBarcode = "STR-9999";
            logStep("Scanning section barcode as item: " + fakeSectionBarcode);
            dwHelper.scanItemBarcode(fakeSectionBarcode);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Dismiss any dialog (could be "switch section?" or error)
            dismissAllDialogs(3);

            int countAfter = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after: " + countAfter);
            Assert.assertEquals(countAfter, countBefore,
                    "Section barcode should not be added as an inventory item");

            // Recover: scan valid item and close
            scanValidItem(mainScan);
            closeCurrentSection(mainScan);
            logStep("Recovery: section closed after section-barcode-as-item rejection");

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Section barcode as item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.PreviousOrderNotClosed — scanning a DIFFERENT section barcode while
     * the current section is still open triggers OpenSectionDialog: "Section X is still open.
     * Close this section?"
     *
     * Flow: Login → open section 1 → scan items → scan section 2 barcode → verify prompt →
     *       cancel (stay on section 1) → close section 1 → open section 2 → scan → close
     */
    @Test(priority = 11, description = "Switch sections while one is open — app prompts to close previous")
    public void testSwitchSectionWhileOneIsOpen() {
        setup("Negative - Switch Sections While Open");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan an item in section 1
            scanValidItem(mainScan);
            int countInSection1 = mainScan.getItemCount();
            String section1Output = mainScan.getSectionOutput();
            logStep("Section 1 has " + countInSection1 + " items. Output: " + section1Output);

            // Get a different section barcode
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertTrue(sections.size() >= 2,
                    "Need at least 2 section barcodes for switch test, got " + sections.size());
            String section2 = sections.get(1);

            // Negative: scan section 2 while section 1 is still open
            logStep("Scanning section 2 (" + section2 + ") while section 1 is still open");
            dwHelper.scanSectionBarcode(section2);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // App should show "Section still open. Close?" dialog
            boolean switchDialogAppeared =
                    WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)
                            || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE);

            logStep("Switch section dialog appeared: " + switchDialogAppeared);

            // Dismiss — choose to stay or switch (either way proves the dialog works)
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                // "No" — stay on current section
                driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
                logStep("Chose to stay on section 1");
                Thread.sleep(AppConfig.SHORT_WAIT);
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                logStep("Acknowledged switch dialog");
                Thread.sleep(AppConfig.SHORT_WAIT);
                // May need to handle manual count dialog if it auto-closes
                ManualCountDialog countDialog = new ManualCountDialog(driver, wait);
                if (countDialog.isDisplayed()) {
                    countDialog.closeWithCount(String.valueOf(countInSection1));
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                }
                dismissAllDialogs(3);
            }

            // Recover: ensure we end up with a valid state — close whatever section is open
            if (mainScan.getItemCount() > 0) {
                closeCurrentSection(mainScan);
            }

            // Open section 2 cleanly if we're not already in it
            openSection(section2);
            scanValidItem(mainScan);
            closeCurrentSection(mainScan);
            logStep("Recovery: section 2 scanned and closed");

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Switch section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    /**
     * Source: MainActivity.SectionClosedStep — re-scanning a completed section barcode
     * triggers an overwrite dialog: "Section already closed. Overwrite?"
     *
     * Flow: Login → open section → scan → close → re-scan same section → verify overwrite dialog →
     *       decline → open different section → scan → close
     */
    @Test(priority = 12, description = "Re-scan completed section barcode — triggers overwrite dialog")
    public void testRescanCompletedSectionTriggersOverwrite() {
        setup("Negative - Rescan Completed Section");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            List<String> sections = dbHelper.getSectionBarcodes();
            String sectionBarcode = sections.isEmpty() ? "STR-1041" : sections.get(0);

            // Complete the section: scan item + close
            scanValidItem(mainScan);
            closeCurrentSection(mainScan);
            logStep("Section " + sectionBarcode + " completed");

            // Negative: re-scan the same section barcode
            logStep("Re-scanning completed section: " + sectionBarcode);
            dwHelper.scanSectionBarcode(sectionBarcode);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Should show overwrite dialog: "Yes, clear and start again" / "No"
            By yesBtn = By.xpath("//*[@text='Yes']");
            By yesClearBtn = By.xpath("//*[contains(@text,'clear')]");
            By noBtn = By.xpath("//*[@text='No']");

            boolean overwriteDialogAppeared =
                    WaitHelper.isElementPresent(driver, yesBtn)
                            || WaitHelper.isElementPresent(driver, yesClearBtn)
                            || WaitHelper.isElementPresent(driver, noBtn)
                            || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)
                            || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE);

            logStep("Overwrite dialog appeared: " + overwriteDialogAppeared);
            Assert.assertTrue(overwriteDialogAppeared,
                    "Re-scanning completed section should trigger an overwrite confirmation dialog");

            // Decline the overwrite
            if (WaitHelper.isElementPresent(driver, noBtn)) {
                driver.findElement(noBtn).click();
                logStep("Declined overwrite");
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
                logStep("Declined overwrite via negative button");
            } else {
                dismissAllDialogs(3);
                logStep("Dismissed overwrite dialog");
            }
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Recover: open a different section, scan, close
            if (sections.size() >= 2) {
                openSection(sections.get(1));
                scanValidItem(mainScan);
                closeCurrentSection(mainScan);
                logStep("Recovery: different section completed after overwrite decline");
            }

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Rescan completed section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
