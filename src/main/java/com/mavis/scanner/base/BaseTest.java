package com.mavis.scanner.base;

import com.mavis.scanner.config.AppConfig;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Base test class for all Appium scanner tests.
 * Manages driver lifecycle, reporting, and common utilities.
 */
public abstract class BaseTest {

    protected AndroidDriver driver;
    protected WebDriverWait wait;

    private String testName;
    private LocalDateTime startTime;
    private final List<String> steps = new ArrayList<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    protected void setup(String testName) {
        this.testName = testName;
        this.startTime = LocalDateTime.now();
        steps.clear();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST: " + testName);
        System.out.println("START: " + startTime.format(TIME_FMT));
        System.out.println("=".repeat(70));

        try {
            UiAutomator2Options options = new UiAutomator2Options();
            options.setPlatformName(AppConfig.PLATFORM_NAME);
            options.setAutomationName(AppConfig.AUTOMATION_NAME);
            options.setUdid(AppConfig.DEVICE_UDID);
            options.setAppPackage(AppConfig.APP_PACKAGE);
            options.setAppActivity(AppConfig.APP_ACTIVITY);
            options.setNoReset(false);
            options.setFullReset(false);
            options.setNewCommandTimeout(Duration.ofSeconds(300));
            options.setCapability("autoGrantPermissions", true);
            options.setCapability("skipDeviceInitialization", false);
            options.setCapability("skipServerInstallation", false);

            driver = new AndroidDriver(new URL(AppConfig.APPIUM_URL), options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(AppConfig.DEFAULT_TIMEOUT));

            logStep("Driver initialized, app launched");

        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid Appium URL: " + AppConfig.APPIUM_URL, e);
        }
    }

    protected void teardown() {
        if (driver != null) {
            try {
                driver.quit();
                logStep("Driver quit successfully");
            } catch (Exception e) {
                System.err.println("Error quitting driver: " + e.getMessage());
            }
        }
        printSummary();
    }

    protected void logStep(String step) {
        String timestamp = LocalDateTime.now().format(TIME_FMT);
        String entry = "[" + timestamp + "] " + step;
        steps.add(entry);
        System.out.println("  >> " + entry);
    }

    protected void pass() {
        logStep("PASSED");
    }

    protected void fail(String message, Exception e) {
        logStep("FAILED: " + message);
        if (e != null) {
            System.err.println("Exception: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        Assert.fail(message, e);
    }

    protected void fail(String message) {
        fail(message, null);
    }

    protected void skip(String reason) {
        logStep("SKIPPED: " + reason);
        throw new org.testng.SkipException(reason);
    }

    private void printSummary() {
        LocalDateTime endTime = LocalDateTime.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();

        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST SUMMARY: " + testName);
        System.out.println("Duration: " + durationMs + "ms");
        System.out.println("Steps (" + steps.size() + "):");
        for (String step : steps) {
            System.out.println("  " + step);
        }
        System.out.println("-".repeat(70) + "\n");
    }
}
