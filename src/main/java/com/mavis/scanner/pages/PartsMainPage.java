package com.mavis.scanner.pages;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Page Object for MainActivityParts - parts item scanning interface.
 *
 * Layout: content_mainParts.xml
 * Activity: MainActivityParts
 * DataWedge Action: com.darryncampbell.datawedge.xamarin.ACTIONPARTS
 */
public class PartsMainPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    // Element locators (from content_mainParts.xml)
    private static final By ITEM_LIST = By.id("com.mavis.inventory_barcode_scanner:id/txtOutput");
    private static final By SECTION_OUTPUT = By.id("com.mavis.inventory_barcode_scanner:id/txtSectionOutputP");
    private static final By BTN_ADD_ITEM = By.id("com.mavis.inventory_barcode_scanner:id/btnEnterItemShowDialog");
    private static final By BTN_CLOSE_SECTION = By.id("com.mavis.inventory_barcode_scanner:id/btnCloseSection");
    private static final By BTN_FINISH_PC = By.id("com.mavis.inventory_barcode_scanner:id/btnFinishPc");

    public PartsMainPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, BTN_ADD_ITEM)
                && WaitHelper.isElementPresent(driver, BTN_FINISH_PC);
    }

    public String getSectionOutput() {
        return WaitHelper.getTextSafe(driver, SECTION_OUTPUT);
    }

    public int getItemCount() {
        try {
            WebElement listView = driver.findElement(ITEM_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    public void tapAddItem() {
        WaitHelper.waitAndClick(wait, BTN_ADD_ITEM);
    }

    public void tapCloseSection() {
        WaitHelper.waitAndClick(wait, BTN_CLOSE_SECTION);
    }

    /**
     * Tap "Finish PC" to complete this category and return to PartsCategoryPage.
     */
    public void tapFinishCategory() {
        WaitHelper.waitAndClick(wait, BTN_FINISH_PC);
    }
}
