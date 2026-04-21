package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Collections;

/**
 * Simulate barcode scans on the fly — just pass the barcode.
 * Auto-detects section vs item, tire vs parts from the current app state.
 *
 * Usage:
 *   ScanHelper scan = new ScanHelper(driver, wait);
 *
 *   scan.scan("STR-1002");          // opens section (detected by STR- prefix)
 *   scan.scan("092971236021");      // scans item
 *   scan.scan("092971277918");      // scans another item
 *   scan.scan("715459469376", 4);   // scans item 4 times
 *   scan.closeSection();            // closes section (manual count = scans in current section)
 *
 *   scan.scan("STR-1003");          // opens next section (auto-closes previous if still open)
 *   scan.scan("841623129200");
 *   scan.closeSection();
 */
public class ScanHelper {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    private final AndroidDriver driver;
    private final WebDriverWait wait;
    private final DataWedgeHelper dwHelper;

    private int sectionScanCount = 0;
    private boolean sectionOpen = false;
    private String currentSection = null;
    private int batteryReturnScanCount = 0;

    public ScanHelper(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
        this.dwHelper = new DataWedgeHelper(driver);
    }

    /**
     * Scan any barcode. Detects section (STR-*) vs item automatically.
     * Detects tire vs parts from the current activity.
     */
    public void scan(String barcode) throws InterruptedException {
        if (isSection(barcode)) {
            if (sectionOpen) {
                closeSection();
            }
            openSection(barcode);
        } else {
            scanItem(barcode);
        }
    }

    public void scanBattery(String barcode) throws InterruptedException {
        scanItem(barcode);
    }

    /**
     * Scan an item barcode multiple times (e.g., quantity on hand).
     */
    public void scan(String barcode, int times) throws InterruptedException {
        if (isSection(barcode)) {
            scan(barcode);
        } else {
            for (int i = 0; i < times; i++) {
                scanItem(barcode);
            }
        }
    }

    /**
     * Close the current section. Manual count = number of scans since section opened.
     */
    public void closeSection() throws InterruptedException {
        closeSection(sectionScanCount);
    }

    /**
     * Close the current section with a specific manual count.
     */
    public void closeSection(int manualCount) throws InterruptedException {
        scrollToBottom();

        By btnClose = By.id("com.mavis.inventory_barcode_scanner:id/btnCloseSection");
        if (WaitHelper.isElementPresent(driver, btnClose)) {
            driver.findElement(btnClose).click();
        }
        Thread.sleep(AppConfig.SHORT_WAIT);

        By completeBtn = byText("Complete");
        if (WaitHelper.isElementPresent(driver, completeBtn)) {
            driver.findElement(completeBtn).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
            driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
            driver.findElement(DIALOG_BUTTON_POSITIVE).click();
        }
        Thread.sleep(AppConfig.SHORT_WAIT);

        ManualCountDialog countDialog = new ManualCountDialog(driver, wait);
        if (countDialog.isDisplayed()) {
            countDialog.closeWithCount(String.valueOf(manualCount));
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        }

        sectionOpen = false;
        sectionScanCount = 0;
        currentSection = null;
    }

    /** Current section barcode, or null if no section is open. */
    public String getCurrentSection() {
        return currentSection;
    }

    /** Number of scans in the current open section. */
    public int getSectionScanCount() {
        return sectionScanCount;
    }


    /**
     * Scan a battery barcode for the returns flow.
     */
    public void scanBatteryReturn(String upc) throws InterruptedException {
        dwHelper.scanBatteryReturnBarcode(upc);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        batteryReturnScanCount++;
    }

    /**
     * Scan a battery barcode for the returns flow multiple times.
     */
    public void scanBatteryReturn(String upc, int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            scanBatteryReturn(upc);
        }
    }

    /** Number of battery return scans since last reset. */
    public int getBatteryReturnScanCount() {
        return batteryReturnScanCount;
    }

    /** Reset the battery return scan counter. */
    public void resetBatteryReturnCount() {
        batteryReturnScanCount = 0;
    }


    // ==================== INTERNAL ====================

    private boolean isSection(String barcode) {
        return barcode != null && barcode.toUpperCase().startsWith("STR-");
    }

    private boolean isOnPartsScreen() {
        try {
            String activity = driver.currentActivity();
            return activity != null && activity.contains("MainActivityParts");
        } catch (Exception e) {
            return false;
        }
    }

    private void openSection(String sectionBarcode) throws InterruptedException {
        if (isOnPartsScreen()) {
            dwHelper.simulateScan(sectionBarcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
        } else {
            dwHelper.scanSectionBarcode(sectionBarcode);
        }
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissAnyDialog();
        sectionOpen = true;
        sectionScanCount = 0;
        currentSection = sectionBarcode;
    }

    private void scanItem(String upc) throws InterruptedException {
        if (isOnPartsScreen()) {
            dwHelper.simulateScan(upc, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
        } else {
            dwHelper.scanItemBarcode(upc);
        }
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        dismissAnyDialog();
        sectionScanCount++;
    }

    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                String btnText = driver.findElement(DIALOG_BUTTON_POSITIVE).getText();
                if (btnText != null && btnText.toLowerCase().contains("go back")) {
                    return;
                }
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(500);
            }
        } catch (Exception e) { /* No dialog */ }
    }

    private void scrollToBottom() {
        try {
            Dimension size = driver.manage().window().getSize();
            int startX = size.width / 2;
            int startY = (int) (size.height * 0.8);
            int endY = (int) (size.height * 0.2);
            PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
            Sequence swipe = new Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(Duration.ZERO,
                    PointerInput.Origin.viewport(), startX, startY));
            swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(Duration.ofMillis(500),
                    PointerInput.Origin.viewport(), startX, endY));
            swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(swipe));
            Thread.sleep(500);
        } catch (Exception e) { /* Scroll failed */ }
    }

    private static By byText(String text) {
        return By.xpath("//*[@text='" + text + "']");
    }
}
