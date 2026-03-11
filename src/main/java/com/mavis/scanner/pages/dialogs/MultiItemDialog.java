package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog shown when a UPC maps to multiple items - user picks the correct one.
 * Layout: upcToMultiItem.xml
 */
public class MultiItemDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/UpcMultiItemTitle");
    private static final By INSTRUCTIONS = By.id("com.mavis.inventory_barcode_scanner:id/UpcMultiItemTitle2");
    private static final By BTN_ITEM_1 = By.id("com.mavis.inventory_barcode_scanner:id/btnChooseitem1");
    private static final By BTN_ITEM_2 = By.id("com.mavis.inventory_barcode_scanner:id/btnChooseitem2");

    public MultiItemDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ITEM_1);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    public String getItem1Text() {
        return WaitHelper.getTextSafe(driver, BTN_ITEM_1);
    }

    public String getItem2Text() {
        return WaitHelper.getTextSafe(driver, BTN_ITEM_2);
    }

    public void selectItem1() {
        WaitHelper.waitAndClick(wait, BTN_ITEM_1);
    }

    public void selectItem2() {
        WaitHelper.waitAndClick(wait, BTN_ITEM_2);
    }
}
