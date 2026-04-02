package com.mavis.scanner.config;

/**
 * Central configuration for the Inventory Scanner Appium test suite.
 */
public class AppConfig {

    // ==================== DEVICE & APPIUM ====================
    public static final String APPIUM_URL = "http://127.0.0.1:4723";
    public static final String DEVICE_UDID ="23167524701232";
    public static final String Device_UDID2 = "24028524700126";
    public static final String PLATFORM_NAME = "Android";
    public static final String AUTOMATION_NAME = "UiAutomator2";
    public static final String DEFAULT_DEVICE_UDID = "23167524701232";


    public static String getDeviceUDID() {
        String udid = System.getProperty("DEVICE_UDID");
        return (udid != null && !udid.isEmpty()) ? udid : DEFAULT_DEVICE_UDID;
    }


    //"24028524700126";
    // ==================== APP UNDER TEST ====================
    public static final String APP_PACKAGE = "com.mavis.inventory_barcode_scanner";
    // Xamarin CRC-hashed activity prefix
    public static final String XAMARIN_CRC = "crc645a25d35f8b28a2b1";
    public static final String APP_ACTIVITY = XAMARIN_CRC + ".StartHome";

    // Full activity class names (Xamarin-compiled)
    public static final String ACTIVITY_START_HOME = XAMARIN_CRC + ".StartHome";
    public static final String ACTIVITY_LOGIN = XAMARIN_CRC + ".LoginActivity";
    public static final String ACTIVITY_MAIN = XAMARIN_CRC + ".MainActivity";
    public static final String ACTIVITY_PARTS_PC = XAMARIN_CRC + ".PartsPCActivity";
    public static final String ACTIVITY_MAIN_PARTS = XAMARIN_CRC + ".MainActivityParts";
    public static final String ACTIVITY_BATTERY_RETURN = XAMARIN_CRC + ".BatteryReturnActivity";
    public static final String ACTIVITY_BATTERY_RECEIVE = XAMARIN_CRC + ".BatteryReceiveActivity";
    public static final String ACTIVITY_BATTERY_LOGIN = XAMARIN_CRC + ".BatteryReturnsLogin";
    public static final String ACTIVITY_FINAL_CONFIRM = XAMARIN_CRC + ".FinalConfirmActivity";

    // ==================== DATAWEDGE INTENT ACTIONS ====================
    public static final String DW_ACTION_TIRES = "com.darryncampbell.datawedge.xamarin.ACTION";
    public static final String DW_ACTION_PARTS = "com.darryncampbell.datawedge.xamarin.ACTIONPARTS";
    public static final String DW_ACTION_CATEGORIES = "com.darryncampbell.datawedge.xamarin.ACTIONCategories";
    public static final String DW_ACTION_BATTERY = "com.darryncampbell.datawedge.xamarin.ACTIONBATTERYRETURNS";
    public static final String DW_ACTION_BATTERY_RETURNS = "com.darryncampbell.datawedge.xamarin.ACTIONBATTRETURNS";
    public static final String DW_ACTION_BATTERY_RECEIVE = "com.darryncampbell.datawedge.xamarin.ACTIONBATTRECIEVE";


    // DataWedge intent extra keys
    public static final String DW_KEY_DATA = "com.symbol.datawedge.data_string";
    public static final String DW_KEY_LABEL_TYPE = "com.symbol.datawedge.label_type";
    public static final String DW_KEY_SOURCE = "com.symbol.datawedge.source";

    // ==================== TEST CREDENTIALS ====================
    public static final String TEST_STORE = "2030";
    public static final String TEST_EMPLOYEE = "421628";
    public static final String TEST_INV_CODE = "63727"; // Override per environment

    // ==================== API ====================
    public static final String API_BASE_URL = "https://inventoryscanapi.qa.mavis.com";

    // ==================== TIMEOUTS (milliseconds) ====================
    public static final int DEFAULT_TIMEOUT = 30;       // seconds for WebDriverWait
    public static final int SHORT_WAIT = 1000;
    public static final int MEDIUM_WAIT = 2000;
    public static final int LONG_WAIT = 5000;
    public static final int LOGIN_SYNC_WAIT = 15000;    // data sync after login
    public static final int SCAN_PROCESS_WAIT = 2000;   // barcode processing time


    // ==================== ANDROID SDK ====================
    public static final String ANDROID_HOME = "C:\\Users\\ashafiq\\AppData\\Local\\Android\\Sdk";
    public static final String ADB_PATH = ANDROID_HOME + "\\platform-tools\\adb.exe";


    // ==================== PRODUCT CATEGORIES (PC) ====================
    public static final int PC_OIL = 188;
    public static final int PC_OIL_FILTERS = 86;
    public static final int PC_TPMS = 95;
    public static final int PC_WIPERS = 92;
    public static final int PC_AIR_CONDITION = 62;
    public static final int PC_ROTOR_BRAKEPAD = 66;
    public static final int PC_BATTERIES = 65;
    public static final int PC_TIRES = 2;


    public static String getPcCodeFromLabel(String label) {
        if (label == null || label.isEmpty()) return null;
        String lower = label.toLowerCase().trim();
        if (lower.contains("oil filter") || lower.contains("filters")) return String.valueOf(PC_OIL_FILTERS);
        if (lower.contains("oil")) return String.valueOf(PC_OIL);
        if (lower.contains("tpms") || lower.contains("tire pressure")) return String.valueOf(PC_TPMS);
        if (lower.contains("wiper")) return String.valueOf(PC_WIPERS);
        if (lower.contains("air condition") || lower.contains("a/c") || lower.contains("ac")) return String.valueOf(PC_AIR_CONDITION);
        if (lower.contains("rotor") || lower.contains("brake")) return String.valueOf(PC_ROTOR_BRAKEPAD);
        if (lower.contains("batter")) return String.valueOf(PC_BATTERIES);
        if (lower.contains("tire")) return String.valueOf(PC_TIRES);
        return null;
    }

    // ==================== DATABASE (DEV) ====================
    public static final String DB_SERVER = "Server3.db.dev.local.mavis-hq.com";
    public static final String DB_PORT = "1433";
    public static final String DB_USERNAME = "MavisDeveloper_DEV";
    public static final String DB_PASSWORD = "RxtSkHZl2UrKFCvKzJiX9T7Mj";
    public static final String DB_INVENTORY = "InventoryScanning";
    public static final String DB_TIREMAX = "TireMaxLive";

    // ==================== FALLBACK TEST UPC DATA ====================
    // Known valid tire items from Mavis_Automation fallback data
    // Format: {itemNumber, upc}
    public static final String[][] FALLBACK_TIRE_DATA = {
            {"000180", "2356518"},
            {"000227", "2656518"},
            {"000238", "2455517"},
            {"000216", "2356018"}
    };

    // Known valid storage location barcodes
    public static final String[] FALLBACK_SECTION_BARCODES = {
            "STR-1041", "STR-1079", "STR-1080"
    };
}
