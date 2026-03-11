package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog for adding an item by item number and quantity.
 * Layout: enter_item_layout.xml
 */
public class AddItemDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By DIALOG_TITLE = By.id("com.mavis.inventory_barcode_scanner:id/enterItemDialogTitle");
    private static final By ITEM_NUMBER = By.id("com.mavis.inventory_barcode_scanner:id/addItemText");
    private static final By ITEM_QTY = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
    // Dialog buttons are typically AlertDialog positiveButton / negativeButton
    private static final By BTN_OK = By.id("android:id/button1");
    private static final By BTN_CANCEL = By.id("android:id/button2");

    public AddItemDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, ITEM_NUMBER);
    }

    public AddItemDialog enterItemNumber(String itemNumber) {
        WaitHelper.waitAndType(wait, ITEM_NUMBER, itemNumber);
        return this;
    }

    public AddItemDialog enterQuantity(String qty) {
        WaitHelper.waitAndType(wait, ITEM_QTY, qty);
        return this;
    }

    public void tapOk() {
        WaitHelper.waitAndClick(wait, BTN_OK);
    }

    public void tapCancel() {
        WaitHelper.waitAndClick(wait, BTN_CANCEL);
    }

    /**
     * Add an item with number and quantity, then confirm.
     */
    public void addItem(String itemNumber, String quantity) {
        enterItemNumber(itemNumber);
        enterQuantity(quantity);
        tapOk();
    }
}
