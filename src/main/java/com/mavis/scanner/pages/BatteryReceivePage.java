package com.mavis.scanner.pages;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Page Object for BatteryReceiveActivity - scan incoming battery shipments.
 *
 * Layout: content_batteryReceive.xml
 * Activity: BatteryReceiveActivity (LaunchMode = SingleTop)
 * DataWedge Action: com.darryncampbell.datawedge.xamarin.ACTIONBATTRECIEVE
 */
public class BatteryReceivePage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final String PKG = "com.mavis.inventory_barcode_scanner:id/";

    private static final By ITEM_LIST = By.id(PKG + "txtOutput");
    private static final By BTN_ADD_ITEM = By.id(PKG + "btnEnterItemShowDialog");
    private static final By BTN_COMPLETE = By.id(PKG + "btnCompletedBattScan");

    // Dialog elements
    private static final By DIALOG_TITLE = By.id("android:id/alertTitle");
    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    // Battery count validation dialog (batteryCountValidation.xml)
    private static final By COUNT_VALIDATION_TITLE = By.id(PKG + "manualDialogTitle");
    private static final By COUNT_VALIDATION_INPUT = By.id(PKG + "editTextManualCount");

    public BatteryReceivePage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM)
                && WaitHelper.isElementPresent(driver, BTN_COMPLETE);
    }

    public boolean isAddItemButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM);
    }

    public boolean isCompleteButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_COMPLETE);
    }

    /**
     * Get count of items in the scanned list.
     */
    public int getItemCount() {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get text of a specific item in the list by index.
     */
    public String getItemText(int index) {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            if (index < items.size()) {
                return items.get(index).getText();
            }
        } catch (Exception e) {
            // fall through
        }
        return "";
    }

    /**
     * Tap an item in the list (triggers delete dialog).
     */
    public void tapItem(int index) {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            if (index < items.size()) {
                items.get(index).click();
            }
        } catch (Exception e) {
            // fall through
        }
    }

    /**
     * Tap "Add Item" to open the manual add item dialog.
     */
    public void tapAddItem() {
        WaitHelper.waitAndClick(wait, BTN_ADD_ITEM);
    }

    /**
     * Tap "Complete battery scan" to trigger the completion flow.
     */
    public void tapComplete() {
        WaitHelper.waitAndClick(wait, BTN_COMPLETE);
    }

    // ==================== COMPLETION FLOW DIALOGS ====================

    /**
     * In the "Completing battery scan" dialog, tap "Complete" (button3/neutral).
     */
    public void tapDialogComplete() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * In the "Completing battery scan" dialog, tap "Delete" (button2/negative).
     */
    public void tapDialogDelete() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    /**
     * In the "Completing battery scan" dialog, tap "Exit" (button1/positive).
     */
    public void tapDialogExit() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_POSITIVE);
    }

    /**
     * Check if the count validation dialog is showing.
     */
    public boolean isCountValidationDisplayed() {
        return WaitHelper.isElementPresent(driver, COUNT_VALIDATION_INPUT);
    }

    /**
     * Enter the manual count in the validation dialog and submit.
     */
    public void enterCountAndSubmit(String count) {
        WaitHelper.waitAndType(wait, COUNT_VALIDATION_INPUT, count);
        // Submit is neutral button (button3)
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * In the scan summary/verify dialog, tap "Correct, complete" (button2/negative).
     */
    public void tapVerifyComplete() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    /**
     * In the delete confirmation dialog, tap "Yes" (button3/neutral).
     */
    public void tapDeleteConfirmYes() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * In a delete item dialog, tap "Delete" (button2/negative).
     */
    public void tapDeleteItem() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    /**
     * Get the dialog title text.
     */
    public String getDialogTitle() {
        return WaitHelper.getTextSafe(driver, DIALOG_TITLE);
    }

    /**
     * Check if any dialog is currently displayed.
     */
    public boolean isDialogDisplayed() {
        return WaitHelper.isElementPresent(driver, DIALOG_TITLE)
                || WaitHelper.isElementPresent(driver, COUNT_VALIDATION_TITLE);
    }
}
