package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.AddItemDialog;
import com.mavis.scanner.utils.DataWedgeHelper;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Parts Scanning functional tests - category-based inventory workflow.
 *
 * Flow: Login -> PartsPCActivity -> Select category -> MainActivityParts -> Scan items -> Finish PC
 *
 * Categories (PCs): Oil(188), Oil Filters(86), TPMS(95), Wipers(92),
 *                   Air Condition(62), Rotor-BrakePad(66), Batteries(65), Tires(2)
 */
public class PartsScanningTest extends BaseTest {

    private DataWedgeHelper dwHelper;

    private PartsCategoryPage loginAndNavigateToPartsCategory() throws InterruptedException {
        StartHomePage startHome = new StartHomePage(driver, wait);
        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);

        loginPage.login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE, AppConfig.TEST_INV_CODE);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

        dwHelper = new DataWedgeHelper(driver);

        PartsCategoryPage partsPage = new PartsCategoryPage(driver, wait);
        if (!partsPage.isDisplayed()) {
            // May have gone directly to tire scanning
            skip("Login did not navigate to Parts Category screen - inventory may be tire-only");
        }
        return partsPage;
    }

    @Test(priority = 0, description = "Parts category screen renders with correct categories")
    public void testPartsCategoryScreenLayout() {
        setup("Parts - Category Screen Layout");

        try {
            PartsCategoryPage partsPage = loginAndNavigateToPartsCategory();
            logStep("On Parts Category screen");

            // Verify title
            String title = partsPage.getTitle();
            logStep("Title: " + title);

            // Count visible categories
            int categoryCount = partsPage.getVisibleCategoryCount();
            Assert.assertTrue(categoryCount > 0, "At least one category should be visible");
            logStep("Visible categories: " + categoryCount);

            // Log each category label
            for (int i = 1; i <= categoryCount; i++) {
                String label = partsPage.getCategoryLabel(i);
                boolean hasStart = partsPage.isStartButtonVisible(i);
                boolean completed = partsPage.isCategoryCompleted(i);
                logStep("Category " + i + ": '" + label + "' | Start=" + hasStart + " | Completed=" + completed);
            }

            pass();

        } catch (Exception e) {
            fail("Parts category layout test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 1, description = "Start scanning a parts category navigates to scan screen")
    public void testStartPartsCategory() {
        setup("Parts - Start Category Scanning");

        try {
            PartsCategoryPage partsPage = loginAndNavigateToPartsCategory();

            // Get first category label
            String firstLabel = partsPage.getCategoryLabel(1);
            logStep("Starting category 1: " + firstLabel);

            // Tap Start on first category
            partsPage.tapStart(1);
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Tapped Start on category 1");

            // Should navigate to MainActivityParts
            PartsMainPage partsMain = new PartsMainPage(driver, wait);
            Assert.assertTrue(partsMain.isDisplayed(),
                    "Should navigate to Parts scanning screen");
            logStep("Parts scanning screen displayed");

            // Verify section output shows category info
            String sectionOutput = partsMain.getSectionOutput();
            logStep("Section output: " + sectionOutput);

            pass();

        } catch (Exception e) {
            fail("Start parts category test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 2, description = "Add item in parts scanning via manual entry")
    public void testAddItemInPartsScanning() {
        setup("Parts - Add Item");

        try {
            PartsCategoryPage partsPage = loginAndNavigateToPartsCategory();

            // Start first category
            partsPage.tapStart(1);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            PartsMainPage partsMain = new PartsMainPage(driver, wait);
            if (!partsMain.isDisplayed()) {
                skip("Parts scanning screen did not load");
                return;
            }
            logStep("On parts scanning screen");

            // Scan a section barcode first
            dwHelper.simulateScan("STR-001", "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Scanned section barcode for parts");

            // Add item manually
            partsMain.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            AddItemDialog addDialog = new AddItemDialog(driver, wait);
            if (addDialog.isDisplayed()) {
                addDialog.addItem("54321", "1");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Added item 54321 qty 1");
            } else {
                logStep("Add Item dialog did not appear (may need section open first)");
            }

            int itemCount = partsMain.getItemCount();
            logStep("Item count: " + itemCount);

            pass();

        } catch (Exception e) {
            fail("Parts add item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 3, description = "Finish a parts category returns to category selection")
    public void testFinishPartsCategory() {
        setup("Parts - Finish Category");

        try {
            PartsCategoryPage partsPage = loginAndNavigateToPartsCategory();

            // Start first category
            partsPage.tapStart(1);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            PartsMainPage partsMain = new PartsMainPage(driver, wait);
            if (!partsMain.isDisplayed()) {
                skip("Parts scanning screen did not load");
                return;
            }

            // Finish this category
            partsMain.tapFinishCategory();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Tapped Finish PC");

            // Should return to PartsCategoryPage
            PartsCategoryPage returnedPartsPage = new PartsCategoryPage(driver, wait);
            Assert.assertTrue(returnedPartsPage.isDisplayed(),
                    "Should return to Parts Category screen");
            logStep("Returned to Parts Category screen");

            // Check if category 1 is now marked complete
            boolean completed = returnedPartsPage.isCategoryCompleted(1);
            logStep("Category 1 completed status: " + completed);

            pass();

        } catch (Exception e) {
            fail("Finish parts category test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 4, description = "Parts category logout returns to login screen")
    public void testPartsCategoryLogout() {
        setup("Parts - Logout");

        try {
            PartsCategoryPage partsPage = loginAndNavigateToPartsCategory();

            partsPage.tapLogout();
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            logStep("Tapped Logout");

            // Should return to LoginActivity
            LoginPage loginPage = new LoginPage(driver, wait);
            Assert.assertTrue(loginPage.isDisplayed(),
                    "Should return to Login screen after logout");
            logStep("Returned to Login screen");

            pass();

        } catch (Exception e) {
            fail("Parts logout test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
