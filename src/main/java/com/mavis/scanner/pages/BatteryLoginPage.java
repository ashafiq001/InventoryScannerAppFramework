package com.mavis.scanner.pages;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for BatteryLogin - authentication for battery operations.
 *
 * Layout: batteryLogin.xml
 * Activity: BatteryLogin
 * Fields: Store #, Employee #
 */
public class BatteryLoginPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final String PKG = "com.mavis.inventory_barcode_scanner:id/";

    private static final By TITLE = By.id(PKG + "loginTitle");
    private static final By TXT_STORE = By.id(PKG + "txtStore");
    private static final By TXT_EMPLOYEE = By.id(PKG + "txtEmplNum");
    private static final By BTN_LOGIN = By.id(PKG + "btnLogin");
    private static final By BTN_HOME = By.id(PKG + "btnHome");
    private static final By INV_INFO = By.id(PKG + "invInfo");
    private static final By APP_VERSION = By.id(PKG + "appVersion");

    public BatteryLoginPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_LOGIN)
                && WaitHelper.isElementPresent(driver, TXT_STORE);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    public String getInfoMessage() {
        return WaitHelper.getTextSafe(driver, INV_INFO);
    }

    public BatteryLoginPage enterStore(String storeNumber) {
        WaitHelper.waitAndType(wait, TXT_STORE, storeNumber);
        return this;
    }

    public BatteryLoginPage enterEmployee(String employeeNumber) {
        WaitHelper.waitAndType(wait, TXT_EMPLOYEE, employeeNumber);
        return this;
    }

    /**
     * Tap Login button. After successful login + barcode master list sync,
     * navigates to BatteryMenuActivity.
     */
    public void tapLogin() {
        WaitHelper.waitAndClick(wait, BTN_LOGIN);
    }

    /**
     * Full login flow with credentials.
     */
    public void login(String store, String employee) {
        enterStore(store);
        enterEmployee(employee);
        tapLogin();
    }

    /**
     * Login with default test credentials from AppConfig.
     */
    public void loginWithDefaults() {
        login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE);
    }

    public BatteryLoginPage tapHome() {
        WaitHelper.waitAndClick(wait, BTN_HOME);
        return this;
    }
}
