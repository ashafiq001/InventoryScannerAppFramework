package com.mavis.scanner.config;

/**
 * Configuration for the Deadcount Scanner app.
 * Separate app from the Barcode Scanner — different package, activities, and intent actions.
 */
public class DeadcountConfig {

    // ==================== APP ====================
    public static final String APP_PACKAGE = "com.mavis.deadcount";
    public static final String XAMARIN_CRC = "crc64412511ba1f973fe4"; // may differ — discover via adb
    public static final String APP_ACTIVITY = XAMARIN_CRC + ".LoginActivity";

    // ==================== DATAWEDGE ====================
    public static final String DW_ACTION = "com.mavis.deadcount.ACTION";
    public static final String DW_KEY_DATA = "com.symbol.datawedge.data_string";
    public static final String DW_KEY_LABEL_TYPE = "com.symbol.datawedge.label_type";

    // ==================== ACTIVITIES ====================
    public static final String ACTIVITY_LOGIN = XAMARIN_CRC + ".LoginActivity";
    public static final String ACTIVITY_MAIN = XAMARIN_CRC + ".MainActivity";
    public static final String ACTIVITY_FINAL_CONFIRM = XAMARIN_CRC + ".FinalConfirmActivity";

    // ==================== VALIDATION ====================
    public static final int MAX_SECTION_COUNT = 250;
    public static final double PREV_COUNT_VARIANCE_THRESHOLD = 0.50; // 50%
}
