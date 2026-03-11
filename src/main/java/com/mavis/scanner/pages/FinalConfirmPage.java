package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for FinalConfirmActivity - inventory completion summary.
 *
 * Layout: finalConfirmation_layout.xml
 * Activity: FinalConfirmActivity
 * Shows upload confirmation with section/item counts.
 */
public class FinalConfirmPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from finalConfirmation_layout.xml)
    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/loginTitle");
    private static final By COUNT_INFO = By.id("com.mavis.inventory_barcode_scanner:id/countInfo");
    private static final By BTN_LOGOUT = By.id("com.mavis.inventory_barcode_scanner:id/btnLogout");
    private static final By INV_INFO = By.id("com.mavis.inventory_barcode_scanner:id/invInfo");

    public FinalConfirmPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_LOGOUT)
                && WaitHelper.isElementPresent(driver, COUNT_INFO);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    /**
     * Get the upload confirmation message (e.g., "Uploaded 5 sections - 120 items").
     */
    public String getCountInfo() {
        return WaitHelper.getTextSafe(driver, COUNT_INFO);
    }

    public String getInventoryInfo() {
        return WaitHelper.getTextSafe(driver, INV_INFO);
    }

    /**
     * Tap Logout to return to LoginActivity.
     */
    public LoginPage tapLogout() {
        WaitHelper.waitAndClick(wait, BTN_LOGOUT);
        return new LoginPage(driver, wait);
    }
}
