package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for StartHome screen - the app's main entry point.
 *
 * Layout: startHome.xml
 * Activity: StartHome (MainLauncher = true)
 */
public class StartHomePage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from startHome.xml)
    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/loginTitle");
    private static final By TITLE_NOTE = By.id("com.mavis.inventory_barcode_scanner:id/loginTitleNote");
    private static final By BTN_INVENTORY = By.id("com.mavis.inventory_barcode_scanner:id/btnInv");
    private static final By INV_INFO = By.id("com.mavis.inventory_barcode_scanner:id/invInfo");
    private static final By APP_VERSION = By.id("com.mavis.inventory_barcode_scanner:id/appVersion");
    private static final By APP_LOGO = By.id("com.mavis.inventory_barcode_scanner:id/imageView");

    public StartHomePage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_INVENTORY);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    public String getTitleNote() {
        return WaitHelper.getTextSafe(driver, TITLE_NOTE);
    }

    public String getAppVersion() {
        return WaitHelper.getTextSafe(driver, APP_VERSION);
    }

    public boolean isLogoDisplayed() {
        return WaitHelper.isElementPresent(driver, APP_LOGO);
    }

    public boolean isInventoryButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_INVENTORY);
    }

    public String getInventoryButtonText() {
        return WaitHelper.getTextSafe(driver, BTN_INVENTORY);
    }

    /**
     * Tap "Start Inventory" to navigate to LoginActivity.
     */
    public LoginPage tapStartInventory() {
        WaitHelper.waitAndClick(wait, BTN_INVENTORY);
        return new LoginPage(driver, wait);
    }
}
