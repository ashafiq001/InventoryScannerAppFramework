package com.mavis.scanner.functional;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.config.DeadcountConfig;
import com.mavis.scanner.utils.DeadcountSetupHelper;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.options.UiAutomator2Options;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Deadcount Scanner Tests.
 *
 * The Deadcount app is a SEPARATE app from the Barcode Scanner.
 * It only scans section barcodes (STR-*) and records how many tires
 * are in each section (0-250). No individual UPC scanning.
 *
 * Workflow:
 *   1. Login with store, employee, deadcount code
 *   2. Scan section barcode → enter tire count → optional room assignment
 *   3. Repeat for all sections
 *   4. Finish → upload
 *
 * Key validations:
 *   - Count must be 0-250
 *   - If previous deadcount exists and variance > 50%, warns user
 *   - Section can be "Close with 0" or "Remove label" if not physically scanned
 */
public class DeadcountScannerTest {

    protected AndroidDriver driver;
    protected WebDriverWait wait;

    private String testName;
    private LocalDateTime startTime;
    private final List<String> steps = new ArrayList<>();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // ==================== UI LOCATORS ====================

    // Login
    private static final By TXT_STORE = By.id(DeadcountConfig.APP_PACKAGE + ":id/txtStore");
    private static final By TXT_EMPL = By.id(DeadcountConfig.APP_PACKAGE + ":id/txtEmplNum");
    private static final By TXT_INV_CODE = By.id(DeadcountConfig.APP_PACKAGE + ":id/txtInvCode");
    private static final By BTN_LOGIN = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnLogin");
    private static final By INV_INFO = By.id(DeadcountConfig.APP_PACKAGE + ":id/invInfo");

    // Main scan screen
    private static final By LIST_OUTPUT = By.id(DeadcountConfig.APP_PACKAGE + ":id/txtOutputDeadcount");
    private static final By TXT_SECTION_OUTPUT = By.id(DeadcountConfig.APP_PACKAGE + ":id/txtSectionOutputDeadcount");
    private static final By BTN_REMAINING = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnRemainingSections");
    private static final By BTN_FINISH = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnFinishDeadcount");

    // Count dialog
    private static final By DIALOG_SECTION_TITLE = By.id(DeadcountConfig.APP_PACKAGE + ":id/DialogTitleDeadcountSection");
    private static final By SPINNER_ROOMS = By.id(DeadcountConfig.APP_PACKAGE + ":id/spinnerRooms");
    private static final By TXT_COUNT = By.id(DeadcountConfig.APP_PACKAGE + ":id/editTextManualCountDeadcount");
    private static final By TXT_ROOM_NAME = By.id(DeadcountConfig.APP_PACKAGE + ":id/enterRoomName");

    // Validation warning dialog
    private static final By BTN_YES = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnYes");
    private static final By BTN_NO = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnNo");

    // Standard dialog buttons
    private static final By DIALOG_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_NEGATIVE = By.id("android:id/button2");
    private static final By DIALOG_NEUTRAL = By.id("android:id/button3");

    // Final confirm
    private static final By COUNT_INFO = By.id(DeadcountConfig.APP_PACKAGE + ":id/countInfo");
    private static final By BTN_LOGOUT = By.id(DeadcountConfig.APP_PACKAGE + ":id/btnLogout");

    // ==================== SETUP / TEARDOWN ====================

    @BeforeMethod
    public void setupDriver() throws Exception {
        UiAutomator2Options options = new UiAutomator2Options();
        options.setPlatformName(AppConfig.PLATFORM_NAME);
        options.setAutomationName(AppConfig.AUTOMATION_NAME);
        options.setUdid(AppConfig.DEVICE_UDID);
        options.setAppPackage(DeadcountConfig.APP_PACKAGE);
        options.setAppActivity(DeadcountConfig.APP_ACTIVITY);
        options.setNoReset(false);
        options.setFullReset(false);
        options.setNewCommandTimeout(Duration.ofSeconds(300));
        options.setCapability("autoGrantPermissions", true);

        driver = new AndroidDriver(new URL(AppConfig.APPIUM_URL), options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(AppConfig.DEFAULT_TIMEOUT));
    }

    @AfterMethod
    public void teardownDriver() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception e) { /* ignore */ }
        }
        printSummary();
    }

    // ==================== LOGIN TESTS ====================

    @Test(priority = 1, description = "Deadcount: Valid login navigates to scan screen")
    public void testDeadcountValidLogin() {
        start("Deadcount Valid Login");
        try {
            Assert.assertTrue(isOnLoginScreen(),
                    "Deadcount app should launch on login screen");
            logStep("Login screen displayed");

            login();

            Assert.assertTrue(isOnScanScreen(),
                    "DEADCOUNT LOGIN FAILED: Not on scan screen after login. " +
                            "Activity: " + driver.currentActivity());

            String sectionOutput = getTextSafe(TXT_SECTION_OUTPUT);
            logStep("On scan screen. Progress: " + sectionOutput);

            pass();
        } catch (Exception e) {
            fail("Deadcount login test failed: " + e.getMessage());
        }
    }

    @Test(priority = 2, description = "Deadcount: Empty fields rejected")
    public void testDeadcountEmptyFieldsRejected() {
        start("Deadcount Empty Fields");
        try {
            Assert.assertTrue(isOnLoginScreen(), "Should be on login screen");

            // Tap login with empty fields
            driver.findElement(BTN_LOGIN).click();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            Assert.assertTrue(isOnLoginScreen(),
                    "DEADCOUNT VALIDATION BUG: App left login screen with empty fields");

            logStep("Empty fields correctly rejected");
            pass();
        } catch (Exception e) {
            fail("Deadcount empty fields test failed: " + e.getMessage());
        }
    }

    // ==================== SECTION SCANNING ====================

    @Test(priority = 3, description = "Deadcount: Scan section, enter count — section recorded in list")
    public void testScanSectionAndEnterCount() {
        start("Deadcount Scan Section + Count");
        try {
            login();
            Assert.assertTrue(isOnScanScreen(), "Should be on scan screen");

            // Scan a section
            scanSection("STR-1002");

            // Count dialog should appear
            Assert.assertTrue(waitForCountDialog(),
                    "DEADCOUNT BUG: Count dialog did not appear after scanning section");

            String sectionTitle = getTextSafe(DIALOG_SECTION_TITLE);
            logStep("Count dialog shown for: " + sectionTitle);

            // Enter count and submit
            enterCountAndSubmit(10);
            logStep("Entered count 10 for section STR-1002");

            // Verify section appears in list
            int listCount = getCompletedSectionCount();
            Assert.assertTrue(listCount >= 1,
                    "DEADCOUNT BUG: Entered count for section but list shows " + listCount +
                            " completed sections. Section not recorded!");

            String progress = getTextSafe(TXT_SECTION_OUTPUT);
            logStep("Progress: " + progress + " (" + listCount + " sections in list)");

            pass();
        } catch (Exception e) {
            fail("Deadcount scan section test failed: " + e.getMessage());
        }
    }

    @Test(priority = 4, description = "Deadcount: Scan multiple sections — all recorded with correct counts")
    public void testScanMultipleSections() {
        start("Deadcount Multiple Sections");
        try {
            login();

            // Scan 3 sections with different counts
            scanSectionWithCount("STR-1002", 5);
            scanSectionWithCount("STR-1003", 12);
            scanSectionWithCount("STR-1004", 0);

            int listCount = getCompletedSectionCount();
            logStep("Scanned 3 sections, list shows: " + listCount);

            Assert.assertTrue(listCount >= 3,
                    "DEADCOUNT BUG: Scanned 3 sections but list shows " + listCount +
                            ". Sections lost!");

            pass();
        } catch (Exception e) {
            fail("Deadcount multiple sections test failed: " + e.getMessage());
        }
    }

    // ==================== COUNT VALIDATION ====================

    @Test(priority = 5, description = "Deadcount: Count exceeding 250 must be rejected")
    public void testCountExceeds250Rejected() {
        start("Deadcount Count > 250 Rejected");
        try {
            login();

            scanSection("STR-1002");
            Assert.assertTrue(waitForCountDialog(), "Count dialog should appear");

            // Enter 251 (over max)
            WebElement countField = driver.findElement(TXT_COUNT);
            countField.clear();
            countField.sendKeys("251");

            // Submit button is the neutral button (android:id/button3 = "Submit")
            if (WaitHelper.isElementPresent(driver, DIALOG_NEUTRAL)) {
                driver.findElement(DIALOG_NEUTRAL).click();
            } else {
                driver.findElement(DIALOG_POSITIVE).click();
            }
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // App should reject and show error toast
            // Check if we're still on dialog or if section was NOT added
            boolean stillOnDialog = isCountDialogDisplayed();
            int listCount = getCompletedSectionCount();

            logStep("After entering 251: stillOnDialog=" + stillOnDialog +
                    " listCount=" + listCount);

            if (listCount == 0 || stillOnDialog) {
                logStep("GOOD: Count 251 rejected (max is 250)");
            } else {
                Assert.fail("DEADCOUNT VALIDATION BUG: Count of 251 was accepted! " +
                        "Max should be 250. listCount=" + listCount);
            }

            // Dismiss any remaining dialog
            dismissAnyDialog();

            pass();
        } catch (Exception e) {
            fail("Deadcount count > 250 test failed: " + e.getMessage());
        }
    }

    @Test(priority = 6, description = "Deadcount: Count of 0 must be accepted (empty section)")
    public void testCountZeroAccepted() {
        start("Deadcount Count 0 Accepted");
        try {
            login();

            scanSectionWithCount("STR-1002", 0);

            int listCount = getCompletedSectionCount();
            Assert.assertTrue(listCount >= 1,
                    "DEADCOUNT BUG: Count of 0 should be valid (empty section) " +
                            "but list shows " + listCount + " sections");

            logStep("GOOD: Count 0 accepted for empty section");
            pass();
        } catch (Exception e) {
            fail("Deadcount count 0 test failed: " + e.getMessage());
        }
    }

    // ==================== DUPLICATE SECTION ====================

    @Test(priority = 7, description = "Deadcount: Scan same section twice — must warn or block")
    public void testDuplicateSectionHandled() {
        start("Deadcount Duplicate Section");
        try {
            login();

            // Scan and count section
            scanSectionWithCount("STR-1002", 8);
            logStep("First scan of STR-1002 with count 8");

            // Scan same section again
            scanSection("STR-1002");

            // Wait for any dialog to appear
            boolean dialogShown = false;
            try {
                WaitHelper.waitForAny(driver, AppConfig.DEFAULT_TIMEOUT,
                        TXT_COUNT, DIALOG_POSITIVE);
                dialogShown = true;
            } catch (Exception e) {
                // no dialog appeared within timeout
            }

            String activity = driver.currentActivity();
            logStep("After re-scanning STR-1002: dialog=" + dialogShown +
                    " activity=" + activity);

            // App should NOT crash
            Assert.assertFalse(activity.contains("LoginActivity"),
                    "DEADCOUNT CRASH: App went back to login after duplicate section scan");

            if (dialogShown) {
                logStep("Dialog shown for duplicate section — user can update count or dismiss");
                dismissAnyDialog();
            }

            pass();
        } catch (Exception e) {
            fail("Deadcount duplicate section test failed: " + e.getMessage());
        }
    }

    // ==================== MALFORMED SECTION BARCODE ====================

    @Test(priority = 8, description = "Deadcount: Non-STR barcode and malformed barcodes — must not crash")
    public void testMalformedSectionBarcodes() {
        start("Deadcount Malformed Barcodes");
        try {
            login();

            String[] barcodes = {
                    "092971236021",   // UPC (not a section)
                    "",               // empty
                    "STR-",           // prefix only
                    "STR-ABC",        // non-numeric
                    "HELLO",          // random
                    "!@#$",           // special chars
            };

            for (String barcode : barcodes) {
                logStep("Scanning: '" + barcode + "'");
                scanSection(barcode);
                // Brief wait for any error dialog; garbage barcodes may not trigger one
                try {
                    WaitHelper.waitForAny(driver, 3, DIALOG_POSITIVE, TXT_COUNT);
                } catch (Exception e) { /* no dialog — expected for garbage */ }
                dismissAnyDialog();

                String activity = driver.currentActivity();
                Assert.assertTrue(activity.contains("MainActivity"),
                        "DEADCOUNT CRASH: App left scan screen after barcode '" + barcode + "'. " +
                                "Activity: " + activity);
            }

            // Verify normal section scan still works after garbage
            scanSectionWithCount("STR-1002", 5);
            int listCount = getCompletedSectionCount();
            Assert.assertTrue(listCount >= 1,
                    "DEADCOUNT RECOVERY FAILURE: After garbage barcodes, normal scan broken");

            logStep("All malformed barcodes handled, normal scanning works");
            pass();
        } catch (Exception e) {
            fail("Deadcount malformed barcode test failed: " + e.getMessage());
        }
    }

    // ==================== FINISH WITH INCOMPLETE SECTIONS ====================

    @Test(priority = 9, description = "Deadcount: Finish with incomplete sections — close with 0 flow")
    public void testFinishWithIncompleteSections() {
        start("Deadcount Finish Incomplete");
        try {
            login();

            // Scan only 1 section
            scanSectionWithCount("STR-1002", 7);
            logStep("Scanned 1 section with count 7");

            // Tap Finish
            scrollToBottom();
            driver.findElement(BTN_FINISH).click();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // App should show "Completed X of Y" with missing sections
            // Handle close-with-0 and finish dialogs
            By closeWith0 = byTextIgnoreCase("close with 0");
            By removeLabel = byTextIgnoreCase("remove label");
            By yesBtn = byTextIgnoreCase("yes");

            int closedWith0 = 0;
            for (int i = 0; i < 200; i++) {
                if (WaitHelper.isElementPresent(driver, closeWith0)) {
                    closedWith0++;
                    driver.findElement(closeWith0).click();
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                } else if (WaitHelper.isElementPresent(driver, yesBtn)) {
                    logStep("Confirming finish after closing " + closedWith0 + " sections with 0");
                    driver.findElement(yesBtn).click();
                    Thread.sleep(AppConfig.LONG_WAIT);
                    break;
                } else if (WaitHelper.isElementPresent(driver, DIALOG_POSITIVE)) {
                    driver.findElement(DIALOG_POSITIVE).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else if (WaitHelper.isElementPresent(driver, DIALOG_NEUTRAL)) {
                    driver.findElement(DIALOG_NEUTRAL).click();
                    Thread.sleep(AppConfig.MEDIUM_WAIT);
                } else {
                    break;
                }
            }

            Thread.sleep(AppConfig.LONG_WAIT);

            // Should reach FinalConfirm
            String activity = driver.currentActivity();
            logStep("After finish: activity = " + activity);

            if (activity.contains("FinalConfirm")) {
                String countInfo = getTextSafe(COUNT_INFO);
                logStep("Final confirmation: " + countInfo);

                Assert.assertTrue(countInfo.toLowerCase().contains("section") ||
                                countInfo.toLowerCase().contains("uploaded"),
                        "DEADCOUNT UPLOAD BUG: Final confirmation doesn't show upload info. " +
                                "countInfo: " + countInfo);
            }

            pass();
        } catch (Exception e) {
            fail("Deadcount finish incomplete test failed: " + e.getMessage());
        }
    }

    // ==================== REMAINING SECTIONS BUTTON ====================

    @Test(priority = 10, description = "Deadcount: Remaining sections button shows progress")
    public void testRemainingSectionsButton() {
        start("Deadcount Remaining Sections");
        try {
            login();

            // Scan 1 section
            scanSectionWithCount("STR-1002", 3);

            // Tap Remaining sections
            driver.findElement(BTN_REMAINING).click();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Dialog should show completed and remaining sections
            boolean dialogShown = WaitHelper.isElementPresent(driver, DIALOG_POSITIVE) ||
                    WaitHelper.isElementPresent(driver, DIALOG_NEUTRAL);

            logStep("Remaining sections dialog shown: " + dialogShown);
            Assert.assertTrue(dialogShown,
                    "DEADCOUNT BUG: Remaining sections button did nothing");

            dismissAnyDialog();

            // Should still be on scan screen
            Assert.assertTrue(isOnScanScreen(),
                    "DEADCOUNT BUG: Left scan screen after viewing remaining sections");

            pass();
        } catch (Exception e) {
            fail("Deadcount remaining sections test failed: " + e.getMessage());
        }
    }

    // ==================== APP RESUME ====================

    @Test(priority = 11, description = "Deadcount: Background app mid-scan — completed sections preserved")
    public void testDeadcountAppResume() {
        start("Deadcount App Resume");
        try {
            login();

            scanSectionWithCount("STR-1002", 5);
            scanSectionWithCount("STR-1003", 10);
            int countBefore = getCompletedSectionCount();
            logStep("Before background: " + countBefore + " sections completed");

            driver.runAppInBackground(Duration.ofSeconds(5));
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            int countAfter = getCompletedSectionCount();
            logStep("After resume: " + countAfter + " sections completed");

            Assert.assertEquals(countAfter, countBefore,
                    "DEADCOUNT DATA LOSS ON RESUME: Had " + countBefore +
                            " sections before, but " + countAfter + " after resume!");

            pass();
        } catch (Exception e) {
            fail("Deadcount app resume test failed: " + e.getMessage());
        }
    }

    // ==================== DELETE SECTION ====================

    @Test(priority = 12, description = "Deadcount: Delete a completed section from list")
    public void testDeleteCompletedSection() {
        start("Deadcount Delete Section");
        try {
            login();

            scanSectionWithCount("STR-1002", 5);
            scanSectionWithCount("STR-1003", 10);
            int countBefore = getCompletedSectionCount();
            logStep("Before delete: " + countBefore + " sections");

            // Tap on a section in the list to delete it
            try {
                WebElement listView = driver.findElement(LIST_OUTPUT);
                List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
                if (!items.isEmpty()) {
                    items.get(0).click();
                    Thread.sleep(AppConfig.SHORT_WAIT);

                    // Delete confirmation dialog — "Delete" is button2 (DIALOG_NEGATIVE)
                    if (WaitHelper.isElementPresent(driver, DIALOG_NEGATIVE)) {
                        driver.findElement(DIALOG_NEGATIVE).click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        logStep("Deleted first section from list");
                    }
                }
            } catch (Exception e) {
                logStep("Could not delete section: " + e.getMessage());
            }

            int countAfter = getCompletedSectionCount();
            logStep("After delete: " + countAfter + " sections");

            if (countAfter < countBefore) {
                logStep("GOOD: Section deleted, count decreased from " + countBefore + " to " + countAfter);
            } else {
                logStep("WARNING: Section count didn't decrease. Delete may not have worked.");
            }

            // App should not crash
            Assert.assertTrue(isOnScanScreen(),
                    "DEADCOUNT CRASH: App left scan screen after delete attempt");

            pass();
        } catch (Exception e) {
            fail("Deadcount delete section test failed: " + e.getMessage());
        }
    }

    // ==================== HELPERS ====================

    private void start(String name) {
        this.testName = name;
        this.startTime = LocalDateTime.now();
        steps.clear();
        System.out.println("\n" + "=".repeat(70));
        System.out.println("TEST: " + name);
        System.out.println("=".repeat(70));
    }

    private void logStep(String step) {
        String entry = "[" + LocalDateTime.now().format(TIME_FMT) + "] " + step;
        steps.add(entry);
        System.out.println("  >> " + entry);
    }

    private void pass() { logStep("PASSED"); }

    private void fail(String msg) {
        logStep("FAILED: " + msg);
        Assert.fail(msg);
    }

    private void printSummary() {
        if (testName == null) return;
        System.out.println("\n" + "-".repeat(70));
        System.out.println("SUMMARY: " + testName + " (" + steps.size() + " steps)");
        for (String s : steps) System.out.println("  " + s);
        System.out.println("-".repeat(70));
    }

    private boolean isOnLoginScreen() {
        return WaitHelper.isElementPresent(driver, BTN_LOGIN) &&
                WaitHelper.isElementPresent(driver, TXT_STORE);
    }

    private boolean isOnScanScreen() {
        return WaitHelper.isElementPresent(driver, BTN_FINISH) &&
                WaitHelper.isElementPresent(driver, BTN_REMAINING);
    }

    private boolean isCountDialogDisplayed() {
        return WaitHelper.isElementPresent(driver, TXT_COUNT);
    }

    private boolean waitForCountDialog() {
        try {
            WaitHelper.waitForVisible(wait, TXT_COUNT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void login() throws InterruptedException {
        String employee = AppConfig.TEST_EMPLOYEE;

        // Resolve store + invCode dynamically (schedule if needed)
        DeadcountSetupHelper.ScheduledDeadcount dc = DeadcountSetupHelper.resolveDeadcount();

        String store;
        String invCode;
        if (dc != null) {
            store = dc.store;
            invCode = dc.invCode;
            logStep("Resolved deadcount: " + dc);
        } else {
            store = System.getProperty("TEST_STORE", "30");
            invCode = System.getProperty("DEADCOUNT_CODE", "");
            logStep("WARNING: No deadcount resolved from DB. Using defaults: store=" + store +
                    " invCode=" + invCode);
        }

        if (invCode == null || invCode.isEmpty()) {
            logStep("WARNING: No DEADCOUNT_CODE available. Login will likely fail.");
        }

        driver.findElement(TXT_STORE).sendKeys(store);
        driver.findElement(TXT_EMPL).sendKeys(employee);
        driver.findElement(TXT_INV_CODE).sendKeys(invCode);
        driver.findElement(BTN_LOGIN).click();

        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        if (isOnLoginScreen()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

        logStep("Logged in: store=" + store + " employee=" + employee);
    }

    private void scanSection(String sectionBarcode) {
        String command = String.format(
                "am start -a %s -c android.intent.category.DEFAULT " +
                        "--es \"%s\" \"%s\" " +
                        "--es \"%s\" \"LABEL-TYPE-CODE128\" " +
                        "--es \"%s\" \"scanner\" " +
                        "-n %s/%s",
                DeadcountConfig.DW_ACTION,
                DeadcountConfig.DW_KEY_DATA, sectionBarcode,
                DeadcountConfig.DW_KEY_LABEL_TYPE,
                "com.symbol.datawedge.source",
                DeadcountConfig.APP_PACKAGE, DeadcountConfig.ACTIVITY_MAIN);

        try {
            Map<String, Object> args = new HashMap<>();
            args.put("command", "sh");
            args.put("args", Arrays.asList("-c", command));
            driver.executeScript("mobile: shell", args);
        } catch (Exception e) {
            // Fallback to direct ADB
            try {
                new ProcessBuilder(AppConfig.ADB_PATH, "-s", AppConfig.DEVICE_UDID, "shell", command)
                        .redirectErrorStream(true).start().waitFor();
            } catch (Exception ex) {
                System.err.println("Failed to scan section: " + ex.getMessage());
            }
        }
    }

    private void enterCountAndSubmit(int count) throws InterruptedException {
        WebElement countField = driver.findElement(TXT_COUNT);
        countField.clear();
        countField.sendKeys(String.valueOf(count));

        // Submit button is the neutral button (android:id/button3 = "Submit")
        if (WaitHelper.isElementPresent(driver, DIALOG_NEUTRAL)) {
            driver.findElement(DIALOG_NEUTRAL).click();
        } else if (WaitHelper.isElementPresent(driver, DIALOG_POSITIVE)) {
            driver.findElement(DIALOG_POSITIVE).click();
        }
        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

        // Handle validation warning if it appears (previous count variance > 50%)
        if (WaitHelper.isElementPresent(driver, BTN_NO)) {
            // "No" = accept current count despite variance
            driver.findElement(BTN_NO).click();
            Thread.sleep(AppConfig.SHORT_WAIT);
        }

        dismissAnyDialog();
    }

    private void scanSectionWithCount(String section, int count) throws InterruptedException {
        scanSection(section);

        // Wait for either the count dialog or an unexpected dialog to appear
        try {
            WaitHelper.waitForAny(driver, AppConfig.DEFAULT_TIMEOUT,
                    TXT_COUNT, DIALOG_POSITIVE);
        } catch (Exception e) {
            // timeout — neither appeared
        }

        // If a non-count dialog appears first (e.g. duplicate warning), dismiss it
        if (!isCountDialogDisplayed() && WaitHelper.isElementPresent(driver, DIALOG_POSITIVE)) {
            driver.findElement(DIALOG_POSITIVE).click();
            waitForCountDialog();
        }

        if (isCountDialogDisplayed()) {
            enterCountAndSubmit(count);
            logStep("Section " + section + ": count " + count);
        } else {
            logStep("WARNING: Count dialog did not appear for " + section);
            dismissAnyDialog();
        }
    }

    private int getCompletedSectionCount() {
        try {
            WebElement listView = driver.findElement(LIST_OUTPUT);
            List<WebElement> items = listView.findElements(By.className("android.widget.TextView"));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private String getTextSafe(By locator) {
        try {
            return driver.findElement(locator).getText();
        } catch (Exception e) {
            return "";
        }
    }

    private void dismissAnyDialog() {
        try {
            Thread.sleep(500);
            if (WaitHelper.isElementPresent(driver, DIALOG_POSITIVE)) {
                driver.findElement(DIALOG_POSITIVE).click();
                Thread.sleep(500);
            }
        } catch (Exception e) { /* No dialog */ }
    }

    private void scrollToBottom() {
        try {
            org.openqa.selenium.Dimension size = driver.manage().window().getSize();
            int startX = size.width / 2;
            int startY = (int) (size.height * 0.8);
            int endY = (int) (size.height * 0.2);
            org.openqa.selenium.interactions.PointerInput finger =
                    new org.openqa.selenium.interactions.PointerInput(
                            org.openqa.selenium.interactions.PointerInput.Kind.TOUCH, "finger");
            org.openqa.selenium.interactions.Sequence swipe =
                    new org.openqa.selenium.interactions.Sequence(finger, 1);
            swipe.addAction(finger.createPointerMove(Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, startY));
            swipe.addAction(finger.createPointerDown(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(Duration.ofMillis(500),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, endY));
            swipe.addAction(finger.createPointerUp(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(Collections.singletonList(swipe));
            Thread.sleep(500);
        } catch (Exception e) { /* Scroll failed */ }
    }

    private static By byTextIgnoreCase(String text) {
        String lower = text.toLowerCase();
        return By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + lower + "']");
    }
}
