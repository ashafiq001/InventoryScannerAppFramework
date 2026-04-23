package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog for entering quantity after scanning wipers (PC 92), oil filters (PC 86),
 * batteries (PC 65), TPMS (PC 95), and engine clean kits (PC 80).
 *
 * When an item is scanned, the app shows a dialog with a quantity field (default 1)
 * and a Submit button. The user enters the count and taps Submit.
 */
public class QuantityEntryDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By QTY_FIELD = By.id("com.mavis.inventory_barcode_scanner:id/addItemQty");
    private static final By QTY_FIELD_ALT = By.id("com.mavis.inventory_barcode_scanner:id/editTextQty");
    private static final By ANY_EDIT_TEXT = By.className("android.widget.EditText");
    private static final By BTN_SUBMIT = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='submit']");
    private static final By BTN_OK = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='ok']");
    private static final By BTN_NEUTRAL = By.id("android:id/button3");
    private static final By BTN_POSITIVE = By.id("android:id/button1");

    public QuantityEntryDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /**
     * Check if the quantity entry dialog is displayed.
     * Tries multiple known locators for the qty field.
     */
    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, QTY_FIELD)
                || WaitHelper.isElementPresent(driver, QTY_FIELD_ALT);
    }

    /**
     * Find the quantity input field, trying known IDs then falling back to EditText.
     */
    private WebElement findQtyField() {
        if (WaitHelper.isElementPresent(driver, QTY_FIELD)) return driver.findElement(QTY_FIELD);
        if (WaitHelper.isElementPresent(driver, QTY_FIELD_ALT)) return driver.findElement(QTY_FIELD_ALT);
        if (WaitHelper.isElementPresent(driver, ANY_EDIT_TEXT)) return driver.findElement(ANY_EDIT_TEXT);
        return null;
    }

    /**
     * Get the current quantity value shown in the field.
     */
    public String getQuantity() {
        WebElement field = findQtyField();
        if (field == null) return "";
        String text = field.getText();
        if (text == null || text.trim().isEmpty()) text = field.getAttribute("text");
        return text != null ? text.trim() : "";
    }

    /**
     * Enter a quantity value in the field.
     */
    public QuantityEntryDialog enterQuantity(String qty) {
        WebElement field = findQtyField();
        if (field != null) {
            field.clear();
            field.sendKeys(qty);
        }
        return this;
    }

    /**
     * Tap Submit/OK to confirm the quantity.
     */
    public void tapSubmit() {
        if (WaitHelper.isElementPresent(driver, BTN_SUBMIT)) {
            driver.findElement(BTN_SUBMIT).click();
        } else if (WaitHelper.isElementPresent(driver, BTN_NEUTRAL)) {
            driver.findElement(BTN_NEUTRAL).click();
        } else if (WaitHelper.isElementPresent(driver, BTN_OK)) {
            driver.findElement(BTN_OK).click();
        } else if (WaitHelper.isElementPresent(driver, BTN_POSITIVE)) {
            driver.findElement(BTN_POSITIVE).click();
        }
    }

    /**
     * Enter quantity and submit in one step.
     * If the field already has a valid quantity (non-zero, non-empty), submits as-is.
     */
    public void submitWithQuantity(String qty) {
        String existing = getQuantity();
        if (existing.isEmpty() || existing.equals("0")) {
            enterQuantity(qty);
        }
        tapSubmit();
    }
}
