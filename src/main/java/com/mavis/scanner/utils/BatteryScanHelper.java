package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.BatteryReceivePage;
import com.mavis.scanner.pages.BatteryReturnPage;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * High-level helper for battery barcode scanning operations.
 * Wraps DataWedgeHelper with battery-specific intent actions and
 * tracks scan counts for count validation.
 */
public class BatteryScanHelper {

    private final AndroidDriver driver;
    private final WebDriverWait wait;
    private final DataWedgeHelper dwHelper;

    private int scanCount = 0;

    public BatteryScanHelper(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
        this.dwHelper = new DataWedgeHelper(driver);
    }

    /**
     * Scan a battery barcode for the RECEIVE flow.
     */
    public void scanReceive(String upc) throws InterruptedException {
        dwHelper.scanBatteryReceiveBarcode(upc);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        scanCount++;
    }

    /**
     * Scan a battery barcode for the RECEIVE flow multiple times.
     */
    public void scanReceive(String upc, int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            scanReceive(upc);
        }
    }

    /**
     * Scan a battery barcode for the RETURNS flow.
     */
    public void scanReturn(String upc) throws InterruptedException {
        dwHelper.scanBatteryReturnBarcode(upc);
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
        scanCount++;
    }

    /**
     * Scan a battery barcode for the RETURNS flow multiple times.
     */
    public void scanReturn(String upc, int times) throws InterruptedException {
        for (int i = 0; i < times; i++) {
            scanReturn(upc);
        }
    }

    /**
     * Get total number of scans performed since last reset.
     */
    public int getScanCount() {
        return scanCount;
    }

    /**
     * Reset the scan counter (call when starting a new receive/return session).
     */
    public void resetCount() {
        scanCount = 0;
    }
}
