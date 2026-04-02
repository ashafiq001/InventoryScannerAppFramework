package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for BatteryMenuActivity - choose between Receive or Returns.
 *
 * Layout: batteryMenu.xml
 * Activity: BatteryMenuActivity
 */
public class BatteryMenuPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final String PKG = "com.mavis.inventory_barcode_scanner:id/";

    private static final By TITLE = By.id(PKG + "loginTitle");
    private static final By TITLE_NOTE = By.id(PKG + "loginTitleNote");
    private static final By BTN_RECEIVE = By.id(PKG + "btnRcv");
    private static final By BTN_RETURNS = By.id(PKG + "btnRtrn");
    private static final By BTN_HOME = By.id(PKG + "btnHome");

    public BatteryMenuPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_RECEIVE)
                && WaitHelper.isElementPresent(driver, BTN_RETURNS);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    public String getTitleNote() {
        return WaitHelper.getTextSafe(driver, TITLE_NOTE);
    }

    public boolean isReceiveButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_RECEIVE);
    }

    public boolean isReturnsButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_RETURNS);
    }

    /**
     * Tap "Battery Receive" to navigate to BatteryReceiveActivity.
     */
    public void tapBatteryReceive() {
        WaitHelper.waitAndClick(wait, BTN_RECEIVE);
    }

    /**
     * Tap "Battery Returns" to navigate to BatteryReturnWizard.
     */
    public void tapBatteryReturns() {
        WaitHelper.waitAndClick(wait, BTN_RETURNS);
    }

    /**
     * Tap "Back to Start" to navigate to StartHome.
     */
    public void tapHome() {
        WaitHelper.waitAndClick(wait, BTN_HOME);
    }
}
