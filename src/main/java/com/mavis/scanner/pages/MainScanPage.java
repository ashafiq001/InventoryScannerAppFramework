package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Page Object for MainActivity - primary tire/inventory scanning interface.
 *
 * Layout: content_main.xml
 * Activity: MainActivity (LaunchMode = SingleTop)
 * DataWedge Action: com.darryncampbell.datawedge.xamarin.ACTION
 */
public class MainScanPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from content_main.xml)
    private static final By ITEM_LIST = By.id("com.mavis.inventory_barcode_scanner:id/txtOutput");
    private static final By SECTION_OUTPUT = By.id("com.mavis.inventory_barcode_scanner:id/txtSectionOutput");
    private static final By BTN_ADD_ITEM = By.id("com.mavis.inventory_barcode_scanner:id/btnEnterItemShowDialog");
    private static final By BTN_CLOSE_SECTION = By.id("com.mavis.inventory_barcode_scanner:id/btnCloseSection");
    private static final By BTN_SUMMARY = By.id("com.mavis.inventory_barcode_scanner:id/btnShowSummary");
    private static final By BTN_FINISH = By.id("com.mavis.inventory_barcode_scanner:id/btnFinish");

    public MainScanPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM)
                && WaitHelper.isElementPresent(driver, BTN_CLOSE_SECTION);
    }

    public String getSectionOutput() {
        return WaitHelper.getTextSafe(driver, SECTION_OUTPUT);
    }

    public boolean isAddItemButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM);
    }

    public boolean isCloseSectionButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_CLOSE_SECTION);
    }

    public boolean isSummaryButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_SUMMARY);
    }

    public boolean isFinishButtonDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_FINISH);
    }

    /**
     * Get count of items in the scanned list.
     */
    public int getItemCount() {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get text of a specific item in the list by index.
     */
    public String getItemText(int index) {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            if (index < items.size()) {
                return items.get(index).getText();
            }
        } catch (Exception e) {
            // fall through
        }
        return "";
    }

    /**
     * Tap "Add Item" to open the add item dialog.
     */
    public void tapAddItem() {
        WaitHelper.waitAndClick(wait, BTN_ADD_ITEM);
    }

    /**
     * Tap "Close Section" to open the manual count dialog.
     */
    public void tapCloseSection() {
        WaitHelper.waitAndClick(wait, BTN_CLOSE_SECTION);
    }

    /**
     * Tap "Summary" to view section summary.
     */
    public void tapSummary() {
        WaitHelper.waitAndClick(wait, BTN_SUMMARY);
    }

    /**
     * Tap "Finish" to complete the inventory and navigate to FinalConfirmActivity.
     */
    public void tapFinish() {
        WaitHelper.waitAndClick(wait, BTN_FINISH);
    }
}
