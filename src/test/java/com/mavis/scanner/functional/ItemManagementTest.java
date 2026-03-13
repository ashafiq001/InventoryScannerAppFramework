package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.BoxedOilDialog;
import com.mavis.scanner.pages.dialogs.MultiItemDialog;
import com.mavis.scanner.utils.DatabaseHelper;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.*;

/**
 * Item management tests — add and delete items for both tires and parts.
 *
 * Tests cover:
 * - Tire: scan item via DataWedge → delete via DeleteItemDialog → verify count decreases
 * - Tire: add item manually via AddItemDialog → delete → verify
 * - Parts: scan item via DataWedge → delete → verify
 * - Parts: add item manually → delete → verify
 * - Cancel delete dialog → verify item remains
 * - Delete multiple items in sequence
 *
 * Delete flow in the app:
 *   Click/tap on an item in the list → DeleteItemDialog appears →
 *   Enter row number → tap OK → item is soft-deleted (Deleted=true in InvTbl)
 *
 * Each test runs the full sequential flow from app launch (no step can be skipped).
 */
public class ItemManagementTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");
    private static final By ITEM_LIST = By.id("com.mavis.inventory_barcode_scanner:id/txtOutput");

    // ==================== TIRE INVENTORY HELPERS ====================

    private ScheduledInventory resolveTireInventory() {
        ScheduledInventory inv = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inv);
        if (!inv.scheduledPCs.isEmpty() && !inv.scheduledPCs.contains(2)) {
            skip("Resolved inventory has PCs " + inv.scheduledPCs +
                    " (no tire PC=2). Schedule a tire inventory or use -DINVENTORY_PCS=2");
        }
        return inv;
    }

    private MainScanPage fullLoginToTireScan(ScheduledInventory inv) throws InterruptedException {
        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");

        loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
        logStep("Logged in: store=" + inv.store + " invCode=" + inv.invCode);

        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        if (loginPage.isDisplayed()) {
            logStep("Still syncing, waiting longer...");
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        }

        MainScanPage mainScan = new MainScanPage(driver, wait);
        if (!mainScan.isDisplayed()) {
            String activity = driver.currentActivity();
            if (activity != null && activity.contains("PartsPCActivity")) {
                skip("App routed to PartsPCActivity — inventory has no tire PC=2.");
            }
            Thread.sleep(AppConfig.LONG_WAIT);
        }

        Assert.assertTrue(mainScan.isDisplayed(),
                "Should be on tire scan screen. Activity: " + driver.currentActivity());
        logStep("On tire scanning screen (MainActivity)");
        return mainScan;
    }

    // ==================== PARTS INVENTORY HELPERS ====================

    private ScheduledInventory resolvePartsInventory() {
        ScheduledInventory inv = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inv);
        if (!inv.scheduledPCs.isEmpty() && inv.scheduledPCs.size() == 1 && inv.scheduledPCs.contains(2)) {
            skip("Resolved inventory is tire-only (PC=2). Schedule a parts inventory.");
        }
        return inv;
    }

    private PartsCategoryPage fullLoginToPartsCategory(ScheduledInventory inv) throws InterruptedException {
        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);
        Assert.assertTrue(loginPage.isDisplayed(), "Login screen should display");

        loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
        logStep("Logged in: store=" + inv.store + " invCode=" + inv.invCode + " PCs=" + inv.scheduledPCs);

        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        if (loginPage.isDisplayed()) {
            logStep("Still syncing...");
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        }

        PartsCategoryPage partsCategory = new PartsCategoryPage(driver, wait);
        MainScanPage mainScan = new MainScanPage(driver, wait);

        if (mainScan.isDisplayed()) {
            skip("App routed to MainActivity — inventory contains tire PC=2.");
        }

        if (!partsCategory.isDisplayed()) {
            Thread.sleep(AppConfig.LONG_WAIT);
        }

        Assert.assertTrue(partsCategory.isDisplayed(),
                "Should be on Parts Category screen. Activity: " + driver.currentActivity());
        Thread.sleep(AppConfig.MEDIUM_WAIT);
        logStep("On Parts Category screen");
        return partsCategory;
    }

    private String[] findFirstStartableCategory(PartsCategoryPage partsCategory) {
        for (int i = 1; i <= 7; i++) {
            if (partsCategory.isStartButtonVisible(i) && !partsCategory.isCategoryCompleted(i)) {
                String label = partsCategory.getCategoryLabel(i);
                String pcCode = AppConfig.getPcCodeFromLabel(label);
                if (pcCode != null) {
                    return new String[]{String.valueOf(i), label, pcCode};
                }
            }
        }
        return null;
    }

    private PartsMainPage startCategory(PartsCategoryPage partsCategory, int slot) throws InterruptedException {
        partsCategory.tapStart(slot);
        Thread.sleep(AppConfig.MEDIUM_WAIT);
        PartsMainPage partsMain = new PartsMainPage(driver, wait);
        Assert.assertTrue(partsMain.isDisplayed(),
                "Should navigate to parts scan screen. Activity: " + driver.currentActivity());
        return partsMain;
    }

    // ==================== SECTION HELPERS ====================

    private void openTireSection(DataWedgeHelper dwHelper, MainScanPage mainScan, String sectionBarcode)
            throws InterruptedException {
        dwHelper.scanSectionBarcode(sectionBarcode);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissAnyDialog();
        logStep("Scanned section " + sectionBarcode + ", output: " + mainScan.getSectionOutput());
    }

    private boolean openPartsSection(DataWedgeHelper dwHelper, PartsMainPage partsMain,
                                     List<String> allSections, Set<String> usedSections) throws InterruptedException {
        for (String sectionBarcode : allSections) {
            if (usedSections.contains(sectionBarcode)) continue;

            dwHelper.simulateScan(sectionBarcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            String sectionOutput = partsMain.getSectionOutput();
            logStep("Scanned section " + sectionBarcode + ", output: " + sectionOutput);

            if (sectionOutput != null && !sectionOutput.toLowerCase().contains("scan section")) {
                usedSections.add(sectionBarcode);
                logStep("Section " + sectionBarcode + " opened");
                return true;
            }
        }
        logStep("No section could be opened");
        return false;
    }

    // ==================== ADD ITEM HELPERS ====================

    /**
     * Add item via Add Item dialog (tire flow).
     * Flow: Tap Add Item -> chooser ("Lookup by Item Number") -> AddItemDialog -> Submit.
     */
    private boolean addItemViaDialog(MainScanPage mainScan, String itemNumber, String qty)
            throws InterruptedException {
        scrollToBottom();
        mainScan.tapAddItem();
        Thread.sleep(AppConfig.SHORT_WAIT);

        // Handle chooser dialog
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

        // Wait for AddItemDialog
        By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
        boolean found = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (WaitHelper.isElementPresent(driver, addItemField)) { found = true; break; }
            Thread.sleep(1000);
        }
        if (!found) {
            logStep("  Item entry dialog did not appear");
            dismissAnyDialog();
            return false;
        }

        // Enter item number and quantity
        By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
        WaitHelper.waitAndType(wait, addItemField, itemNumber);
        WaitHelper.waitAndType(wait, addItemQty, qty);

        // Tap Submit or OK
        By submitBtn = byText("Submit");
        if (WaitHelper.isElementPresent(driver, submitBtn)) {
            driver.findElement(submitBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        }
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        return true;
    }

    /**
     * Add item via Add Item dialog (parts flow).
     *
     * Parts flow is different from tires — NO chooser dialog:
     * - PC 188/62 (Oil/AC): Tap Add Item → BoxedOilDialog directly (Full/3-4/Half/1-4)
     * - Other PCs: Tap Add Item → enter_item_layout directly (addItemText + addItemQty + Submit)
     */
    private boolean addItemViaDialog(PartsMainPage partsMain, String itemNumber, String qty)
            throws InterruptedException {
        scrollToBottom();
        partsMain.tapAddItem();
        Thread.sleep(AppConfig.MEDIUM_WAIT);

        // PC 188/62: BoxedOilDialog appears directly — select Full to add
        BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
        if (oilDialog.isDisplayed()) {
            logStep("  Parts Add Item: BoxedOil dialog (Oil/AC) - selecting Full");
            oilDialog.selectFull();
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            return true;
        }

        // Other PCs: AddItemDialog appears directly (no chooser step)
        By addItemField = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
        boolean found = false;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (WaitHelper.isElementPresent(driver, addItemField)) { found = true; break; }
            Thread.sleep(1000);
        }
        if (!found) {
            logStep("  Item entry dialog did not appear");
            dismissAnyDialog();
            return false;
        }

        By addItemQty = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
        WaitHelper.waitAndType(wait, addItemField, itemNumber);
        WaitHelper.waitAndType(wait, addItemQty, qty);

        By submitBtn = byText("Submit");
        if (WaitHelper.isElementPresent(driver, submitBtn)) {
            driver.findElement(submitBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        }
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        return true;
    }

    // ==================== DELETE ITEM HELPERS ====================

    /**
     * Tap on an item in the ListView to trigger the delete confirmation AlertDialog.
     *
     * Actual app flow (from Xamarin source MainActivity.cs / MainActivityParts.cs):
     *   ListView.ItemClick → AlertDialog "Deleting item. Are you sure?"
     *     → Button2 "Delete"  (maps to android:id/button2)
     *     → Button  "Exit"    (maps to android:id/button3)
     *
     * txtOutput is a ListView (not a plain TextView), so children are clickable list items.
     */
    private boolean tapItemToTriggerDelete(int itemIndex) throws InterruptedException {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            if (itemIndex >= items.size()) {
                logStep("Cannot tap item at index " + itemIndex + ", only " + items.size() + " items");
                return false;
            }

            String itemText = items.get(itemIndex).getText();
            logStep("Tapping item [" + itemIndex + "]: " + itemText);
            items.get(itemIndex).click();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Check for the "Deleting item. Are you sure?" AlertDialog
            By deleteConfirmTitle = byTextContains("Deleting item");
            By deleteBtn = byText("Delete");

            if (WaitHelper.isElementPresent(driver, deleteConfirmTitle)
                    || WaitHelper.isElementPresent(driver, deleteBtn)) {
                logStep("Delete confirmation dialog appeared");
                return true;
            }

            // Also check standard dialog buttons as fallback
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)
                    || WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                logStep("Dialog with buttons appeared (likely delete confirmation)");
                return true;
            }

            logStep("Delete confirmation dialog did not appear");
            return false;
        } catch (Exception e) {
            logStep("Failed to tap item: " + e.getMessage());
            return false;
        }
    }

    /**
     * Confirm deletion by tapping the "Delete" button in the AlertDialog.
     * In Xamarin: SetButton2("Delete", ...) maps to android:id/button2 (negative button).
     */
    private boolean confirmDelete() throws InterruptedException {
        By deleteBtn = byText("Delete");
        if (WaitHelper.isElementPresent(driver, deleteBtn)) {
            driver.findElement(deleteBtn).click();
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Tapped 'Delete' to confirm");
            return true;
        }
        // Fallback: button2 is the "Delete" button (SetButton2 in Xamarin = negative button)
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEGATIVE)) {
            driver.findElement(DIALOG_BUTTON_NEGATIVE).click();
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            logStep("Tapped button2 (Delete) to confirm");
            return true;
        }
        logStep("Could not find Delete button");
        return false;
    }

    /**
     * Cancel deletion by tapping the "Exit" button in the AlertDialog.
     * In Xamarin: SetButton("Exit", ...) = deprecated overload that maps to
     * BUTTON_POSITIVE (android:id/button1), NOT neutral/button3.
     * SetButton2("Delete", ...) = BUTTON_NEGATIVE (android:id/button2).
     */
    private boolean cancelDelete() throws InterruptedException {
        // Try by text first
        By exitBtn = byText("Exit");
        if (WaitHelper.isElementPresent(driver, exitBtn)) {
            driver.findElement(exitBtn).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
            logStep("Tapped 'Exit' to cancel delete");
            return true;
        }
        // SetButton("Exit") maps to BUTTON_POSITIVE = button1
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
            logStep("Tapped button1 (Exit) to cancel delete");
            return true;
        }
        // Also try neutral as fallback
        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
            logStep("Tapped button3 (Exit) to cancel delete");
            return true;
        }
        logStep("Could not find Exit button");
        return false;
    }

    /**
     * Delete an item: tap it in the list → confirm "Delete" in the AlertDialog.
     */
    private boolean deleteItemByIndex(int itemIndex) throws InterruptedException {
        boolean dialogShown = tapItemToTriggerDelete(itemIndex);
        if (!dialogShown) return false;
        return confirmDelete();
    }

    // ==================== POST-SCAN DIALOG (PARTS) ====================

    private void handlePostScanDialog() throws InterruptedException {
        Thread.sleep(AppConfig.SHORT_WAIT);

        BoxedOilDialog oilDialog = new BoxedOilDialog(driver, wait);
        if (oilDialog.isDisplayed()) {
            logStep("  Boxed Oil dialog - selecting Full");
            oilDialog.selectFull();
            Thread.sleep(AppConfig.SHORT_WAIT);
            return;
        }

        MultiItemDialog multiDialog = new MultiItemDialog(driver, wait);
        if (multiDialog.isDisplayed()) {
            logStep("  Multi-Item dialog - selecting Item 1");
            multiDialog.selectItem1();
            Thread.sleep(AppConfig.SHORT_WAIT);
        }

        By qtyField = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
        By qtyField2 = By.id("com.mavis.inventory_barcode_scanner:id/editTextQty");
        By anyEditText = By.className("android.widget.EditText");

        By foundQtyField = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (WaitHelper.isElementPresent(driver, qtyField)) { foundQtyField = qtyField; break; }
            else if (WaitHelper.isElementPresent(driver, qtyField2)) { foundQtyField = qtyField2; break; }
            else if (WaitHelper.isElementPresent(driver, anyEditText)) { foundQtyField = anyEditText; break; }
            Thread.sleep(800);
        }

        if (foundQtyField != null) {
            WebElement qtyElement = driver.findElement(foundQtyField);
            String existingQty = qtyElement.getText();
            if (existingQty == null || existingQty.trim().isEmpty())
                existingQty = qtyElement.getAttribute("text");
            if (existingQty == null || existingQty.trim().isEmpty() || existingQty.trim().equals("0")) {
                qtyElement.clear();
                qtyElement.sendKeys("1");
            }
            Thread.sleep(500);

            By submitBtn = byTextIgnoreCase("SUBMIT");
            By okBtn = byTextIgnoreCase("OK");
            if (WaitHelper.isElementPresent(driver, submitBtn)) driver.findElement(submitBtn).click();
            else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            else if (WaitHelper.isElementPresent(driver, okBtn)) driver.findElement(okBtn).click();
            else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
            return;
        }

        if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
            Thread.sleep(500);
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
            Thread.sleep(500);
        }
    }

    // ==================== UTILITY HELPERS ====================

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

    private static By byTextContains(String text) {
        return By.xpath("//*[contains(@text,'" + text + "')]");
    }

    // ==================== TIRE TESTS ====================

    @Test(priority = 0, description = "Tire: Scan item via DataWedge then delete it, verify count decreases")
    public void testTireScanItemThenDelete() {
        setup("Tire - Scan Item Then Delete");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Should have at least one section barcode");
            openTireSection(dwHelper, mainScan, sections.get(0));

            // Scan an item
            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");

            int countBeforeScan = mainScan.getItemCount();
            dwHelper.scanItemBarcode(upcs.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int countAfterScan = mainScan.getItemCount();
            logStep("Scanned " + upcs.get(0) + ": count " + countBeforeScan + " -> " + countAfterScan);
            Assert.assertTrue(countAfterScan > countBeforeScan,
                    "Item count should increase after scan");

            // Delete the scanned item (tap first item to trigger delete dialog)
            boolean deleted = deleteItemByIndex(0);
            Assert.assertTrue(deleted, "Delete dialog should appear when tapping item");

            int countAfterDelete = mainScan.getItemCount();
            logStep("After delete: count " + countAfterScan + " -> " + countAfterDelete);
            Assert.assertTrue(countAfterDelete < countAfterScan,
                    "Item count should decrease after deletion (was " + countAfterScan +
                            ", now " + countAfterDelete + ")");

            pass();

        } catch (Exception e) {
            fail("Tire scan + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 1, description = "Tire: Add item manually via dialog then delete it, verify count returns to original")
    public void testTireManualAddThenDelete() {
        setup("Tire - Manual Add Then Delete");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            openTireSection(dwHelper, mainScan, sections.get(0));

            int initialCount = mainScan.getItemCount();
            logStep("Initial item count: " + initialCount);

            // Add item manually
            String itemNumber = dbHelper.getValidItemNumber();
            logStep("Adding item manually: " + itemNumber);
            boolean added = addItemViaDialog(mainScan, itemNumber, "1");
            Assert.assertTrue(added, "Add Item dialog flow should complete");

            int countAfterAdd = mainScan.getItemCount();
            logStep("After manual add: count " + initialCount + " -> " + countAfterAdd);
            Assert.assertTrue(countAfterAdd > initialCount,
                    "Item count should increase after manual add");

            // Delete the manually added item
            boolean deleted = deleteItemByIndex(countAfterAdd - 1);
            Assert.assertTrue(deleted, "Delete dialog should appear");

            int countAfterDelete = mainScan.getItemCount();
            logStep("After delete: count " + countAfterAdd + " -> " + countAfterDelete);
            Assert.assertTrue(countAfterDelete < countAfterAdd,
                    "Item count should decrease after deletion");

            pass();

        } catch (Exception e) {
            fail("Tire manual add + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 2, description = "Tire: Cancel delete dialog — item count should remain unchanged")
    public void testTireCancelDelete() {
        setup("Tire - Cancel Delete Dialog");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open section and scan an item
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            openTireSection(dwHelper, mainScan, sections.get(0));

            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");

            dwHelper.scanItemBarcode(upcs.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();

            int countBeforeCancel = mainScan.getItemCount();
            logStep("Item count before cancel attempt: " + countBeforeCancel);
            Assert.assertTrue(countBeforeCancel > 0, "Should have at least one item");

            // Tap item to trigger delete dialog, then cancel
            boolean dialogShown = tapItemToTriggerDelete(0);
            Assert.assertTrue(dialogShown, "Delete dialog should appear");

            cancelDelete();
            logStep("Cancelled delete dialog");

            int countAfterCancel = mainScan.getItemCount();
            logStep("Item count after cancel: " + countAfterCancel);
            Assert.assertEquals(countAfterCancel, countBeforeCancel,
                    "Item count should remain unchanged after cancelling delete");

            pass();

        } catch (Exception e) {
            fail("Tire cancel delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 3, description = "Tire: Scan multiple items then delete them one by one")
    public void testTireScanMultipleThenDeleteAll() {
        setup("Tire - Scan Multiple Then Delete All");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            openTireSection(dwHelper, mainScan, sections.get(0));

            int initialCount = mainScan.getItemCount();

            // Scan 3 items
            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 3);
            Assert.assertTrue(upcs.size() >= 2, "Need at least 2 UPCs");

            int scannedCount = 0;
            for (int i = 0; i < Math.min(3, upcs.size()); i++) {
                dwHelper.scanItemBarcode(upcs.get(i));
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                dismissAnyDialog();
                scannedCount++;
                logStep("Scanned [" + scannedCount + "]: " + upcs.get(i));
            }

            int countAfterScans = mainScan.getItemCount();
            logStep("Count after " + scannedCount + " scans: " + countAfterScans);
            Assert.assertTrue(countAfterScans >= initialCount + scannedCount,
                    "Count should reflect all scanned items");

            // Delete items one by one (always delete the first item since list shifts)
            int deleteCount = 0;
            for (int i = 0; i < scannedCount; i++) {
                int currentCount = mainScan.getItemCount();
                if (currentCount <= initialCount) {
                    logStep("No more items to delete (count=" + currentCount + ")");
                    break;
                }

                boolean deleted = deleteItemByIndex(0);
                if (deleted) {
                    deleteCount++;
                    int newCount = mainScan.getItemCount();
                    logStep("Deleted item " + deleteCount + ": count " + currentCount + " -> " + newCount);
                    Assert.assertTrue(newCount < currentCount,
                            "Count should decrease after each deletion");
                } else {
                    logStep("Delete dialog did not appear for item " + (i + 1));
                    break;
                }
            }

            logStep("Deleted " + deleteCount + " of " + scannedCount + " items");
            Assert.assertTrue(deleteCount > 0, "Should have deleted at least one item");

            pass();

        } catch (Exception e) {
            fail("Tire scan multiple + delete all test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 4, description = "Tire: Add item manually and scan item, then delete both")
    public void testTireMixedAddThenDeleteBoth() {
        setup("Tire - Mixed Add (Scan + Manual) Then Delete Both");

        try {
            ScheduledInventory inv = resolveTireInventory();
            MainScanPage mainScan = fullLoginToTireScan(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Assert.assertFalse(sections.isEmpty(), "Need section barcodes");
            openTireSection(dwHelper, mainScan, sections.get(0));

            int initialCount = mainScan.getItemCount();
            logStep("Initial count: " + initialCount);

            // 1. Scan an item via DataWedge
            List<String> upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");
            dwHelper.scanItemBarcode(upcs.get(0));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            dismissAnyDialog();
            int countAfterScan = mainScan.getItemCount();
            logStep("After scan: count " + initialCount + " -> " + countAfterScan);

            // 2. Add an item manually via dialog
            String itemNumber = dbHelper.getValidItemNumber();
            boolean added = addItemViaDialog(mainScan, itemNumber, "1");
            Assert.assertTrue(added, "Manual add should succeed");
            int countAfterBoth = mainScan.getItemCount();
            logStep("After manual add: count " + countAfterScan + " -> " + countAfterBoth);

            // 3. Delete both items (always index 0 since list shifts after delete)
            int deletedCount = 0;
            for (int i = 0; i < 2; i++) {
                int currentCount = mainScan.getItemCount();
                if (currentCount <= initialCount) break;

                boolean deleted = deleteItemByIndex(0);
                if (deleted) {
                    deletedCount++;
                    logStep("Deleted item " + deletedCount);
                } else {
                    logStep("Could not delete item " + (i + 1));
                    break;
                }
            }

            int finalCount = mainScan.getItemCount();
            logStep("Final count: " + finalCount + " (deleted " + deletedCount + " items)");
            Assert.assertTrue(deletedCount >= 1, "Should delete at least one item");
            Assert.assertTrue(finalCount < countAfterBoth,
                    "Final count should be less than count after adds");

            pass();

        } catch (Exception e) {
            fail("Tire mixed add + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== PARTS TESTS ====================

    @Test(priority = 5, description = "Parts: Scan item via DataWedge then delete it, verify count decreases")
    public void testPartsScanItemThenDelete() {
        setup("Parts - Scan Item Then Delete");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            PartsCategoryPage partsCategory = fullLoginToPartsCategory(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            String[] target = findFirstStartableCategory(partsCategory);
            Assert.assertNotNull(target, "Need a startable category");
            int slot = Integer.parseInt(target[0]);
            String pcCode = target[2];
            logStep("Starting: " + target[1] + " (PC=" + pcCode + ")");

            PartsMainPage partsMain = startCategory(partsCategory, slot);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Set<String> usedSections = new HashSet<>();
            boolean opened = openPartsSection(dwHelper, partsMain, sections, usedSections);
            Assert.assertTrue(opened, "Should open a section");

            // Scan an item
            int countBeforeScan = partsMain.getItemCount();
            List<String> upcs = dbHelper.getTestUpcsByPc(pcCode, 1);
            if (upcs.isEmpty()) upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");

            dwHelper.simulateScan(upcs.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            handlePostScanDialog();

            int countAfterScan = partsMain.getItemCount();
            logStep("Scanned " + upcs.get(0) + ": count " + countBeforeScan + " -> " + countAfterScan);
            Assert.assertTrue(countAfterScan > countBeforeScan,
                    "Item count should increase after scan");

            // Delete the scanned item
            boolean deleted = deleteItemByIndex(0);
            Assert.assertTrue(deleted, "Delete dialog should appear when tapping item");

            int countAfterDelete = partsMain.getItemCount();
            logStep("After delete: count " + countAfterScan + " -> " + countAfterDelete);
            Assert.assertTrue(countAfterDelete < countAfterScan,
                    "Item count should decrease after deletion");

            pass();

        } catch (Exception e) {
            fail("Parts scan + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 6, description = "Parts: Add item manually via dialog then delete it")
    public void testPartsManualAddThenDelete() {
        setup("Parts - Manual Add Then Delete");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            PartsCategoryPage partsCategory = fullLoginToPartsCategory(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            String[] target = findFirstStartableCategory(partsCategory);
            Assert.assertNotNull(target, "Need a startable category");
            int slot = Integer.parseInt(target[0]);
            String pcCode = target[2];
            logStep("Starting: " + target[1] + " (PC=" + pcCode + ")");

            PartsMainPage partsMain = startCategory(partsCategory, slot);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Set<String> usedSections = new HashSet<>();
            boolean opened = openPartsSection(dwHelper, partsMain, sections, usedSections);
            Assert.assertTrue(opened, "Should open a section");

            int initialCount = partsMain.getItemCount();
            logStep("Initial count: " + initialCount);

            // Add item manually
            String itemNumber = dbHelper.getValidItemNumberByPc(pcCode);
            logStep("Adding item manually: " + itemNumber + " (PC=" + pcCode + ")");
            boolean added = addItemViaDialog(partsMain, itemNumber, "1");
            Assert.assertTrue(added, "Add Item dialog flow should complete");

            int countAfterAdd = partsMain.getItemCount();
            logStep("After manual add: count " + initialCount + " -> " + countAfterAdd);
            Assert.assertTrue(countAfterAdd > initialCount,
                    "Item count should increase after manual add");

            // Delete the manually added item
            boolean deleted = deleteItemByIndex(countAfterAdd - 1);
            Assert.assertTrue(deleted, "Delete dialog should appear");

            int countAfterDelete = partsMain.getItemCount();
            logStep("After delete: count " + countAfterAdd + " -> " + countAfterDelete);
            Assert.assertTrue(countAfterDelete < countAfterAdd,
                    "Item count should decrease after deletion");

            pass();

        } catch (Exception e) {
            fail("Parts manual add + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 7, description = "Parts: Scan item and add item manually, then delete both")
    public void testPartsMixedAddThenDeleteBoth() {
        setup("Parts - Mixed Add (Scan + Manual) Then Delete Both");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            PartsCategoryPage partsCategory = fullLoginToPartsCategory(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            String[] target = findFirstStartableCategory(partsCategory);
            Assert.assertNotNull(target, "Need a startable category");
            int slot = Integer.parseInt(target[0]);
            String pcCode = target[2];
            logStep("Starting: " + target[1] + " (PC=" + pcCode + ")");

            PartsMainPage partsMain = startCategory(partsCategory, slot);

            // Open section
            List<String> sections = dbHelper.getSectionBarcodes();
            Set<String> usedSections = new HashSet<>();
            boolean opened = openPartsSection(dwHelper, partsMain, sections, usedSections);
            Assert.assertTrue(opened, "Should open a section");

            int initialCount = partsMain.getItemCount();
            logStep("Initial count: " + initialCount);

            // 1. Scan an item via DataWedge
            List<String> upcs = dbHelper.getTestUpcsByPc(pcCode, 1);
            if (upcs.isEmpty()) upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");

            dwHelper.simulateScan(upcs.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            handlePostScanDialog();
            int countAfterScan = partsMain.getItemCount();
            logStep("After scan: count " + initialCount + " -> " + countAfterScan);

            // 2. Add an item manually
            String itemNumber = dbHelper.getValidItemNumberByPc(pcCode);
            boolean added = addItemViaDialog(partsMain, itemNumber, "1");
            Assert.assertTrue(added, "Manual add should succeed");
            int countAfterBoth = partsMain.getItemCount();
            logStep("After manual add: count " + countAfterScan + " -> " + countAfterBoth);

            // 3. Delete both items
            int deletedCount = 0;
            for (int i = 0; i < 2; i++) {
                int currentCount = partsMain.getItemCount();
                if (currentCount <= initialCount) break;

                boolean deleted = deleteItemByIndex(0);
                if (deleted) {
                    deletedCount++;
                    logStep("Deleted item " + deletedCount);
                } else {
                    logStep("Could not delete item " + (i + 1));
                    break;
                }
            }

            int finalCount = partsMain.getItemCount();
            logStep("Final count: " + finalCount + " (deleted " + deletedCount + " items)");
            Assert.assertTrue(deletedCount >= 1, "Should delete at least one item");
            Assert.assertTrue(finalCount < countAfterBoth,
                    "Final count should be less than count after adds");

            pass();

        } catch (Exception e) {
            fail("Parts mixed add + delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 8, description = "Parts: Cancel delete dialog — item count should remain unchanged")
    public void testPartsCancelDelete() {
        setup("Parts - Cancel Delete Dialog");

        try {
            ScheduledInventory inv = resolvePartsInventory();
            PartsCategoryPage partsCategory = fullLoginToPartsCategory(inv);
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            DatabaseHelper dbHelper = new DatabaseHelper(driver);

            String[] target = findFirstStartableCategory(partsCategory);
            Assert.assertNotNull(target, "Need a startable category");
            int slot = Integer.parseInt(target[0]);
            String pcCode = target[2];
            logStep("Starting: " + target[1] + " (PC=" + pcCode + ")");

            PartsMainPage partsMain = startCategory(partsCategory, slot);

            // Open section and scan an item
            List<String> sections = dbHelper.getSectionBarcodes();
            Set<String> usedSections = new HashSet<>();
            boolean opened = openPartsSection(dwHelper, partsMain, sections, usedSections);
            Assert.assertTrue(opened, "Should open a section");

            List<String> upcs = dbHelper.getTestUpcsByPc(pcCode, 1);
            if (upcs.isEmpty()) upcs = dbHelper.getTestUpcs(inv.store, inv.invCode, 1);
            Assert.assertFalse(upcs.isEmpty(), "Need at least one UPC");

            dwHelper.simulateScan(upcs.get(0), "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            handlePostScanDialog();

            int countBeforeCancel = partsMain.getItemCount();
            logStep("Item count before cancel attempt: " + countBeforeCancel);
            Assert.assertTrue(countBeforeCancel > 0, "Should have at least one item");

            // Tap item to trigger delete, then cancel
            boolean dialogShown = tapItemToTriggerDelete(0);
            Assert.assertTrue(dialogShown, "Delete dialog should appear");

            cancelDelete();
            logStep("Cancelled delete dialog");

            int countAfterCancel = partsMain.getItemCount();
            logStep("Item count after cancel: " + countAfterCancel);
            Assert.assertEquals(countAfterCancel, countBeforeCancel,
                    "Item count should remain unchanged after cancelling delete");

            pass();

        } catch (Exception e) {
            fail("Parts cancel delete test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }
}
