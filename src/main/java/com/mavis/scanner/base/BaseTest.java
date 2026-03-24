package com.mavis.scanner.base;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.EmailHelper;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    //Extent Report
    private static ExtentReports extent;;
    private static String screenshotDir;
    protected ExtentTest extentTest;

    //Appium
    private static Process appiumProcess;

    private static String reportDir;


    @BeforeSuite(alwaysRun = true)
    public void startAppiumServer() throws Exception {
        System.out.println("[BaseTest] Starting Appium server...");

        ProcessBuilder pb = new ProcessBuilder("C:\\Users\\ashafiq\\AppData\\Roaming\\npm\\appium.cmd",
                "--address", "127.0.0.1", "--port", "4723","--allow-insecure", "adb_shell");
        pb.redirectErrorStream(true);
        // Send output to a log file so it doesn't flood the console
        pb.redirectOutput(new File("test-reports/appium-server.log"));
        appiumProcess = pb.start();

        // Wait for Appium to be ready (poll the status endpoint)
        waitForAppiumReady("http://127.0.0.1:4723/status", 30);

        System.out.println("[BaseTest] Appium server is running on port 4723");
    }

    private void waitForAppiumReady(String statusUrl, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new URL(statusUrl).openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                if (conn.getResponseCode() == 200) return;
            } catch (Exception ignored) {
                // Server not ready yet
            }
            Thread.sleep(1000);
        }
        throw new RuntimeException("Appium server did not start within " + timeoutSeconds + "s");
    }



    @BeforeSuite(alwaysRun = true)
    public void initExtentReport() {
        String timestamp = LocalDateTime.now().format(FILE_FMT);
          reportDir = "test-reports";
        screenshotDir = reportDir + "/screenshots";

        new File(reportDir).mkdirs();
        new File(screenshotDir).mkdirs();


        String reportPath = reportDir + "/InventoryScanner" + timestamp + ".html";


        ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setDocumentTitle("Mavis Inventory Scanner App - Test Report");
        spark.config().setReportName("Appium Test Suite Results");
        spark.config().setTimeStampFormat("yyyy-MM-dd HH:mm:ss");


        extent = new ExtentReports();
        extent.attachReporter(spark);
        extent.setSystemInfo("Platform", "Android");
        extent.setSystemInfo("App", AppConfig.APP_PACKAGE);
        extent.setSystemInfo("Device", AppConfig.DEVICE_UDID);
        extent.setSystemInfo("Appium URL", AppConfig.APPIUM_URL);

        ExtentSparkReporter latest = new ExtentSparkReporter(reportDir + "/latest-report.html");
        latest.config().setTheme(Theme.STANDARD);
        latest.config().setDocumentTitle("Mavis Inventory Scanner - Test Report");
        latest.config().setReportName("Appium Test Suite Results (Latest)");
        extent.attachReporter(latest);

        System.out.println("[ExtentReport] Report: " + reportPath);

    }

    @AfterSuite(alwaysRun = true)
    public void flushExtentReport() {
        if (extent != null) {
            extent.flush();
            System.out.println("[ExtentReport] Report flushed to disk.");
        }
    }

    @AfterSuite(alwaysRun = true)
    public void stopAppiumServer() {
        if (appiumProcess != null && appiumProcess.isAlive()) {
            System.out.println("[BaseTest] Stopping Appium server...");
            appiumProcess.destroy();
            try {
                // Give it 5s to shut down gracefully
                if (!appiumProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    appiumProcess.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                appiumProcess.destroyForcibly();
            }
            System.out.println("[BaseTest] Appium server stopped.");
        }
    }



    /**
     * Bridge TestNG &lt;parameter&gt; tags from testng.xml into System properties
     * so InventorySetupHelper.resolveInventory() picks them up.
     * Runs once before the entire suite. Always schedules a fresh inventory.
     */
    @BeforeTest(alwaysRun = true)
    public void loadTestParameters(ITestContext context) {
        String[] keys = {"TEST_STORE", "SCHEDULE_IF_NEEDED", "INVENTORY_PCS"};
        for (String key : keys) {
            String value = context.getCurrentXmlTest().getParameter(key);
            if (value == null || value.isEmpty()) {
                // Fall back to suite-level parameter
                value = context.getSuite().getParameter(key);
            }
            if (value != null && !value.isEmpty()) {
                System.setProperty(key, value);
                System.out.println("[BaseTest] Set " + key + "=" + value +
                        " (test: " + context.getName() + ")");
            }
        }
    }

    protected void setup(String testName) {
        this.testName = testName;
        this.startTime = LocalDateTime.now();
        steps.clear();

        extentTest = extent.createTest(testName);
        extentTest.info("Test started at " + startTime.format(TIME_FMT));

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

    /**
     * Attach to an already-running app session without restarting.
     * Use this when the app is already open and logged in.
     */
    protected void attach(String testName) {
        this.testName = testName;
        this.startTime = LocalDateTime.now();
        steps.clear();

        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST (attach): " + testName);
        System.out.println("START: " + startTime.format(TIME_FMT));
        System.out.println("=".repeat(70));

        try {
            UiAutomator2Options options = new UiAutomator2Options();
            options.setPlatformName(AppConfig.PLATFORM_NAME);
            options.setAutomationName(AppConfig.AUTOMATION_NAME);
            options.setUdid(AppConfig.DEVICE_UDID);
            options.setAppPackage(AppConfig.APP_PACKAGE);
            options.setNoReset(true);
            options.setCapability("autoLaunch", false);
            options.setCapability("skipDeviceInitialization", true);
            options.setCapability("skipServerInstallation", true);
            options.setNewCommandTimeout(Duration.ofSeconds(300));

            driver = new AndroidDriver(new URL(AppConfig.APPIUM_URL), options);
            wait = new WebDriverWait(driver, Duration.ofSeconds(AppConfig.DEFAULT_TIMEOUT));

            logStep("Attached to running app. Activity: " + driver.currentActivity());

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

        if (extentTest != null) {
            if (step.startsWith("FAILED")) {
                extentTest.fail(step);
            } else if (step.startsWith("SKIPPED")) {
                extentTest.skip(step);
            } else if (step.equals("PASSED")) {
                extentTest.pass(step);
            } else {
                extentTest.info(step);
            }
        }
    }


    protected void captureScreenshot(String name) {
        if (driver == null) return;
        try {
            String safeName = name.replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = safeName + "_" + LocalDateTime.now().format(FILE_FMT) + ".png";
            String filePath = screenshotDir + "/" + fileName;

            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(srcFile.toPath(), Path.of(filePath), StandardCopyOption.REPLACE_EXISTING);

            String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(Path.of(filePath)));
            if (extentTest != null) {
                extentTest.info("Screenshot: " + fileName,
                        MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
            }
            System.out.println("  >> Screenshot saved: " + filePath);
        } catch (Exception e) {
            System.err.println("Failed to capture screenshot: " + e.getMessage());
        }
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
        captureScreenshot("FAIL_" + testName);

        if (e != null && extentTest != null) {
            extentTest.fail(e);
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

        if (extentTest != null) {
            extentTest.info("Completed in " + durationMs + "ms (" + steps.size() + " steps)");
        }

    }
}
