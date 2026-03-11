package com.mavis.scanner.pages.dialogs;

import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Dialog for entering manual count when closing a section.
 * Layout: manual_count_layout.xml
 */
public class ManualCountDialog {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final By SECTION_TITLE = By.id("com.mavis.inventory_barcode_scanner:id/DialogTitleSection");
    private static final By DIALOG_TITLE = By.id("com.mavis.inventory_barcode_scanner:id/manualDialogTitle");
    private static final By ROOM_SPINNER = By.id("com.mavis.inventory_barcode_scanner:id/spinnerRooms");
    private static final By ROOM_NAME_INPUT = By.id("com.mavis.inventory_barcode_scanner:id/enterRoomName");
    private static final By MANUAL_COUNT = By.id("com.mavis.inventory_barcode_scanner:id/editTextManualCount");
    private static final By BTN_SUBMIT = By.id("android:id/button3");   // neutral = "Submit"
    private static final By BTN_OK = By.id("android:id/button1");       // fallback
    private static final By BTN_CANCEL = By.id("android:id/button2");

    public ManualCountDialog(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    public boolean isDisplayed() {
        return WaitHelper.isElementPresent(driver, MANUAL_COUNT);
    }

    public String getSectionTitle() {
        return WaitHelper.getTextSafe(driver, SECTION_TITLE);
    }

    public ManualCountDialog enterManualCount(String count) {
        WaitHelper.waitAndType(wait, MANUAL_COUNT, count);
        return this;
    }

    public ManualCountDialog enterRoomName(String roomName) {
        WaitHelper.waitAndType(wait, ROOM_NAME_INPUT, roomName);
        return this;
    }

    /**
     * Select a room from the spinner dropdown.
     */
    public ManualCountDialog selectRoom() {
        WaitHelper.waitAndClick(wait, ROOM_SPINNER);
        // Select first item in the spinner dropdown
        try {
            Thread.sleep(500);
            By firstOption = By.className("android.widget.CheckedTextView");
            WaitHelper.waitAndClick(wait, firstOption);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this;
    }

    /**
     * Tap the Submit button (neutral/button3).
     * The manual count dialog uses SetNeutralButton("Submit") in the source code.
     */
    public void tapSubmit() {
        // Try text-based locator first, then button3, then button1 as fallback
        By submitByText = By.xpath("//*[@text='Submit']");
        if (WaitHelper.isElementPresent(driver, submitByText)) {
            driver.findElement(submitByText).click();
        } else if (WaitHelper.isElementPresent(driver, BTN_SUBMIT)) {
            driver.findElement(BTN_SUBMIT).click();
        } else {
            WaitHelper.waitAndClick(wait, BTN_OK);
        }
    }

    public void tapCancel() {
        WaitHelper.waitAndClick(wait, BTN_CANCEL);
    }

    /**
     * Close section with a manual count value.
     * Enters the count, taps Submit, and waits for processing.
     */
    public void closeWithCount(String count) {
        enterManualCount(count);
        tapSubmit();
        // Wait for the section close to process
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
