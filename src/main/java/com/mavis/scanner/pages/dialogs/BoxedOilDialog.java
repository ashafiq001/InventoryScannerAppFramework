package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog for selecting boxed oil quantity (Full, 3/4, Half, 1/4).
 * Layout: boxedOil_layout.xml
 */
public class BoxedOilDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/boxedOilTitle");
    private static final By ITEM_OUTPUT = By.id("com.mavis.inventory_barcode_scanner:id/txtBoxedOilItemOutput");
    private static final By BTN_FULL = By.id("com.mavis.inventory_barcode_scanner:id/btnFull");
    private static final By BTN_THREE_QUARTER = By.id("com.mavis.inventory_barcode_scanner:id/btn3qtrfull");
    private static final By BTN_HALF = By.id("com.mavis.inventory_barcode_scanner:id/btnHalf");
    private static final By BTN_QUARTER = By.id("com.mavis.inventory_barcode_scanner:id/btn1qtrfull");

    public BoxedOilDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_FULL);
    }

    public String getItemOutput() {
        return WaitHelper.getTextSafe(driver, ITEM_OUTPUT);
    }

    public void selectFull() {
        WaitHelper.waitAndClick(wait, BTN_FULL);
    }

    public void selectThreeQuarter() {
        WaitHelper.waitAndClick(wait, BTN_THREE_QUARTER);
    }

    public void selectHalf() {
        WaitHelper.waitAndClick(wait, BTN_HALF);
    }

    public void selectQuarter() {
        WaitHelper.waitAndClick(wait, BTN_QUARTER);
    }
}
