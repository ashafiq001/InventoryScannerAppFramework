package com.mavis.scanner.utils;

import com.mavis.scanner.config.AppConfig;
import io.appium.java_client.android.AndroidDriver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Simulates DataWedge barcode scans on Zebra devices via ADB intents.
 *
 * The BarcodeScanner app uses DataWedge with intent delivery mode (startActivity).
 * Activities have LaunchMode=SingleTop, so intents trigger OnNewIntent().
 * We simulate scans by broadcasting the same intent actions the real scanner sends.
 */
public class DataWedgeHelper {

    private final AndroidDriver driver;

    public DataWedgeHelper(AndroidDriver driver) {
        this.driver = driver;
    }

    /**
     * Simulate a barcode scan by sending a DataWedge-style intent via ADB.
     *
     * @param barcodeData  The barcode string (e.g., "STR-001" for section, or UPC for item)
     * @param labelType    Barcode symbology (e.g., "LABEL-TYPE-CODE128", "LABEL-TYPE-I2OF5")
     * @param intentAction The DataWedge intent action for the target activity
     */
    public void simulateScan(String barcodeData, String labelType, String intentAction) {
        // Use ADB shell to send the intent, matching DataWedge's startActivity delivery
        String command = String.format(
                "am start -a %s -c android.intent.category.DEFAULT " +
                        "--es \"%s\" \"%s\" " +
                        "--es \"%s\" \"%s\" " +
                        "--es \"%s\" \"scanner\" " +
                        "-n %s/%s",
                intentAction,
                AppConfig.DW_KEY_DATA, barcodeData,
                AppConfig.DW_KEY_LABEL_TYPE, labelType,
                AppConfig.DW_KEY_SOURCE,
                AppConfig.APP_PACKAGE, getCurrentActivity(intentAction)
        );

        executeAdbShell(command);
    }

    /**
     * Simulate a tire/inventory section barcode scan (e.g., "STR-001").
     */
    public void scanSectionBarcode(String sectionBarcode) {
        simulateScan(sectionBarcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_TIRES);
    }

    /**
     * Simulate a tire/inventory item barcode scan (UPC code).
     */
    public void scanItemBarcode(String upcCode) {
        simulateScan(upcCode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_TIRES);
    }

    /**
     * Simulate a parts item barcode scan.
     */
    public void scanPartsItemBarcode(String upcCode) {
        simulateScan(upcCode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_PARTS);
    }

    /**
     * Simulate a battery receive barcode scan.
     */
    public void scanBatteryReceiveBarcode(String upcCode) {
        simulateScan(upcCode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_BATTERY_RECEIVE);
    }

    /**
     * Simulate a battery return barcode scan.
     */
    public void scanBatteryReturnBarcode(String upcCode) {
        simulateScan(upcCode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_BATTERY_RETURNS);
    }


    /**
     * Simulate a parts category barcode scan.
     */
    public void scanCategoryBarcode(String barcode) {
        simulateScan(barcode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_CATEGORIES);
    }

    /**
     * Simulate a battery return barcode scan.
     */
    public void scanBatteryBarcode(String upcCode) {
        simulateScan(upcCode, "LABEL-TYPE-CODE128", AppConfig.DW_ACTION_BATTERY);
    }

    /**
     * Maps intent actions to their Xamarin CRC-hashed activity class names.
     * Discovered via: adb shell dumpsys activity activities | findstr "com.mavis.inventory_barcode_scanner"
     */
    private String getCurrentActivity(String intentAction) {
        if (intentAction.equals(AppConfig.DW_ACTION_TIRES)) {
            return AppConfig.ACTIVITY_MAIN;
        } else if (intentAction.equals(AppConfig.DW_ACTION_PARTS)) {
            return AppConfig.ACTIVITY_MAIN_PARTS;
        } else if (intentAction.equals(AppConfig.DW_ACTION_CATEGORIES)) {
            return AppConfig.ACTIVITY_PARTS_PC;
        } else if (intentAction.equals(AppConfig.DW_ACTION_BATTERY)) {
            return AppConfig.ACTIVITY_BATTERY_RETURN;
        }else if (intentAction.equals(AppConfig.DW_ACTION_BATTERY_RETURNS)) {
            return AppConfig.ACTIVITY_BATTERY_RETURN;
        } else if (intentAction.equals(AppConfig.DW_ACTION_BATTERY_RECEIVE)) {
            return AppConfig.ACTIVITY_BATTERY_RECEIVE;
        }
        return "";
    }

    /**
     * Splits an "am start ..." command string into individual arguments,
     * stripping the leading "am" (since we pass "am" as the command directly).
     * Handles quoted strings so extras like "STR-1002" stay as single args.
     */
    private java.util.List<String> buildAmArgs(String command) {
        java.util.List<String> args = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"([^\"]*)\"|'([^']*)'|(\\S+)").matcher(command);
        while (m.find()) {
            if (m.group(1) != null) args.add(m.group(1));
            else if (m.group(2) != null) args.add(m.group(2));
            else args.add(m.group(3));
        }
        // Remove leading "am" since it's now the command itself
        if (!args.isEmpty() && args.get(0).equals("am")) {
            args.remove(0);
        }
        return args;
    }

    /**
     * Execute an ADB shell command.
     * Tries Appium's mobile:shell first (requires --allow-insecure=adb_shell).
     * Falls back to direct adb command via ProcessBuilder if mobile:shell is blocked.
     */
    private void executeAdbShell(String command) {
        try {
            // Primary: use Appium's mobile:shell (fast, in-process)
            Map<String, Object> args = new HashMap<>();
            args.put("command", "am");
            args.put("args", buildAmArgs(command));
            driver.executeScript("mobile: shell", args);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("adb_shell")) {
                // Fallback: run adb directly from the host machine
                executeAdbDirect(command);
            } else {
                throw new RuntimeException("Failed to execute ADB shell command", e);
            }
        }
    }

    /**
     * Fallback: execute ADB command directly via host machine's adb binary.
     * Works without --allow-insecure=adb_shell on Appium.
     */
    private void executeAdbDirect(String command) {
        try {
            String adbPath = AppConfig.ADB_PATH;

            ProcessBuilder pb = new ProcessBuilder(
                    adbPath, "-s", AppConfig.getDeviceUDID(), "shell", command
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[ADB] " + line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("[ADB] Command exited with code: " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute ADB command directly: " + command, e);
        }
    }

    /**
     * Simulate scan using simple ADB broadcast (alternative approach).
     * Use this if the startActivity approach doesn't trigger OnNewIntent.
     */
    public void simulateScanViaBroadcast(String barcodeData, String intentAction) {
        String command = String.format(
                "am broadcast -a %s --es \"%s\" \"%s\" --es \"%s\" \"LABEL-TYPE-CODE128\"",
                intentAction,
                AppConfig.DW_KEY_DATA, barcodeData,
                AppConfig.DW_KEY_LABEL_TYPE
        );
        executeAdbShell(command);
    }
}
