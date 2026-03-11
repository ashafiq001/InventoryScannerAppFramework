package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.util.List;

/**
 * Dialog for adding an item by size and brand search.
 * Layout: enter_itemBySize_layout.xml
 */
public class AddItemBySizeDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By DIALOG_TITLE = By.id("com.mavis.inventory_barcode_scanner:id/enterItemDialogTitle");
    private static final By SIZE_INPUT = By.id("com.mavis.inventory_barcode_scanner:id/addGsizeText");
    private static final By BRAND_INPUT = By.id("com.mavis.inventory_barcode_scanner:id/addBrandText");
    private static final By RADIO_ALL = By.id("com.mavis.inventory_barcode_scanner:id/radioAll");
    private static final By RADIO_STORE = By.id("com.mavis.inventory_barcode_scanner:id/radioStore");
    private static final By ITEMS_LIST = By.id("com.mavis.inventory_barcode_scanner:id/listViewItems");
    private static final By BTN_OK = By.id("android:id/button1");
    private static final By BTN_CANCEL = By.id("android:id/button2");

    public AddItemBySizeDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, SIZE_INPUT);
    }

    public AddItemBySizeDialog enterSize(String size) {
        WaitHelper.waitAndType(wait, SIZE_INPUT, size);
        return this;
    }

    public AddItemBySizeDialog enterBrand(String brand) {
        WaitHelper.waitAndType(wait, BRAND_INPUT, brand);
        return this;
    }

    public AddItemBySizeDialog selectAllItems() {
        WaitHelper.waitAndClick(wait, RADIO_ALL);
        return this;
    }

    public AddItemBySizeDialog selectStoreItems() {
        WaitHelper.waitAndClick(wait, RADIO_STORE);
        return this;
    }

    public int getSearchResultCount() {
        try {
            WebElement listView = driver.findElement(ITEMS_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Select an item from the search results by index.
     */
    public void selectSearchResult(int index) {
        try {
            WebElement listView = driver.findElement(ITEMS_LIST);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            if (index < items.size()) {
                items.get(index).click();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not select search result at index " + index, e);
        }
    }

    public void tapOk() {
        WaitHelper.waitAndClick(wait, BTN_OK);
    }

    public void tapCancel() {
        WaitHelper.waitAndClick(wait, BTN_CANCEL);
    }
}
