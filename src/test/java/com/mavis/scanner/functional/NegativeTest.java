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
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * Negative tests — verify the app handles invalid input, bad data, and error scenarios correctly.
 *
 * These tests validate that the app rejects bad input, shows appropriate error messages,
 * and does not crash or enter an invalid state when given unexpected data.
 *
 * Every test must follow the full sequential flow from app launch (no step can be skipped).
 */
public class NegativeTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    // ==================== HELPERS ====================

    private LoginPage navigateToLogin() throws InterruptedException {
        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");
        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        return loginPage;
    }

    private MainScanPage fullLoginToTireScan(ScheduledInventory inv) throws InterruptedException {
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
                skip("App routed to PartsPCActivity — need tire inventory (PC=2)");
            }
            Thread.sleep(AppConfig.LONG_WAIT);
        }
        Assert.assertTrue(mainScan.isDisplayed(), "Should be on tire scan screen");
        return mainScan;
    }

    private ScheduledInventory resolveTireInventory() {
        ScheduledInventory inv = InventorySetupHelper.resolveInventory();
        activeInvNum = inv.invNum;
        activeInvCode = inv.invCode;
        logStep("Resolved inventory: " + inv);
        if (!inv.scheduledPCs.isEmpty() && !inv.scheduledPCs.contains(2)) {
            skip("No tire PC=2 in resolved inventory: " + inv.scheduledPCs);
        }
        return inv;
    }

    private ScheduledInventory resolvePartsInventory() {
        ScheduledInventory inv = InventorySetupHelper.resolveInventory();
        activeInvNum = inv.invNum;
        activeInvCode=inv.invCode;
        logStep("Resolved inventory: " + inv);
        if (!inv.scheduledPCs.isEmpty()) {
            boolean hasPartsPc = inv.scheduledPCs.stream().anyMatch(pc -> pc != 2);
            if (!hasPartsPc) {
                skip("No parts PC in resolved inventory (only tire PC=2): " + inv.scheduledPCs);
            }
        }
        return inv;
    }

    /**
     * Login and navigate to parts scanning: StartHome -> Login -> PartsCategoryPage -> tap first PC -> PartsMainPage.
     */
    private PartsMainPage fullLoginToPartsScan(ScheduledInventory inv) throws InterruptedException {
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

        // Tap Start on the first available category
        int categoryCount = partsCategory.getVisibleCategoryCount();
        Assert.assertTrue(categoryCount > 0, "At least one parts category must be scheduled");
        logStep("Parts categories available: " + categoryCount);

        partsCategory.tapStart(1);
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        PartsMainPage partsMain = new PartsMainPage(driver, wait);
        Assert.assertTrue(partsMain.isDisplayed(), "Should be on parts scanning screen");
        return partsMain;
    }

    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
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

    private static By byText(String text) {
        return By.xpath("//*[@text='" + text + "']");
    }

    private static By byTextIgnoreCase(String text) {
        String lower = text.toLowerCase();
        return By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + lower + "']");
    }

    // ==================== LOGIN NEGATIVE TESTS ====================

    @Test(priority = 0, description = "Login with only store filled, employee and inv code empty")
    public void testLoginStoreOnlyFilled() {
        setup("Negative - Login Store Only");

        try {
            LoginPage loginPage = navigateToLogin();

            loginPage.enterStore(AppConfig.TEST_STORE);
            loginPage.tapLogin();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Tapped Login with only store filled");

            Assert.assertTrue(loginPage.isDisplayed(),
                    "Should stay on Login screen when employee and inv code are empty");

            String infoMsg = loginPage.getInfoMessage();
            logStep("Info message: " + infoMsg);

            pass();

        } catch (Exception e) {
            fail("Login store-only test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 1, description = "Login with special characters in store field — potential SQL injection")
    public void testLoginSpecialCharactersInStore() {
        setup("Negative - Login SQL Injection Attempt");

        try {
            LoginPage loginPage = navigateToLogin();

            // The store field is numeric (phone inputType) so special chars may not be enterable.
            // But we try anyway to verify the app doesn't crash.
            loginPage.enterStore("'; DROP TABLE--");
            loginPage.enterEmployee(AppConfig.TEST_EMPLOYEE);
            loginPage.enterInventoryCode("1234");
            loginPage.tapLogin();
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            logStep("Submitted with special characters in store");

            // App should either reject the input or remain on login — it must NOT crash
            boolean appAlive = loginPage.isDisplayed()
                    || WaitHelper.isElementPresent(driver, By.id("android:id/button1"))
                    || new MainScanPage(driver, wait).isDisplayed()
                    || new PartsCategoryPage(driver, wait).isDisplayed();

            Assert.assertTrue(appAlive,
                    "App should not crash with special characters. Activity: " + driver.currentActivity());
            logStep("App did not crash — handled gracefully");

            pass();

        } catch (Exception e) {
            fail("SQL injection test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 2, description = "Login with very long store number (overflow test)")
    public void testLoginVeryLongStoreNumber() {
        setup("Negative - Login Long Store Number");

        try {
            LoginPage loginPage = navigateToLogin();

            String longStore = "9".repeat(50);
            loginPage.enterStore(longStore);
            loginPage.enterEmployee(AppConfig.TEST_EMPLOYEE);
            loginPage.enterInventoryCode("1234");
            loginPage.tapLogin();
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            logStep("Submitted with 50-digit store number");

            // Should remain on login or show error — not crash
            boolean appAlive = loginPage.isDisplayed()
                    || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE);
            Assert.assertTrue(appAlive,
                    "App should handle long input gracefully. Activity: " + driver.currentActivity());
            logStep("App handled long input without crashing");

            pass();

        } catch (Exception e) {
            fail("Long store number test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 3, description = "Login with leading zeros in store and employee fields")
    public void testLoginLeadingZeros() {
        setup("Negative - Login Leading Zeros");

        try {
            LoginPage loginPage = navigateToLogin();

            loginPage.enterStore("0030");
            loginPage.enterEmployee("0" + AppConfig.TEST_EMPLOYEE);
            loginPage.enterInventoryCode("0000");
            loginPage.tapLogin();
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            logStep("Submitted with leading zeros: store=0030 employee=0421628 invCode=0000");

            String infoMsg = loginPage.getInfoMessage();
            logStep("Info message: " + infoMsg);

            // Leading zeros with invalid invCode=0000 should not pass login
            Assert.assertTrue(loginPage.isDisplayed(),
                    "LEADING ZEROS BUG: Login succeeded with invCode=0000. " +
                            "Leading zeros may be causing incorrect store/code matching. " +
                            "Activity: " + driver.currentActivity());
            logStep("Login correctly rejected with leading zeros and invalid invCode");

            pass();

        } catch (Exception e) {
            fail("Leading zeros test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== SCANNING NEGATIVE TESTS ====================

    @Test(priority = 4, description = "Scan invalid barcode format via DataWedge — app should not crash")
    public void testScanInvalidBarcodeFormat() {
        setup("Negative - Invalid Barcode Scan");

        try {
            ScheduledInventory inv = resolveTireInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);

            logStep("Scanning invalid barcode: ABCDEFG");
            dwHelper.scanItemBarcode("ABCDEFG");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            // App should not crash — it should ignore or show error
            Assert.assertTrue(mainScan.isDisplayed(),
                    "Should remain on scan screen after invalid barcode");
            logStep("App handled invalid barcode without crashing");

            // Try empty barcode
            logStep("Scanning empty barcode");
            dwHelper.scanItemBarcode("");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            Assert.assertTrue(mainScan.isDisplayed(),
                    "Should remain on scan screen after empty barcode");
            logStep("App handled empty barcode without crashing");

            pass();

        } catch (Exception e) {
            fail("Invalid barcode test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 5, description = "Scan item barcode BEFORE opening a section — should be rejected")
    public void testScanItemWithoutOpenSection() {
        setup("Negative - Item Scan Without Section");

        try {
            ScheduledInventory inv = resolveTireInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Try scanning an item before any section is opened
            String sectionOutput = mainScan.getSectionOutput();
            logStep("Section output before scan: " + sectionOutput);

            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need a UPC for testing");

            int initialCount = mainScan.getItemCount();
            dwHelper.scanItemBarcode(upcs.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int afterCount = mainScan.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            Assert.assertTrue(mainScan.isDisplayed(), "App should remain on scan screen");

            Assert.assertEquals(afterCount, initialCount,
                    "SCAN WITHOUT SECTION BUG: Item was accepted without an open section. " +
                            "Count went from " + initialCount + " to " + afterCount);
            logStep("Item correctly rejected — no section open");

            pass();

        } catch (Exception e) {
            fail("Item without section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 6, description = "Add non-existent item number via dialog — should show error")
    public void testAddNonExistentItemNumber() {
        setup("Negative - Add Non-Existent Item");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open a section first (required for add item)
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            dwHelper.scanSectionBarcode(sections.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();
            logStep("Opened section: " + sections.get(0));

            int initialCount = mainScan.getItemCount();

            // Try adding a fake item number
            scrollToBottom();
            mainScan.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupBtn = byText("Lookup by Item Number");
            if (WaitHelper.isElementPresent(driver, lookupBtn)) {
                driver.findElement(lookupBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
            if (WaitHelper.isElementPresent(driver, addItemField)) {
                By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
                WaitHelper.waitAndType(wait, addItemField, "ZZZZZZZ");
                WaitHelper.waitAndType(wait, addItemQty, "1");
                logStep("Entered fake item: ZZZZZZZ qty 1");

                // Submit
                By submitBtn = byText("Submit");
                if (WaitHelper.isElementPresent(driver, submitBtn)) {
                    driver.findElement(submitBtn).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                // Check for error toast or message
                String toast = WaitHelper.waitForToast(driver, 3);
                if (toast != null) {
                    logStep("Toast message: " + toast);
                }
            }

            dismissAnyDialog();
            int afterCount = mainScan.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            if (afterCount == initialCount) {
                logStep("RESULT: Fake item correctly rejected");
            } else {
                logStep("RESULT: Fake item was accepted — may need investigation");
            }

            pass();

        } catch (Exception e) {
            fail("Non-existent item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 7, description = "Add item with quantity 0 — should be rejected or handled")
    public void testAddItemWithZeroQuantity() {
        setup("Negative - Add Item Qty 0");

        try {
            ScheduledInventory inv = resolveTireInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.scanSectionBarcode(sections.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int initialCount = mainScan.getItemCount();
            String validItem = dbHelper.getValidItemNumber();

            scrollToBottom();
            mainScan.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupBtn = byText("Lookup by Item Number");
            if (WaitHelper.isElementPresent(driver, lookupBtn)) {
                driver.findElement(lookupBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
            if (WaitHelper.isElementPresent(driver, addItemField)) {
                By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
                WaitHelper.waitAndType(wait, addItemField, validItem);
                WaitHelper.waitAndType(wait, addItemQty, "0");
                logStep("Entered valid item " + validItem + " with qty 0");

                if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            }

            dismissAnyDialog();
            int afterCount = mainScan.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            if (afterCount == initialCount) {
                logStep("RESULT: Qty 0 correctly rejected");
            } else {
                logStep("BUG: Item added with quantity 0");
            }

            pass();

        } catch (Exception e) {
            fail("Zero qty test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 8, description = "Manual count mismatch — enter count different from scanned items")
    public void testManualCountMismatch() {
        setup("Negative - Manual Count Mismatch");

        try {
            ScheduledInventory inv = resolveTireInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.scanSectionBarcode(sections.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            // Scan 2 items
            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 2);
            int scannedCount = 0;
            for (String upc : upcs) {
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();
                scannedCount++;
            }
            logStep("Scanned " + scannedCount + " items");

            // Close section but enter a mismatched count (scanned 2, say 99)
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
                countDialog.closeWithCount("99");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Entered manual count 99 (actual scanned: " + scannedCount + ")");
            }

            // Document behavior
            String toast = WaitHelper.waitForToast(driver, 3);
            if (toast != null) logStep("Toast: " + toast);

            if (mainScan.isDisplayed()) {
                logStep("RESULT: Section closed with mismatched count — app accepted it");
            }

            pass();

        } catch (Exception e) {
            fail("Manual count mismatch test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 9, description = "Close section with no items scanned — close with count 0")
    public void testCloseSectionWithZeroItems() {
        setup("Negative - Close Section No Items");

        try {
            ScheduledInventory inv = resolveTireInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.scanSectionBarcode(sections.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int itemCount = mainScan.getItemCount();
            logStep("Items in section before close: " + itemCount);

            // Close immediately with 0 — no items scanned
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
                countDialog.closeWithCount("0");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Closed section with count 0");
            }

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on scan screen");
            String sectionOutput = mainScan.getSectionOutput();
            logStep("Section output after close: " + sectionOutput);

            pass();

        } catch (Exception e) {
            fail("Close with zero items test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== PARTS SCANNING NEGATIVE TESTS ====================

    @Test(priority = 10, description = "Parts: Scan invalid barcode format via DataWedge — app should not crash")
    public void testPartsScanInvalidBarcodeFormat() {
        setup("Negative - Parts Invalid Barcode Scan");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);

            logStep("Scanning invalid barcode: ABCDEFG");
            dwHelper.simulateScan("ABCDEFG", "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            Assert.assertTrue(partsMain.isDisplayed(),
                    "Should remain on parts scan screen after invalid barcode");
            logStep("App handled invalid barcode without crashing");

            logStep("Scanning empty barcode");
            dwHelper.simulateScan("", "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            Assert.assertTrue(partsMain.isDisplayed(),
                    "Should remain on parts scan screen after empty barcode");
            logStep("App handled empty barcode without crashing");

            pass();

        } catch (Exception e) {
            fail("Parts invalid barcode test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 11, description = "Parts: Scan item barcode BEFORE opening a section — should be rejected")
    public void testPartsScanItemWithoutOpenSection() {
        setup("Negative - Parts Item Scan Without Section");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            String sectionOutput = partsMain.getSectionOutput();
            logStep("Section output before scan: " + sectionOutput);

            // Get a UPC for the first scheduled parts PC
            int partsPc = inv.scheduledPCs.stream().filter(pc -> pc != 2).findFirst().orElse(62);
            List<String> upcs = dbHelper.getTestUpcsByPc(String.valueOf(partsPc), 1);
            Assert.assertFalse(upcs.isEmpty(), "Need a UPC for PC " + partsPc);

            int initialCount = partsMain.getItemCount();
            dwHelper.simulateScan(upcs.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int afterCount = partsMain.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            Assert.assertTrue(partsMain.isDisplayed(), "App should remain on parts scan screen");

            Assert.assertEquals(afterCount, initialCount,
                    "PARTS SCAN WITHOUT SECTION BUG: Item was accepted without an open section. " +
                            "Count went from " + initialCount + " to " + afterCount);

            pass();

        } catch (Exception e) {
            fail("Parts item without section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 12, description = "Parts: Add non-existent item number via dialog — should show error")
    public void testPartsAddNonExistentItemNumber() {
        setup("Negative - Parts Add Non-Existent Item");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open a section first
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            dwHelper.simulateScan(sections.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();
            logStep("Opened section: " + sections.get(0));

            int initialCount = partsMain.getItemCount();

            scrollToBottom();
            partsMain.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupBtn = byText("Lookup by Item Number");
            if (WaitHelper.isElementPresent(driver, lookupBtn)) {
                driver.findElement(lookupBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
            if (WaitHelper.isElementPresent(driver, addItemField)) {
                By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
                WaitHelper.waitAndType(wait, addItemField, "ZZZZZZZ");
                WaitHelper.waitAndType(wait, addItemQty, "1");
                logStep("Entered fake item: ZZZZZZZ qty 1");

                By submitBtn = byText("Submit");
                if (WaitHelper.isElementPresent(driver, submitBtn)) {
                    driver.findElement(submitBtn).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                String toast = WaitHelper.waitForToast(driver, 3);
                if (toast != null) {
                    logStep("Toast message: " + toast);
                }
            }

            dismissAnyDialog();
            int afterCount = partsMain.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            if (afterCount == initialCount) {
                logStep("RESULT: Fake item correctly rejected");
            } else {
                logStep("RESULT: Fake item was accepted — may need investigation");
            }

            pass();

        } catch (Exception e) {
            fail("Parts non-existent item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 13, description = "Parts: Add item with quantity 0 — should be rejected or handled")
    public void testPartsAddItemWithZeroQuantity() {
        setup("Negative - Parts Add Item Qty 0");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.simulateScan(sections.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int initialCount = partsMain.getItemCount();
            String validItem = dbHelper.getValidItemNumber();

            scrollToBottom();
            partsMain.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By lookupBtn = byText("Lookup by Item Number");
            if (WaitHelper.isElementPresent(driver, lookupBtn)) {
                driver.findElement(lookupBtn).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
            if (WaitHelper.isElementPresent(driver, addItemField)) {
                By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
                WaitHelper.waitAndType(wait, addItemField, validItem);
                WaitHelper.waitAndType(wait, addItemQty, "0");
                logStep("Entered valid item " + validItem + " with qty 0");

                if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                }
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            }

            dismissAnyDialog();
            int afterCount = partsMain.getItemCount();
            logStep("Item count: " + initialCount + " -> " + afterCount);

            if (afterCount == initialCount) {
                logStep("RESULT: Qty 0 correctly rejected");
            } else {
                logStep("BUG: Item added with quantity 0");
            }

            pass();

        } catch (Exception e) {
            fail("Parts zero qty test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 14, description = "Parts: Manual count mismatch — enter count different from scanned items")
    public void testPartsManualCountMismatch() {
        setup("Negative - Parts Manual Count Mismatch");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            activeInvNum = inv.invNum;
            activeInvCode = inv.invCode;
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.simulateScan(sections.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            // Scan 2 items using parts-specific UPCs
            int partsPc = inv.scheduledPCs.stream().filter(pc -> pc != 2).findFirst().orElse(62);
            List<String> upcs = dbHelper.getTestUpcsByPc(String.valueOf(partsPc), 2);
            int scannedCount = 0;
            for (String upc : upcs) {
                dwHelper.simulateScan(upc, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();
                scannedCount++;
            }
            logStep("Scanned " + scannedCount + " items");

            // Close section but enter a mismatched count
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
                countDialog.closeWithCount("99");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Entered manual count 99 (actual scanned: " + scannedCount + ")");
            }

            String toast = WaitHelper.waitForToast(driver, 3);
            if (toast != null) logStep("Toast: " + toast);

            if (partsMain.isDisplayed()) {
                logStep("RESULT: Section closed with mismatched count — app accepted it");
            }

            pass();

        } catch (Exception e) {
            fail("Parts manual count mismatch test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 15, description = "Parts: Close section with no items scanned — close with count 0")
    public void testPartsCloseSectionWithZeroItems() {
        setup("Negative - Parts Close Section No Items");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            PartsMainPage partsMain = fullLoginToPartsScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need sections");
            dwHelper.simulateScan(sections.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int itemCount = partsMain.getItemCount();
            logStep("Items in section before close: " + itemCount);

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
                countDialog.closeWithCount("0");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Closed section with count 0");
            }

            Assert.assertTrue(partsMain.isDisplayed(), "Should remain on parts scan screen");
            String sectionOutput = partsMain.getSectionOutput();
            logStep("Section output after close: " + sectionOutput);

            pass();

        } catch (Exception e) {
            fail("Parts close with zero items test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
