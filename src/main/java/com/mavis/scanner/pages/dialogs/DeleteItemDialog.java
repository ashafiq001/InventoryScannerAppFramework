package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog for deleting a scanned item by row number.
 * Layout: delete_item_layout.xml
 */
public class DeleteItemDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By DIALOG_TITLE = By.id("com.mavis.inventory_barcode_scanner:id/deleteItemDialogTitle");
    private static final By ROW_NUMBER = By.id("com.mavis.inventory_barcode_scanner:id/deleteItemEditText");
    private static final By BTN_OK = By.id("android:id/button1");
    private static final By BTN_CANCEL = By.id("android:id/button2");

    public DeleteItemDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, ROW_NUMBER);
    }

    public DeleteItemDialog enterRowNumber(String rowNum) {
        WaitHelper.waitAndType(wait, ROW_NUMBER, rowNum);
        return this;
    }

    public void tapOk() {
        WaitHelper.waitAndClick(wait, BTN_OK);
    }

    public void tapCancel() {
        WaitHelper.waitAndClick(wait, BTN_CANCEL);
    }

    /**
     * Delete item at specified row number.
     */
    public void deleteRow(String rowNumber) {
        enterRowNumber(rowNumber);
        tapOk();
    }
}
