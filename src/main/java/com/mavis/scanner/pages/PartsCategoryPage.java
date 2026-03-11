package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for PartsPCActivity - parts category selection screen.
 *
 * Layout: partsPcPage.xml
 * Activity: PartsPCActivity
 * Shows up to 7 product categories with Start buttons and checkboxes.
 */
public class PartsCategoryPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from partsPcPage.xml)
    private static final By TITLE = By.id("com.mavis.inventory_barcode_scanner:id/enterPcTitle");
    private static final By PROGRESS_BAR = By.id("com.mavis.inventory_barcode_scanner:id/progressBartop");
    private static final By BTN_LOGOUT = By.id("com.mavis.inventory_barcode_scanner:id/btnLogout");
    private static final By BTN_SUMMARY = By.id("com.mavis.inventory_barcode_scanner:id/btnShowSummary");
    private static final By BTN_FINISH = By.id("com.mavis.inventory_barcode_scanner:id/btnFinishParts");

    // Dynamic locators for category slots 1-7
    private static By pcLabel(int slot) {
        return By.id("com.mavis.inventory_barcode_scanner:id/pc" + slot);
    }

    private static By startButton(int slot) {
        return By.id("com.mavis.inventory_barcode_scanner:id/btnStart" + slot);
    }

    private static By checkBox(int slot) {
        return By.id("com.mavis.inventory_barcode_scanner:id/checkBox" + slot);
    }

    public PartsCategoryPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /**
     * Check if we're on the Parts Category screen.
     * Note: btnFinishParts starts INVISIBLE and only shows after completing a PC.
     * Use enterPcTitle + btnLogout which are always visible on this screen.
     */
    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, TITLE)
                && WaitHelper.isElementPresent(driver, BTN_LOGOUT);
    }

    /**
     * Check if the Finish button is visible (only after at least one PC is completed).
     */
    public boolean isFinishButtonVisible() {
        try {
            org.openqa.selenium.WebElement btn = driver.findElement(BTN_FINISH);
            return btn.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    public String getTitle() {
        return WaitHelper.getTextSafe(driver, TITLE);
    }

    /**
     * Get the label text for a category slot (1-7).
     */
    public String getCategoryLabel(int slot) {
        return WaitHelper.getTextSafe(driver, pcLabel(slot));
    }

    /**
     * Check if a category slot has a Start button visible.
     */
    public boolean isStartButtonVisible(int slot) {
        return WaitHelper.isElementPresent(driver, startButton(slot));
    }

    /**
     * Check if a category checkbox is checked (completed).
     */
    public boolean isCategoryCompleted(int slot) {
        try {
            WebElement cb = driver.findElement(checkBox(slot));
            return cb.getAttribute("checked").equals("true");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tap Start for a specific category slot to begin scanning.
     */
    public void tapStart(int slot) {
        WaitHelper.waitAndClick(wait, startButton(slot));
    }

    /**
     * Get count of visible category slots.
     */
    public int getVisibleCategoryCount() {
        int count = 0;
        for (int i = 1; i <= 7; i++) {
            if (WaitHelper.isElementPresent(driver, pcLabel(i))) {
                String text = WaitHelper.getTextSafe(driver, pcLabel(i));
                if (!text.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    public void tapSummary() {
        WaitHelper.waitAndClick(wait, BTN_SUMMARY);
    }

    public void tapFinish() {
        WaitHelper.waitAndClick(wait, BTN_FINISH);
    }

    public void tapLogout() {
        WaitHelper.waitAndClick(wait, BTN_LOGOUT);
    }
}
