package com.mavis.scanner.pages;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Page Object for BatteryReturnActivity - scan batteries being returned to vendor.
 *
 * Layout: content_batteryReturns.xml
 * Activity: BatteryReturnActivity (LaunchMode = SingleTop)
 * DataWedge Action: com.darryncampbell.datawedge.xamarin.ACTIONBATTRETURNS
 *
 * Return types: "rotates", "warranty", "cores"
 */
public class BatteryReturnPage {

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

    // Battery count validation dialog
    private static final By COUNT_VALIDATION_INPUT = By.id(PKG + "editTextManualCount");

    public BatteryReturnPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM)
                && WaitHelper.isElementPresent(driver, BTN_COMPLETE);
    }

    public int getItemCount() {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

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

    public void tapAddItem() {
        WaitHelper.waitAndClick(wait, BTN_ADD_ITEM);
    }

    public void tapComplete() {
        WaitHelper.waitAndClick(wait, BTN_COMPLETE);
    }

    // ==================== COMPLETION FLOW DIALOGS ====================

    /**
     * "Completing battery scan" dialog -> "Complete" (button3/neutral).
     */
    public void tapDialogComplete() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * "Completing battery scan" dialog -> "Delete" (button2/negative).
     */
    public void tapDialogDelete() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    /**
     * "Completing battery scan" dialog -> "Exit" (button1/positive).
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
     * Enter count and tap Submit in the validation dialog.
     */
    public void enterCountAndSubmit(String count) {
        WaitHelper.waitAndType(wait, COUNT_VALIDATION_INPUT, count);
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * In the delete all confirmation dialog, tap "Yes" (button3/neutral).
     */
    public void tapDeleteConfirmYes() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEUTRAL);
    }

    /**
     * In a single item delete dialog, tap "Delete" (button2/negative).
     */
    public void tapDeleteItem() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    // ==================== RETURN TYPE DIALOG ====================

    /**
     * Check if the "Return type missing" dialog is showing.
     */
    public boolean isReturnTypeDialogDisplayed() {
        String title = WaitHelper.getTextSafe(driver, DIALOG_TITLE);
        return title != null && title.contains("Return type missing");
    }

    /**
     * In the return type dialog, select "Rotates" (button2/negative).
     */
    public void selectRotates() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_NEGATIVE);
    }

    /**
     * In the return type dialog, select "Warranty" (button1/positive).
     */
    public void selectWarranty() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_POSITIVE);
    }

    public String getDialogTitle() {
        return WaitHelper.getTextSafe(driver, DIALOG_TITLE);
    }

    public boolean isDialogDisplayed() {
        return WaitHelper.isElementPresent(driver, DIALOG_TITLE)
                || WaitHelper.isElementPresent(driver, COUNT_VALIDATION_INPUT);
    }
}
