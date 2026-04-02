package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.utils.BatteryScanHelper;
import com.mavis.scanner.utils.DatabaseHelper;
import com.mavis.scanner.utils.WaitHelper;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * End-to-end tests for Battery Returns and Battery Receive workflows.
 *
 * Flow: StartHome -> BatteryLogin -> BatteryMenu -> (Receive or Returns)
 *
 * Battery Returns follows a 3-step wizard:
 *   1. Rotates (scan or skip)
 *   2. Warranty (scan or skip)
 *   3. Cores (enter count)
 *   -> Summary -> Upload
 *
 * Battery Receive:
 *   Scan items -> Complete -> Count validation -> Summary -> Done
 */
public class BatteryEndToEndTest extends BaseTest {

    private BatteryScanHelper batteryScan;
    private DatabaseHelper dbHelper;

    @BeforeMethod
    public void init() {
        setup("Battery E2E");
        batteryScan = new BatteryScanHelper(driver, wait);
        dbHelper = new DatabaseHelper(driver);
    }

    @AfterMethod
    public void cleanup() {
        teardown();
    }

    // ==================== BATTERY RECEIVE ====================

//    @Test(description = "Battery Receive: login, scan items, complete with count validation")
//    public void testBatteryReceiveFlow() throws InterruptedException {
//        logStep("Navigate to Battery Login from StartHome");
//        StartHomePage startHome = new StartHomePage(driver, wait);
//        Assert.assertTrue(startHome.isDisplayed(), "StartHome should be displayed");
//        startHome.tapBattery();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        logStep("Login to battery module");
//        BatteryLoginPage loginPage = new BatteryLoginPage(driver, wait);
//        Assert.assertTrue(loginPage.isDisplayed(), "Battery login should be displayed");
//        loginPage.login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE);
//        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
//
//        logStep("Verify Battery Menu is displayed");
//        BatteryMenuPage menuPage = new BatteryMenuPage(driver, wait);
//        Assert.assertTrue(menuPage.isDisplayed(), "Battery menu should be displayed");
//        Assert.assertTrue(menuPage.isReceiveButtonDisplayed(), "Receive button should be visible");
//        Assert.assertTrue(menuPage.isReturnsButtonDisplayed(), "Returns button should be visible");
//
//        logStep("Navigate to Battery Receive");
//        menuPage.tapBatteryReceive();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        BatteryReceivePage receivePage = new BatteryReceivePage(driver, wait);
//        Assert.assertTrue(receivePage.isDisplayed(), "Battery Receive screen should be displayed");
//
//        logStep("Get battery UPCs for scanning (PC=65)");
//        List<String> batteryUpcs = dbHelper.getUpcsFromPartsFile(
//                String.valueOf(AppConfig.PC_BATTERIES), 3);
//        if (batteryUpcs.isEmpty()) {
//            batteryUpcs = dbHelper.getTestUpcsByPc(String.valueOf(AppConfig.PC_BATTERIES), 3);
//        }
//        Assert.assertFalse(batteryUpcs.isEmpty(), "Should have battery UPCs to scan");
//
//        logStep("Scan " + batteryUpcs.size() + " battery items");
//        batteryScan.resetCount();
//        for (String upc : batteryUpcs) {
//            batteryScan.scanReceive(upc);
//            logStep("Scanned: " + upc);
//        }
//
//        logStep("Verify items appear in list");
//        int itemCount = receivePage.getItemCount();
//        Assert.assertTrue(itemCount > 0, "Scanned items should appear in the list");
//        logStep("Items in list: " + itemCount);
//
//        logStep("Complete battery receive scan");
//        receivePage.tapComplete();
//        Thread.sleep(AppConfig.SHORT_WAIT);
//
//        // "Completing battery scan" dialog -> tap Complete
//        receivePage.tapDialogComplete();
//        Thread.sleep(AppConfig.SHORT_WAIT);
//
//        logStep("Enter manual count for validation");
//        Assert.assertTrue(receivePage.isCountValidationDisplayed(),
//                "Count validation dialog should appear");
//        receivePage.enterCountAndSubmit(String.valueOf(batteryScan.getScanCount()));
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        logStep("Verify scan summary and confirm");
//        receivePage.tapVerifyComplete();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        logStep("Battery Receive completed successfully");
//        pass();
//    }

    // ==================== BATTERY RETURNS ====================

    @Test(description = "Battery Returns: full wizard flow with rotates, warranty, and cores")
    public void testBatteryReturnsFullFlow() throws InterruptedException {
        logStep("Navigate to Battery Login from StartHome");
        StartHomePage startHome = new StartHomePage(driver, wait);
        startHome.tapBattery();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        logStep("Login to battery module");
        BatteryLoginPage loginPage = new BatteryLoginPage(driver, wait);
        loginPage.login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

        logStep("Navigate to Battery Returns from menu");
        BatteryMenuPage menuPage = new BatteryMenuPage(driver, wait);
        Assert.assertTrue(menuPage.isDisplayed(), "Battery menu should be displayed");
        menuPage.tapBatteryReturns();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        BatteryReturnWizardPage wizardPage = new BatteryReturnWizardPage(driver, wait);

        // --- Step 1: Rotates ---
        logStep("Wizard Step 1: Stock rotate batteries");
        Assert.assertTrue(wizardPage.isWizardDisplayed(), "Wizard should be displayed");
        String question1 = wizardPage.getQuestionText();
        logStep("Question: " + question1);

        wizardPage.tapYes();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // Now on BatteryReturnActivity for rotates
        logStep("Scanning rotate batteries");
        BatteryReturnPage returnPage = new BatteryReturnPage(driver, wait);
        Assert.assertTrue(returnPage.isDisplayed(), "Battery Return scan screen should appear");

        List<String> batteryUpcs = dbHelper.getUpcsFromPartsFile(
                String.valueOf(AppConfig.PC_BATTERIES), 2);
        if (batteryUpcs.isEmpty()) {
            batteryUpcs = dbHelper.getTestUpcsByPc(String.valueOf(AppConfig.PC_BATTERIES), 2);
        }
        Assert.assertFalse(batteryUpcs.isEmpty(), "Should have battery UPCs to scan");

        batteryScan.resetCount();
        for (String upc : batteryUpcs) {
            batteryScan.scanReturn(upc);
            logStep("Scanned rotate: " + upc);
        }

        logStep("Complete rotates scanning");
        returnPage.tapComplete();
        Thread.sleep(AppConfig.SHORT_WAIT);
        returnPage.tapDialogComplete();
        Thread.sleep(AppConfig.SHORT_WAIT);

        Assert.assertTrue(returnPage.isCountValidationDisplayed(),
                "Count validation should appear for rotates");
        returnPage.enterCountAndSubmit(String.valueOf(batteryScan.getScanCount()));
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // Back to wizard for step 2
        // --- Step 2: Warranty ---
        logStep("Wizard Step 2: Warranty batteries");
        Assert.assertTrue(wizardPage.isWizardDisplayed(), "Wizard should return for step 2");
        String question2 = wizardPage.getQuestionText();
        logStep("Question: " + question2);

        // Skip warranty for this test
        wizardPage.tapNo();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // --- Step 3: Cores ---
        logStep("Wizard Step 3: Enter core count");
        Assert.assertTrue(wizardPage.isCoreCountInputDisplayed(),
                "Core count input should be displayed");
        wizardPage.enterCoreCount("5");
        wizardPage.tapSubmit();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // --- Summary ---
        logStep("Verify return summary");
        Assert.assertTrue(wizardPage.isSummaryDisplayed(), "Summary should be displayed");
        String rotatesCount = wizardPage.getRotatesCount();
        String warrantiesCount = wizardPage.getWarrantiesCount();
        String coresCount = wizardPage.getCoresCount();
        logStep("Summary - Rotates: " + rotatesCount +
                ", Warranties: " + warrantiesCount +
                ", Cores: " + coresCount);

        logStep("Complete battery returns (upload)");
        wizardPage.tapCompleteReturns();
        Thread.sleep(AppConfig.LONG_WAIT);

        // Completion dialog with PO number
        if (wizardPage.isCompletionDialogDisplayed()) {
            logStep("Upload complete - dismissing confirmation");
            wizardPage.tapCompletionOk();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
        }

        logStep("Battery Returns completed successfully");
        pass();
    }

    @Test(description = "Battery Returns: skip all scans (No rotates, No warranty, 0 cores)")
    public void testBatteryReturnsSkipAll() throws InterruptedException {
        logStep("Navigate to Battery Login from StartHome");
        StartHomePage startHome = new StartHomePage(driver, wait);
        startHome.tapBattery();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        logStep("Login to battery module");
        BatteryLoginPage loginPage = new BatteryLoginPage(driver, wait);
        loginPage.login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

        logStep("Navigate to Battery Returns");
        BatteryMenuPage menuPage = new BatteryMenuPage(driver, wait);
        menuPage.tapBatteryReturns();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        BatteryReturnWizardPage wizardPage = new BatteryReturnWizardPage(driver, wait);

        logStep("Step 1: Skip rotates");
        wizardPage.tapNo();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        logStep("Step 2: Skip warranty");
        wizardPage.tapNo();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        logStep("Step 3: Enter 0 cores");
        wizardPage.enterCoreCount("0");
        wizardPage.tapSubmit();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        logStep("Verify summary shows zero counts");
        Assert.assertTrue(wizardPage.isSummaryDisplayed(), "Summary should be displayed");

        logStep("Complete battery returns");
        wizardPage.tapCompleteReturns();
        Thread.sleep(AppConfig.LONG_WAIT);

        if (wizardPage.isCompletionDialogDisplayed()) {
            wizardPage.tapCompletionOk();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
        }

        logStep("Battery Returns (skip all) completed successfully");
        pass();
    }

    // ==================== BATTERY RECEIVE - MANUAL ADD ====================

//    @Test(description = "Battery Receive: add items manually via Add Item dialog")
//    public void testBatteryReceiveManualAdd() throws InterruptedException {
//        logStep("Navigate to Battery Login from StartHome");
//        StartHomePage startHome = new StartHomePage(driver, wait);
//        startHome.tapBattery();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        logStep("Login");
//        BatteryLoginPage loginPage = new BatteryLoginPage(driver, wait);
//        loginPage.login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE);
//        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
//
//        logStep("Navigate to Battery Receive");
//        BatteryMenuPage menuPage = new BatteryMenuPage(driver, wait);
//        menuPage.tapBatteryReceive();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        BatteryReceivePage receivePage = new BatteryReceivePage(driver, wait);
//        Assert.assertTrue(receivePage.isDisplayed(), "Battery Receive should be displayed");
//
//        logStep("Tap Add Item to open manual entry dialog");
//        receivePage.tapAddItem();
//        Thread.sleep(AppConfig.SHORT_WAIT);
//
//        // The Add Item dialog uses the same layout as inventory add item
//        logStep("Get a valid battery item number for manual entry");
//        String batteryItem = dbHelper.getValidItemNumberByPc(String.valueOf(AppConfig.PC_BATTERIES));
//        logStep("Using battery item: " + batteryItem);
//
//        com.mavis.scanner.pages.dialogs.AddItemDialog addDialog =
//                new com.mavis.scanner.pages.dialogs.AddItemDialog(driver, wait);
//        if (addDialog.isDisplayed()) {
//            addDialog.addItem(batteryItem, "2");
//            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
//            logStep("Manually added item " + batteryItem + " qty 2");
//        }
//
//        int itemCount = receivePage.getItemCount();
//        Assert.assertTrue(itemCount > 0, "Manually added item should appear in list");
//
//        logStep("Complete with count validation (manual count = 2)");
//        receivePage.tapComplete();
//        Thread.sleep(AppConfig.SHORT_WAIT);
//        receivePage.tapDialogComplete();
//        Thread.sleep(AppConfig.SHORT_WAIT);
//
//        if (receivePage.isCountValidationDisplayed()) {
//            receivePage.enterCountAndSubmit("2");
//            Thread.sleep(AppConfig.MEDIUM_WAIT);
//        }
//
//        receivePage.tapVerifyComplete();
//        Thread.sleep(AppConfig.MEDIUM_WAIT);
//
//        logStep("Battery Receive (manual add) completed successfully");
//        pass();
//    }
}
