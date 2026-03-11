package com.mavis.scanner.pages;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for LoginActivity - authentication and data sync.
 *
 * Layout: Login.xml
 * Activity: LoginActivity
 * Fields: Store #, Employee #, Inventory Code
 */
public class LoginPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from Login.xml)
    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/loginTitle");
    private static final By TXT_STORE = By.id("com.mavis.inventory_barcode_scanner:id/txtStore");
    private static final By TXT_EMPLOYEE = By.id("com.mavis.inventory_barcode_scanner:id/txtEmplNum");
    private static final By TXT_INV_CODE = By.id("com.mavis.inventory_barcode_scanner:id/txtInvCode");
    private static final By BTN_LOGIN = By.id("com.mavis.inventory_barcode_scanner:id/btnLogin");
    private static final By BTN_HOME = By.id("com.mavis.inventory_barcode_scanner:id/btnHome");
    private static final By INV_INFO = By.id("com.mavis.inventory_barcode_scanner:id/invInfo");
    private static final By APP_VERSION = By.id("com.mavis.inventory_barcode_scanner:id/appVersion");

    public LoginPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_LOGIN);
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    public String getInfoMessage() {
        return WaitHelper.getTextSafe(driver, INV_INFO);
    }

    public LoginPage enterStore(String storeNumber) {
        WaitHelper.waitAndType(wait, TXT_STORE, storeNumber);
        return this;
    }

    public LoginPage enterEmployee(String employeeNumber) {
        WaitHelper.waitAndType(wait, TXT_EMPLOYEE, employeeNumber);
        return this;
    }

    public LoginPage enterInventoryCode(String invCode) {
        WaitHelper.waitAndType(wait, TXT_INV_CODE, invCode);
        return this;
    }

    public boolean isStoreFieldDisplayed() {
        return WaitHelper.isElementPresent(driver, TXT_STORE);
    }

    public boolean isEmployeeFieldDisplayed() {
        return WaitHelper.isElementPresent(driver, TXT_EMPLOYEE);
    }

    public boolean isInvCodeFieldDisplayed() {
        return WaitHelper.isElementPresent(driver, TXT_INV_CODE);
    }

    public boolean isLoginButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_LOGIN);
    }

    /**
     * Tap Login button. After successful login + data sync,
     * navigates to either MainActivity (tires) or PartsPCActivity (parts).
     */
    public void tapLogin() {
        WaitHelper.waitAndClick(wait, BTN_LOGIN);
    }

    /**
     * Full login flow with credentials.
     */
    public void login(String store, String employee, String invCode) {
        enterStore(store);
        enterEmployee(employee);
        enterInventoryCode(invCode);
        tapLogin();
    }

    /**
     * Login with default test credentials from AppConfig.
     */
    public void loginWithDefaults() {
        login(AppConfig.TEST_STORE, AppConfig.TEST_EMPLOYEE, AppConfig.TEST_INV_CODE);
    }

    /**
     * Tap Home button to go back to StartHome.
     */
    public StartHomePage tapHome() {
        WaitHelper.waitAndClick(wait, BTN_HOME);
        return new StartHomePage(driver, wait);
    }
}
