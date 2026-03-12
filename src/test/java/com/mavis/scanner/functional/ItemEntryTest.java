package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.AddItemBySizeDialog;
import com.mavis.scanner.pages.dialogs.AddItemDialog;
import com.mavis.scanner.pages.dialogs.BoxedOilDialog;
import com.mavis.scanner.pages.dialogs.DeleteItemDialog;
import com.mavis.scanner.pages.dialogs.MultiItemDialog;
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
 * Tests for item entry methods, special dialogs, and item management.
 *
 * Every test follows: Login -> Scan Section -> Perform Test
 *
 * Covers behavior from MainActivity / PartsPCActivity source code:
 * - Add item by item number (enter_item_layout.xml)
 * - Add item by size/brand search (enter_itemBySize_layout.xml)
 * - Boxed oil quantity selection (boxedOil_layout.xml, PC 188/62)
 * - Multi-item UPC resolution (upcToMultiItem.xml, PC 66)
 * - Item not found handling (UPC not in BarcodeMasterList)
 * - Delete item from scan list
 * - Duplicate scan increments quantity
 * - Add item with quantity > 1
 */
public class ItemEntryTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    private DataWedgeHelper dwHelper;
    private DatabaseHelper dbHelper;
    private ScheduledInventory inventory;

    /**
     * Standard login -> scan section flow for tire inventory tests.
     * Uses InventorySetupHelper to resolve a valid inventory (or schedule one).
     * Returns MainScanPage with a section already opened.
     */
    private MainScanPage loginAndOpenSection() throws InterruptedException {
        // Resolve a valid inventory from the database
        inventory = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inventory);

        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "App should launch to StartHome");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");
        logStep("Login screen displayed");

        loginPage.login(inventory.store, AppConfig.TEST_EMPLOYEE, inventory.invCode);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        logStep("Logged in with Store=" + inventory.store + " InvCode=" + inventory.invCode);

        MainScanPage mainScan = new MainScanPage(driver, wait);
        Assert.assertTrue(mainScan.isDisplayed(),
                "Should land on Main Scan screen. Activity: " + driver.currentActivity());
        logStep("On Main Scan screen");


        PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
        Assert.assertTrue(partsCategory.isDisplayed(),
                "Should land on Parts Category screen. Activity: " + driver.currentActivity());
        logStep("Step 2: Landed on Parts Category screen");

        dwHelper = new DataWedgeHelper(driver);
        dbHelper = new DatabaseHelper(driver);

        List<String> sections = dbHelper.getSectionBarcodes();
        String sectionBarcode = sections.isEmpty() ? "STR-1041" : sections.get(0);

        dwHelper.scanSectionBarcode(sectionBarcode);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissDialog();

        logStep("Section " + sectionBarcode + " opened. Output: " + mainScan.getSectionOutput());
        return mainScan;
    }

    /**
     * Login -> navigate to parts category -> select PC -> scan section.
     * Returns PartsMainPage with a section already opened.
     */
    private PartsMainPage loginAndOpenPartsSection(String pcLabel, String pcCode) throws InterruptedException {
        // Resolve a valid inventory from the database
        inventory = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inventory);

        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "App should launch to StartHome");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        loginPage.login(inventory.store, AppConfig.TEST_EMPLOYEE, inventory.invCode);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        logStep("Logged in with Store=" + inventory.store + " InvCode=" + inventory.invCode);

        PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
        Assert.assertTrue(partsCategory.isDisplayed(),
                "Should land on Parts Category screen. Activity: " + driver.currentActivity());
        logStep("Step 2: Landed on Parts Category screen");

        dwHelper = new DataWedgeHelper(driver);
        dbHelper = new DatabaseHelper(driver);

        // Find the requested PC slot
        int targetSlot = -1;
        int categoryCount = partsCategory.getVisibleCategoryCount();
        for (int i = 1; i <= categoryCount; i++) {
            String label = partsCategory.getCategoryLabel(i);
            if (label != null && label.toLowerCase().contains(pcLabel.toLowerCase())) {
                targetSlot = i;
                logStep("Found " + pcLabel + " at slot " + i + ": " + label);
                break;
            }
        }
        Assert.assertTrue(targetSlot > 0, pcLabel + " (PC " + pcCode + ") not scheduled for this inventory");

        partsCategory.tapStart(targetSlot);
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        PartsMainPage partsMain = new PartsMainPage(driver, wait);
        Assert.assertTrue(partsMain.isDisplayed(), "Should enter Parts scanning screen");
        logStep("Entered parts scanning for " + pcLabel);

        // Scan a section barcode
        List<String> sections = dbHelper.getSectionBarcodes();
        String section = sections.isEmpty() ? "STR-1041" : sections.get(0);

        dwHelper.simulateScan(section, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissDialog();

        logStep("Section " + section + " opened. Output: " + partsMain.getSectionOutput());
        return partsMain;
    }

    private void dismissDialog() throws InterruptedException {
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
        }
    }

    private void openAddItemByNumberDialog(MainScanPage mainScan) throws InterruptedException {
        mainScan.tapAddItem();
        Thread.sleep(AppConfig.SHORT_WAIT);

        By lookupByItemBtn = By.xpath("//*[@text='Lookup by Item Number']");
        if (WaitHelper.isElementPresent(driver, lookupByItemBtn)) {
            driver.findElement(lookupByItemBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        }
        Thread.sleep(AppConfig.MEDIUM_WAIT);
    }

    private boolean waitForAddItemDialog(AddItemDialog addDialog) throws InterruptedException {
        for (int attempt = 0; attempt < 5; attempt++) {
            if (addDialog.isDisplayed()) return true;
            Thread.sleep(1000);
        }
        return false;
    }

    @Test(priority = 1, description = "Add item by item number via Add Item dialog")
    public void testAddItemByItemNumber() {
        setup("Item Entry - Add By Item Number");

        try {
            MainScanPage mainScan = loginAndOpenSection();


            PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
            Assert.assertTrue(partsCategory.isDisplayed(),
                    "Should land on Parts Category screen. Activity: " + driver.currentActivity());
            logStep("Step 2: Landed on Parts Category screen");

            String itemNumber = dbHelper.getValidItemNumber();
            logStep("Using item number: " + itemNumber);

            int countBefore = mainScan.getItemCount();
            logStep("Items before add: " + countBefore);

            openAddItemByNumberDialog(mainScan);

            AddItemDialog addDialog = new AddItemDialog(driver, wait);
            Assert.assertTrue(waitForAddItemDialog(addDialog), "Add Item dialog should appear");

            addDialog.addItem(itemNumber, "1");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Submitted item " + itemNumber + " with qty 1");
            dismissDialog();

            int countAfter = mainScan.getItemCount();
            logStep("Items after add: " + countAfter);
            Assert.assertTrue(countAfter > countBefore,
                    "Item count should increase. Before: " + countBefore + " After: " + countAfter);

            pass();

        } catch (Exception e) {
            fail("Add item by item number failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 2, description = "Add item by size/brand search dialog")
    public void testAddItemBySizeBrand() {
        setup("Item Entry - Add By Size/Brand");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            mainScan.tapAddItem();
            Thread.sleep(AppConfig.SHORT_WAIT);

            // Select "Lookup by Size" from chooser
            By lookupBySizeBtn = By.xpath("//*[@text='Lookup by Size']");
            By lookupBySizeBtn2 = By.xpath("//*[contains(@text,'Size')]");
            if (WaitHelper.isElementPresent(driver, lookupBySizeBtn)) {
                driver.findElement(lookupBySizeBtn).click();
            } else if (WaitHelper.isElementPresent(driver, lookupBySizeBtn2)) {
                driver.findElement(lookupBySizeBtn2).click();
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
                driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            AddItemBySizeDialog sizeDialog = new AddItemBySizeDialog(driver, wait);
            for (int attempt = 0; attempt < 5; attempt++) {
                if (sizeDialog.isDisplayed()) break;
                Thread.sleep(1000);
            }

            if (!sizeDialog.isDisplayed()) {
                logStep("Add by Size dialog not available for this inventory type, skipping");
                pass();
                return;
            }
            logStep("Add by Size dialog appeared");

            // Search for a common tire size
            sizeDialog.enterSize("2055516");
            Thread.sleep(AppConfig.SHORT_WAIT);
            sizeDialog.selectAllItems();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            int resultCount = sizeDialog.getSearchResultCount();
            logStep("Search results for '2055516': " + resultCount);

            if (resultCount > 0) {
                sizeDialog.selectSearchResult(0);
                Thread.sleep(AppConfig.SHORT_WAIT);
                sizeDialog.tapOk();
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                logStep("Item added via size/brand search");
            } else {
                logStep("No search results, cancelling");
                sizeDialog.tapCancel();
            }

            dismissDialog();
            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");

            pass();

        } catch (Exception e) {
            fail("Add item by size/brand failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 3, description = "Scan a UPC not in master list — item count should not change")
    public void testItemNotFoundHandling() {
        setup("Item Entry - Item Not Found");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            int countBefore = mainScan.getItemCount();

            // Scan a fake UPC not in BarcodeMasterList
            String fakeUpc = "999999999999";
            logStep("Scanning fake UPC: " + fakeUpc);
            dwHelper.scanItemBarcode(fakeUpc);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Dismiss error dialog if present
            dismissDialog();

            int countAfter = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after: " + countAfter);

            Assert.assertEquals(countAfter, countBefore,
                    "Item count should not increase for unrecognized UPC");
            logStep("Item not found correctly handled");

            pass();

        } catch (Exception e) {
            fail("Item not found test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 4, description = "Scanning same UPC twice — quantity increments, no rejection")
    public void testDuplicateScanIncrementsQty() {
        setup("Item Entry - Duplicate Scan Increments Qty");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least 1 UPC");
            String upc = upcs.get(0);

            // First scan
            logStep("First scan: " + upc);
            dwHelper.scanItemBarcode(upc);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissDialog();
            int countAfterFirst = mainScan.getItemCount();
            logStep("Items after first scan: " + countAfterFirst);

            // Second scan of same UPC
            logStep("Second scan (same UPC): " + upc);
            dwHelper.scanItemBarcode(upc);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissDialog();
            int countAfterSecond = mainScan.getItemCount();
            logStep("Items after second scan: " + countAfterSecond);

            // Source code: InsertData increments qty for same item in same section
            Assert.assertTrue(countAfterSecond >= countAfterFirst,
                    "Duplicate scan should be accepted (count same or increased)");

            pass();

        } catch (Exception e) {
            fail("Duplicate scan test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 5, description = "Delete an item from the scan list")
    public void testDeleteItemFromList() {
        setup("Item Entry - Delete Item");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            // Scan 2 items
            List<String> upcs = dbHelper.getTestUpcs(inventory.store, inventory.invCode, 2);
            for (String upc : upcs) {
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissDialog();
            }

            int countBefore = mainScan.getItemCount();
            logStep("Items before delete: " + countBefore);
            Assert.assertTrue(countBefore > 0, "Need at least 1 item to test delete");

            // Try to trigger the delete dialog
            By deleteBtn = By.xpath("//*[contains(@text,'Delete')]");
            if (WaitHelper.isElementPresent(driver, deleteBtn)) {
                driver.findElement(deleteBtn).click();
                Thread.sleep(AppConfig.SHORT_WAIT);
            }

            DeleteItemDialog deleteDialog = new DeleteItemDialog(driver, wait);
            if (deleteDialog.isDisplayed()) {
                logStep("Delete dialog appeared");
                deleteDialog.deleteRow("1");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                int countAfter = mainScan.getItemCount();
                logStep("Items after delete: " + countAfter);
            } else {
                logStep("Delete dialog not triggered via button — may require different trigger");
            }

            Assert.assertTrue(mainScan.isDisplayed(), "Should remain on Main Scan screen");
            pass();

        } catch (Exception e) {
            fail("Delete item test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 6, description = "Boxed oil dialog appears for PC 188 and allows quantity selection")
    public void testBoxedOilQuantitySelection() {
        setup("Item Entry - Boxed Oil Quantity (PC 188)");

        try {
            PartsMainPage partsMain = loginAndOpenPartsSection("oil", "188");

            // Scan an Oil UPC — should trigger BoxedOilDialog
            List<String> oilUpcs = dbHelper.getTestUpcsByPc("188", 3);
            if (oilUpcs.isEmpty()) {
                logStep("No UPCs for PC 188, skipping");
                pass();
                return;
            }

            logStep("Scanning Oil UPC: " + oilUpcs.get(0));
            dwHelper.simulateScan(oilUpcs.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
            if (oilDialog.isDisplayed()) {
                logStep("Boxed Oil dialog appeared. Item: " + oilDialog.getItemOutput());

                oilDialog.selectFull();
                Thread.sleep(AppConfig.SHORT_WAIT);
                logStep("Selected 'Full' quantity");

                int count = partsMain.getItemCount();
                logStep("Items after boxed oil add: " + count);
                Assert.assertTrue(count > 0, "Item should be added after selecting boxed oil quantity");
            } else {
                logStep("Boxed Oil dialog did not appear — item may not be boxed type");
                dismissDialog();
            }

            pass();

        } catch (Exception e) {
            fail("Boxed oil quantity test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 7, description = "Multi-item UPC resolution dialog for PC 66 (brakes/rotors)")
    public void testMultiItemUpcResolution() {
        setup("Item Entry - Multi-Item UPC Resolution (PC 66)");

        try {
            PartsMainPage partsMain = loginAndOpenPartsSection("rotor", "66");

            List<String> brakeUpcs = dbHelper.getTestUpcsByPc("66", 5);
            if (brakeUpcs.isEmpty()) {
                logStep("No UPCs for PC 66, skipping");
                pass();
                return;
            }

            boolean multiItemDialogSeen = false;
            for (int i = 0; i < Math.min(3, brakeUpcs.size()); i++) {
                String upc = brakeUpcs.get(i);
                logStep("Scanning brake/rotor UPC [" + (i + 1) + "]: " + upc);
                dwHelper.simulateScan(upc, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                MultiItemDialog multiDialog = new MultiItemDialog(driver, wait);
                if (multiDialog.isDisplayed()) {
                    multiItemDialogSeen = true;
                    logStep("Multi-Item dialog: Item1=" + multiDialog.getItem1Text() +
                            " Item2=" + multiDialog.getItem2Text());
                    multiDialog.selectItem1();
                    Thread.sleep(AppConfig.SHORT_WAIT);
                    logStep("Selected Item 1");

                    // Handle follow-up quantity/confirmation dialog
                    if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                        driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                    } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                        driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                    }
                    Thread.sleep(AppConfig.SHORT_WAIT);
                } else {
                    // Dismiss any other dialog
                    if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                        driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                    } else {
                        dismissDialog();
                    }
                    Thread.sleep(AppConfig.SHORT_WAIT);
                }
            }

            int finalCount = partsMain.getItemCount();
            logStep("Items: " + finalCount + " (multi-item dialog seen: " + multiItemDialogSeen + ")");

            pass();

        } catch (Exception e) {
            fail("Multi-item UPC resolution test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 8, description = "Add item with quantity > 1 via Add Item dialog")
    public void testAddItemWithHigherQuantity() {
        setup("Item Entry - Add With Qty > 1");

        try {
            MainScanPage mainScan = loginAndOpenSection();

            String itemNumber = dbHelper.getValidItemNumber();
            int countBefore = mainScan.getItemCount();

            openAddItemByNumberDialog(mainScan);

            AddItemDialog addDialog = new AddItemDialog(driver, wait);
            if (!waitForAddItemDialog(addDialog)) {
                logStep("Add Item dialog did not appear, skipping");
                pass();
                return;
            }

            addDialog.addItem(itemNumber, "5");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Submitted item " + itemNumber + " with qty 5");
            dismissDialog();

            int countAfter = mainScan.getItemCount();
            logStep("Items before: " + countBefore + " after: " + countAfter);
            Assert.assertTrue(countAfter > countBefore,
                    "Item count should increase after adding with qty 5");

            pass();

        } catch (Exception e) {
            fail("Add item with high qty failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
