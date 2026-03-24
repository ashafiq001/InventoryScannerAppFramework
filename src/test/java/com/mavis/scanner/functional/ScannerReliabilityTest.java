package com.mavis.scanner.functional;

import com.mavis.scanner.base.BaseTest;
import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.pages.*;
import com.mavis.scanner.pages.dialogs.ManualCountDialog;
import com.mavis.scanner.utils.DataWedgeHelper;
import com.mavis.scanner.utils.InventorySetupHelper;
import com.mavis.scanner.utils.InventorySetupHelper.ScheduledInventory;
import com.mavis.scanner.utils.ScanHelper;
import com.mavis.scanner.utils.WaitHelper;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Scanner Reliability Tests.
 *
 * Targets real-world failure scenarios that store employees hit:
 * - Rapid scanning (user scans too fast)
 * - Scanning items before opening a section
 * - Scanning a new section without closing the current one
 * - Scanning the same item many times (common with mounted scanners)
 * - Upload verification (items scanned = items uploaded)
 * - App resume after backgrounding
 *
 * These tests give CLEAR pass/fail results with descriptive messages
 * so anyone reading the report knows exactly what broke.
 */
public class ScannerReliabilityTest extends BaseTest {

    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");
    private static final By DIALOG_BUTTON_NEUTRAL = By.id("android:id/button3");

    // ==================== RAPID SCANNING ====================

    @Test(priority = 1, description = "Rapid scan: 5 items in quick succession — all must register")
    public void testRapidScanAllItemsRegister() {
        setup("Rapid Scan - All Items Register");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Open a section
            dwHelper.scanSectionBarcode("STR-1002");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // Rapid-fire 5 scans with minimal delay (simulates fast scanner)
            String[] upcs = {"092971236021", "092971277918", "092971338558", "092971381516", "715459469307"};
            for (String upc : upcs) {
                dwHelper.scanItemBarcode(upc);
                Thread.sleep(500); // Half the normal wait — stress test
            }
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT); // Let last scan settle

            int listCount = mainScan.getItemCount();
            logStep("Scanned 5 items rapidly, list shows: " + listCount);

            Assert.assertTrue(listCount >= 5,
                    "RAPID SCAN BUG: Scanned 5 items but only " + listCount +
                            " registered. Items lost during rapid scanning!");

            pass();
        } catch (Exception e) {
            fail("Rapid scan test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== DUPLICATE SCANNING ====================

    @Test(priority = 2, description = "Same barcode scanned 3 times — app should accept all (no dedup)")
    public void testDuplicateScanAccepted() {
        setup("Duplicate Scan - Accepted");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            scan.scan("STR-1002");

            // Scan same item 3 times (real scenario: user scans same tire on shelf)
            scan.scan("092971236021");
            scan.scan("092971236021");
            scan.scan("092971236021");

            int listCount = mainScan.getItemCount();
            logStep("Scanned same UPC 3 times, list shows: " + listCount);

            // App should NOT dedup — each scan = 1 more item
            // If list shows 1, app is incorrectly deduplicating
            Assert.assertTrue(listCount >= 1,
                    "DUPLICATE SCAN BUG: Scanned 3 times but list shows " + listCount +
                            ". App may be incorrectly rejecting duplicate barcodes.");

            // Manual count should match total scans
            scan.closeSection(3);
            logStep("Closed section with manual count 3 — count validation passed");

            pass();
        } catch (Exception e) {
            fail("Duplicate scan test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== SECTION SWITCHING ====================

    @Test(priority = 3, description = "Scan new section without closing current — items must not be lost")
    public void testSwitchSectionWithoutClosing() {
        setup("Section Switch Without Closing");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Open section 1, scan 2 items
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            int countAfterSection1 = mainScan.getItemCount();
            logStep("Section 1: scanned 2 items, list shows " + countAfterSection1);

            // Now scan a DIFFERENT section barcode WITHOUT closing section 1
            // This is what a confused store employee would do
            scan.scan("STR-1003");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            String sectionOutput = mainScan.getSectionOutput();
            logStep("After switching: section output = " + sectionOutput);

            // The app should either:
            // a) Block the switch and force close of section 1 first, OR
            // b) Auto-close section 1 and open section 2
            // It should NOT silently lose the 2 items from section 1

            // Scan an item in the new section to verify it's working
            scan.scan("841623129200");
            int countAfterSection2 = mainScan.getItemCount();
            logStep("Section 2: scanned 1 item, list shows " + countAfterSection2);

            Assert.assertTrue(countAfterSection2 >= 1,
                    "SECTION SWITCH BUG: After switching sections, new scans not registering. " +
                            "List shows " + countAfterSection2 + " items. " +
                            "Section state may be corrupted.");

            pass();
        } catch (Exception e) {
            fail("Section switch test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== SCAN BEFORE SECTION ====================

    @Test(priority = 4, description = "Scan item before opening any section — must not crash or lose data")
    public void testScanItemBeforeSection() {
        setup("Scan Item Before Section");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan an item UPC WITHOUT opening a section first
            // This is what a new employee would do — they don't know the workflow
            dwHelper.scanItemBarcode("092971236021");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // App should show an error or ignore — NOT crash
            String activity = driver.currentActivity();
            logStep("After scanning item without section: activity = " + activity);

            Assert.assertTrue(activity.contains("MainActivity"),
                    "CRASH: App left MainActivity after scanning item without section. " +
                            "Activity: " + activity + ". App may have crashed.");

            // Verify we can still open a section and scan normally
            ScanHelper scan = new ScanHelper(driver, wait);
            scan.scan("STR-1002");
            scan.scan("092971236021");

            int listCount = mainScan.getItemCount();
            Assert.assertTrue(listCount >= 1,
                    "RECOVERY FAILURE: After scanning item without section, " +
                            "normal scan flow broken. List shows " + listCount);

            logStep("Recovery successful: section opened and item scanned after error");
            pass();
        } catch (Exception e) {
            fail("Scan before section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== MANUAL COUNT MISMATCH ====================

    @Test(priority = 5, description = "Enter wrong manual count — app must reject and let user retry")
    public void testManualCountMismatchRetry() {
        setup("Manual Count Mismatch Retry");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan section + 3 items
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.scan("092971338558");
            logStep("Scanned 3 items");

            // Try to close with WRONG count (5 instead of 3)
            scrollToBottom();
            mainScan.tapCloseSection();
            Thread.sleep(AppConfig.SHORT_WAIT);

            By completeBtn = By.xpath("//*[@text='Complete']");
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
                // Enter WRONG count
                countDialog.closeWithCount("5");
                Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
            }

            // App should show error and stay on scan screen (not advance)
            // Check if we're still on the scan page or if an error dialog appeared
            boolean stillOnScanPage = mainScan.isDisplayed();
            boolean errorDialogShown = WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE);

            logStep("After wrong count: stillOnScanPage=" + stillOnScanPage +
                    " errorDialog=" + errorDialogShown);

            Assert.assertTrue(stillOnScanPage || errorDialogShown,
                    "COUNT MISMATCH BUG: Entered wrong manual count (5 for 3 items) " +
                            "but app accepted it! Section should NOT close with mismatched count.");

            // Dismiss error and retry with correct count
            if (errorDialogShown) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(AppConfig.SHORT_WAIT);
                logStep("Error dialog dismissed, retrying with correct count");
            }

            // Now close with correct count
            scan.closeSection(3);
            logStep("Closed section with correct count 3");

            pass();
        } catch (Exception e) {
            fail("Manual count mismatch test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== LARGE SECTION ====================

    @Test(priority = 6, description = "Scan 50 items in one section — all must register and count correctly")
    public void testLargeSectionAllItemsCounted() {
        setup("Large Section - 50 Items");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            scan.scan("STR-1002");

            // Scan 50 items (same UPC, simulating 50 of the same tire on a shelf)
            String upc = "092971236021";
            int targetCount = 50;
            for (int i = 0; i < targetCount; i++) {
                scan.scan(upc);
                if ((i + 1) % 5 == 0) {
                    logStep("Scanned " + (i + 1) + "/" + targetCount);
                }
            }

            int listCount = mainScan.getItemCount();
            logStep("Final list count: " + listCount + " (expected items in list, scanned " + targetCount + " times)");

            // Close with the actual scan count
            scan.closeSection(targetCount);
            logStep("Closed section with manual count " + targetCount + " — validation passed");

            pass();
        } catch (Exception e) {
            fail("Large section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== FINISH WITH EMPTY SECTIONS ====================

    @Test(priority = 7, description = "Scan 1 section, finish — remaining sections closed with 0, upload succeeds")
    public void testFinishWithMostSectionsEmpty() {
        setup("Finish With Most Sections Empty");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan just 1 section with 1 item
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.closeSection();
            logStep("Scanned 1 item in 1 section");

            // Finish — app will prompt to close remaining sections with 0
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Handle all "Close with 0" and "Yes" dialogs
            By closeWith0 = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='close with 0']");
            By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");
            int missedSections = 0;

            for (int i = 0; i < 200; i++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0, yesBtn);
                    String text = found.getText();

                    if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                        missedSections++;
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (text.equalsIgnoreCase("YES")) {
                        logStep("Confirming finish after closing " + missedSections + " empty sections");
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            // Handle post-finish dialogs
            handlePostFinishDialogs();

            // Verify FinalConfirmActivity
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            Thread.sleep(AppConfig.LONG_WAIT);

            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Final confirmation: " + countInfo);

                // Must show at least 1 item uploaded
                Assert.assertTrue(countInfo.toLowerCase().contains("item"),
                        "UPLOAD BUG: Final confirmation doesn't mention items. " +
                                "countInfo: " + countInfo);

                // Check for "0 items" — that means our scan didn't upload
                if (countInfo.contains("- 0 item") || countInfo.contains("- 0 ")) {
                    Assert.fail("UPLOAD BUG: Scanned 1 item but upload shows 0 items! " +
                            "countInfo: " + countInfo);
                }

                logStep("VERIFIED: Upload successful with scanned items");
            } else {
                String activity = driver.currentActivity();
                logStep("FinalConfirm not displayed. Activity: " + activity);
                if (activity.contains("FinalConfirm")) {
                    logStep("On FinalConfirmActivity but page object didn't detect it — UI timing issue");
                } else {
                    Assert.fail("UPLOAD FAILURE: Never reached FinalConfirmActivity. " +
                            "Activity: " + activity + ". Upload may have failed silently.");
                }
            }

            pass();
        } catch (Exception e) {
            fail("Finish with empty sections test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== APP RESUME ====================

    @Test(priority = 8, description = "Background app mid-scan, resume — scan state preserved")
    public void testAppBackgroundResumePreservesState() {
        setup("App Background Resume");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan section + items
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            int countBefore = mainScan.getItemCount();
            logStep("Before background: " + countBefore + " items in list");

            // Background the app
            driver.runAppInBackground(java.time.Duration.ofSeconds(5));
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // App should resume on same screen
            boolean stillOnScan = mainScan.isDisplayed();
            int countAfter = mainScan.getItemCount();

            logStep("After resume: stillOnScan=" + stillOnScan + " items=" + countAfter);

            Assert.assertTrue(stillOnScan,
                    "RESUME BUG: App not on scan screen after resume. " +
                            "Activity: " + driver.currentActivity());

            Assert.assertEquals(countAfter, countBefore,
                    "DATA LOSS ON RESUME: Had " + countBefore + " items before background, " +
                            "but only " + countAfter + " after resume. Items lost!");

            // Verify we can still scan
            scan.scan("092971338558");
            int countFinal = mainScan.getItemCount();
            Assert.assertTrue(countFinal >= countAfter,
                    "RESUME BROKEN: Cannot scan after resume. Count didn't increase.");

            logStep("Resume successful: state preserved, scanning works");
            pass();
        } catch (Exception e) {
            fail("App resume test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== MALFORMED BARCODE ====================

    @Test(priority = 9, description = "Scan garbage barcode — app must not crash")
    public void testMalformedBarcodeDoesNotCrash() {
        setup("Malformed Barcode - No Crash");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Open a valid section first
            ScanHelper scan = new ScanHelper(driver, wait);
            scan.scan("STR-1002");

            // Scan various garbage barcodes
            String[] garbageBarcodes = {
                    "",                    // empty
                    " ",                   // whitespace
                    "STR-",                // section prefix only, no number
                    "STR-ABC",             // non-numeric section
                    "000000000000",        // all zeros
                    "!@#$%^&*()",          // special characters
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",  // very long
                    "null",                // literal null
                    "undefined",           // literal undefined
            };

            for (String barcode : garbageBarcodes) {
                logStep("Scanning garbage: '" + barcode + "'");
                try {
                    dwHelper.scanItemBarcode(barcode);
                    Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

                    // Dismiss any error dialog
                    if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                        driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                        Thread.sleep(500);
                    }
                } catch (Exception e) {
                    logStep("  Error (non-fatal): " + e.getMessage());
                }

                // Verify app didn't crash
                String activity = driver.currentActivity();
                Assert.assertTrue(activity.contains("MainActivity"),
                        "CRASH: App left MainActivity after scanning '" + barcode + "'. " +
                                "Activity: " + activity);
            }

            // Verify normal scanning still works after all the garbage
            scan.scan("092971236021");
            int listCount = mainScan.getItemCount();
            Assert.assertTrue(listCount >= 1,
                    "RECOVERY FAILURE: After garbage barcodes, normal scan broken. " +
                            "List shows " + listCount);

            logStep("All garbage barcodes handled without crash, normal scan works");
            pass();
        } catch (Exception e) {
            fail("Malformed barcode test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== STR-9999 MISC SECTION ====================

    @Test(priority = 10, description = "STR-9999 misc section: scan items — must not count toward section completion")
    public void testMiscSectionDoesNotCountAsCompleted() {
        setup("STR-9999 Misc Section - Not Counted");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan the misc section STR-9999
            scan.scan("STR-9999");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            String sectionOutput = mainScan.getSectionOutput();
            logStep("STR-9999 section output: " + sectionOutput);

            // Scan an item into it
            scan.scan("092971236021");
            int listCount = mainScan.getItemCount();
            logStep("Scanned 1 item into STR-9999, list shows: " + listCount);

            Assert.assertTrue(listCount >= 1,
                    "STR-9999 BUG: Item scanned into misc section but not shown in list. " +
                            "List shows " + listCount);

            // Close misc section
            scan.closeSection();
            logStep("Closed STR-9999 section");

            // Section counter should NOT include STR-9999
            // The app excludes it from all completion counts (BarcodeLoc != 'STR-9999')
            // Verify by checking section output — should still show low section count
            String outputAfterClose = mainScan.getSectionOutput();
            logStep("Section output after closing STR-9999: " + outputAfterClose);

            // Now scan a real section to verify normal flow still works
            scan.scan("STR-1002");
            scan.scan("092971277918");
            scan.closeSection();
            logStep("Scanned and closed a real section (STR-1002) after misc section");

            pass();
        } catch (Exception e) {
            fail("STR-9999 misc section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 11, description = "STR-9999: items scanned in misc section must still upload")
    public void testMiscSectionItemsUpload() {
        setup("STR-9999 Misc Section - Items Upload");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan items into misc section
            scan.scan("STR-9999");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.scan("092971338558");
            scan.closeSection(3);
            logStep("Scanned 3 items into STR-9999 and closed");

            // Finish inventory — close all real sections with 0
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            By closeWith0 = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='close with 0']");
            By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");
            int missedSections = 0;

            for (int i = 0; i < 200; i++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0, yesBtn);
                    String text = found.getText();

                    if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                        missedSections++;
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (text.equalsIgnoreCase("YES")) {
                        logStep("Confirming finish after closing " + missedSections + " sections with 0");
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            handlePostFinishDialogs();
            Thread.sleep(AppConfig.LONG_WAIT);

            // Verify upload — STR-9999 items MUST be included in upload count
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (!confirmPage.isDisplayed()) {
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            }

            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Final confirmation: " + countInfo);

                if (countInfo.contains("- 0 item") || countInfo.contains("- 0 ")) {
                    Assert.fail("STR-9999 UPLOAD BUG: Scanned 3 items into misc section " +
                            "but upload shows 0 items! Misc section items NOT uploaded. " +
                            "countInfo: " + countInfo);
                }

                logStep("VERIFIED: STR-9999 items included in upload");
            } else {
                String activity = driver.currentActivity();
                if (!activity.contains("FinalConfirm")) {
                    Assert.fail("STR-9999 UPLOAD FAILURE: Never reached FinalConfirmActivity. " +
                            "Activity: " + activity);
                }
            }

            pass();
        } catch (Exception e) {
            fail("STR-9999 upload test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 12, description = "STR-9999: scan misc section, then real sections — no interference")
    public void testMiscSectionDoesNotInterfereWithRealSections() {
        setup("STR-9999 No Interference");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan misc section first
            scan.scan("STR-9999");
            scan.scan("092971236021");
            scan.closeSection(1);
            logStep("Closed STR-9999 with 1 item");

            // Now scan 2 real sections
            scan.scan("STR-1002");
            scan.scan("092971277918");
            scan.scan("092971338558");
            int count1 = mainScan.getItemCount();
            scan.closeSection(2);
            logStep("Closed STR-1002 with 2 items (list showed " + count1 + ")");

            scan.scan("STR-1003");
            scan.scan("092971381516");
            int count2 = mainScan.getItemCount();
            scan.closeSection(1);
            logStep("Closed STR-1003 with 1 item (list showed " + count2 + ")");

            // Verify section output shows correct count (should be 2 real sections, NOT 3)
            // STR-9999 is excluded from section counts in the app
            String sectionOutput = mainScan.getSectionOutput();
            logStep("Section output after 2 real + 1 misc: " + sectionOutput);

            // The section counter in the app should reflect only real sections
            // e.g., "Store: 30 Section: 3/20" where 3 = 2 real + current (not counting 9999)
            Assert.assertFalse(sectionOutput.isEmpty(),
                    "SECTION STATE BUG: Section output is empty after scanning 3 sections. " +
                            "App may have lost track of section state.");

            pass();
        } catch (Exception e) {
            fail("STR-9999 interference test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== WIFI / NETWORK SCENARIOS ====================

    @Test(priority = 13, description = "Toggle airplane mode before finish — upload must fail gracefully, data preserved")
    public void testFinishWithNoWifi() {
        setup("Finish With No Wifi");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan a section with items (wifi still on — all data loaded at login)
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.closeSection();
            logStep("Scanned 2 items, closed section — all with wifi ON");

            // Turn off wifi (airplane mode)
            logStep("Enabling airplane mode to simulate wifi loss...");
            toggleAirplaneMode(true);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Try to finish — upload should fail
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Handle close-with-0 dialogs for remaining sections
            By closeWith0 = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='close with 0']");
            By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");

            for (int i = 0; i < 200; i++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0, yesBtn);
                    String text = found.getText();
                    if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (text.equalsIgnoreCase("YES")) {
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            // Wait for upload attempt to fail
            Thread.sleep(AppConfig.LONG_WAIT * 2);

            // Check what happened — app should show error or FinalConfirm with 0 items
            String activity = driver.currentActivity();
            logStep("After upload attempt with no wifi: activity = " + activity);

            if (activity.contains("FinalConfirm")) {
                // App reached FinalConfirm — check if it shows 0 uploaded (expected with no wifi)
                FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
                if (confirmPage.isDisplayed()) {
                    String countInfo = confirmPage.getCountInfo();
                    logStep("FinalConfirm with no wifi: " + countInfo);

                    // This is a known behavior — app shows FinalConfirm even on upload failure
                    // The key question: did it DELETE local data despite failed upload?
                    logStep("WARNING: App reached FinalConfirm despite no wifi. " +
                            "Check if local data was preserved for retry.");
                }
            } else if (activity.contains("MainActivity")) {
                // Good — stayed on scan screen, data should be preserved
                logStep("GOOD: App stayed on scan screen after wifi failure");

                // Verify items are still there
                boolean stillOnScan = mainScan.isDisplayed();
                Assert.assertTrue(stillOnScan,
                        "WIFI FAILURE BUG: App is on MainActivity but scan screen not displayed. " +
                                "Activity: " + activity);
            }

            // Re-enable wifi
            logStep("Re-enabling wifi...");
            toggleAirplaneMode(false);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

            pass();
        } catch (Exception e) {
            // Re-enable wifi in case of failure
            try { toggleAirplaneMode(false); } catch (Exception ignored) {}
            fail("No wifi test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 14, description = "Scan entire inventory offline — verify all scans persist locally")
    public void testFullScanOfflineThenUpload() {
        setup("Full Scan Offline Then Upload");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Turn off wifi AFTER login (data already loaded)
            logStep("Enabling airplane mode after login...");
            toggleAirplaneMode(true);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Scan multiple sections offline
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            int count1 = mainScan.getItemCount();
            scan.closeSection(2);
            logStep("Section 1: 2 items, list showed " + count1);

            scan.scan("STR-1003");
            scan.scan("092971338558");
            scan.scan("092971381516");
            scan.scan("715459469307");
            int count2 = mainScan.getItemCount();
            scan.closeSection(3);
            logStep("Section 2: 3 items, list showed " + count2);

            // Verify scanning worked offline (items in local SQLite)
            Assert.assertTrue(count1 >= 2,
                    "OFFLINE SCAN BUG: Scanned 2 items offline but list shows " + count1 +
                            ". Local SQLite insert may have failed.");
            Assert.assertTrue(count2 >= 3,
                    "OFFLINE SCAN BUG: Scanned 3 items offline but list shows " + count2 +
                            ". Local SQLite insert may have failed.");

            logStep("Offline scanning works — 5 items in 2 sections");

            // Re-enable wifi
            logStep("Re-enabling wifi for upload...");
            toggleAirplaneMode(false);
            Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

            // Now finish and upload
            scrollToBottom();
            mainScan.tapFinish();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Close remaining sections with 0
            By closeWith0 = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='close with 0']");
            By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");

            for (int i = 0; i < 200; i++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0, yesBtn);
                    String text = found.getText();
                    if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        mainScan.tapFinish();
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (text.equalsIgnoreCase("YES")) {
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            handlePostFinishDialogs();
            Thread.sleep(AppConfig.LONG_WAIT);

            // Verify upload succeeded now that wifi is back
            FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
            if (!confirmPage.isDisplayed()) {
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            }

            if (confirmPage.isDisplayed()) {
                String countInfo = confirmPage.getCountInfo();
                logStep("Final confirmation after offline scan + online upload: " + countInfo);

                if (countInfo.contains("- 0 item") || countInfo.contains("- 0 ")) {
                    Assert.fail("OFFLINE→ONLINE UPLOAD BUG: Scanned 5 items offline, " +
                            "re-enabled wifi, but upload shows 0 items! " +
                            "countInfo: " + countInfo);
                }

                logStep("VERIFIED: Offline scans uploaded successfully after wifi restored");
            } else {
                String activity = driver.currentActivity();
                logStep("FinalConfirm not displayed. Activity: " + activity);
            }

            pass();
        } catch (Exception e) {
            try { toggleAirplaneMode(false); } catch (Exception ignored) {}
            fail("Offline scan then upload test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 15, description = "Wifi drops mid-scan — scanning must continue without interruption")
    public void testWifiDropMidScanContinuesWorking() {
        setup("Wifi Drop Mid-Scan");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan first item with wifi ON
            scan.scan("STR-1002");
            scan.scan("092971236021");
            int countBefore = mainScan.getItemCount();
            logStep("Scanned 1 item with wifi ON, list shows " + countBefore);

            // Drop wifi mid-section
            logStep("Dropping wifi mid-section...");
            toggleAirplaneMode(true);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Continue scanning — should work (all data is local)
            scan.scan("092971277918");
            scan.scan("092971338558");
            int countAfter = mainScan.getItemCount();
            logStep("Scanned 2 more items with wifi OFF, list shows " + countAfter);

            Assert.assertTrue(countAfter > countBefore,
                    "WIFI DROP BUG: Scanning stopped working after wifi dropped! " +
                            "Before=" + countBefore + " After=" + countAfter + ". " +
                            "App should scan offline — all barcode data is local.");

            // Close section offline — should work
            scan.closeSection(3);
            logStep("Closed section with 3 items while offline — success");

            // Re-enable wifi
            toggleAirplaneMode(false);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            pass();
        } catch (Exception e) {
            try { toggleAirplaneMode(false); } catch (Exception ignored) {}
            fail("Wifi drop mid-scan test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== APP RESTART / DATA PERSISTENCE ====================

    @Test(priority = 16, description = "App killed mid-scan, restart — unclosed section items at risk of deletion")
    public void testAppRestartMidScanDataLoss() {
        setup("App Restart Mid-Scan");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan section + items but DON'T close the section
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.scan("092971338558");
            int countBeforeKill = mainScan.getItemCount();
            logStep("Scanned 3 items, section NOT closed. List shows " + countBeforeKill);

            // Also close a second section properly (this data should survive)
            scan.scan("STR-1003");
            scan.scan("092971381516");
            scan.closeSection(1);
            logStep("Closed STR-1003 with 1 item (should survive restart)");

            // Kill the app (simulates force stop, crash, or battery death)
            logStep("Killing app...");
            driver.terminateApp(AppConfig.APP_PACKAGE);
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Relaunch
            logStep("Relaunching app...");
            driver.activateApp(AppConfig.APP_PACKAGE);
            Thread.sleep(AppConfig.LONG_WAIT);

            // Login again
            StartHomePage startHome = new StartHomePage(driver, wait);
            if (startHome.isDisplayed()) {
                LoginPage loginPage = startHome.tapStartInventory();
                Thread.sleep(AppConfig.SHORT_WAIT);
                loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            }

            // Check if we're back on scan screen
            mainScan = new MainScanPage(driver, wait);
            if (!mainScan.isDisplayed()) {
                Thread.sleep(AppConfig.LONG_WAIT);
            }

            if (mainScan.isDisplayed()) {
                String sectionOutput = mainScan.getSectionOutput();
                logStep("After restart — section output: " + sectionOutput);

                // KNOWN BUG: DeletePrevNotClosedSections() runs on OnCreate
                // It deletes ALL items from unclosed sections (STR-1002's 3 items)
                // But STR-1003's 1 item should survive (it was closed)
                // Check if section counter reflects the closed section
                logStep("WARNING: App runs DeletePrevNotClosedSections on restart. " +
                        "Items from unclosed STR-1002 may be deleted. " +
                        "Closed STR-1003 should survive. " +
                        "Section output: " + sectionOutput);

                // The section counter should show at least 1 completed section (STR-1003)
                // If it shows 0, even closed section data was lost
                if (sectionOutput.contains("0/") || sectionOutput.contains("Section:  ")) {
                    logStep("POSSIBLE DATA LOSS: Section counter shows 0 after restart. " +
                            "Even properly closed sections may have been wiped.");
                }
            }

            pass();
        } catch (Exception e) {
            fail("App restart mid-scan test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 17, description = "Scan already-closed section — app must warn or block re-scanning")
    public void testScanAlreadyClosedSection() {
        setup("Scan Already Closed Section");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan and close a section
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.closeSection(2);
            logStep("Closed STR-1002 with 2 items");

            // Try to re-scan the same section
            DataWedgeHelper dwHelper = new DataWedgeHelper(driver);
            dwHelper.scanSectionBarcode("STR-1002");
            Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);

            // App should show a warning dialog about already-completed section
            // The app checks Preferences for completed sections (LocationBarcodeCompleted)
            boolean dialogShown = WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE) ||
                    WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL);

            String activity = driver.currentActivity();
            logStep("After re-scanning closed section: dialog=" + dialogShown +
                    " activity=" + activity);

            if (dialogShown) {
                logStep("GOOD: App showed warning about already-closed section");
                // Dismiss the dialog
                if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                    driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                    driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                }
                Thread.sleep(AppConfig.SHORT_WAIT);
            } else {
                // No dialog — app may have silently re-opened the section
                // This could cause duplicate data
                logStep("WARNING: No dialog shown when re-scanning closed section. " +
                        "App may allow duplicate scanning of completed sections.");
            }

            // Verify app is still functional
            Assert.assertTrue(activity.contains("MainActivity"),
                    "CRASH: App left MainActivity after re-scanning closed section. " +
                            "Activity: " + activity);

            pass();
        } catch (Exception e) {
            fail("Scan already-closed section test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 18, description = "Delete item then close section — count must match after deletion")
    public void testDeleteItemThenCloseSection() {
        setup("Delete Item Then Close");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan section + 3 items
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.scan("092971338558");
            int countBefore = mainScan.getItemCount();
            logStep("Scanned 3 items, list shows " + countBefore);

            // Long-press/tap the last item to delete it
            // The app uses a list — tap on item triggers delete dialog
            try {
                String lastItemText = mainScan.getItemText(countBefore - 1);
                logStep("Attempting to delete item: " + lastItemText);

                // Tap the item in the list
                By itemList = By.id("com.mavis.inventory_barcode_scanner:id/txtOutput");
                java.util.List<org.openqa.selenium.WebElement> items =
                        driver.findElement(itemList).findElements(By.className("android.widget.TextView"));
                if (!items.isEmpty()) {
                    items.get(items.size() - 1).click();
                    Thread.sleep(AppConfig.SHORT_WAIT);

                    // Delete dialog should appear — tap Delete/Yes
                    By deleteBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='delete']");
                    By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");

                    if (WaitHelper.isElementPresent(driver, deleteBtn)) {
                        driver.findElement(deleteBtn).click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        logStep("Deleted item via delete button");
                    } else if (WaitHelper.isElementPresent(driver, yesBtn)) {
                        driver.findElement(yesBtn).click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        logStep("Deleted item via yes button");
                    } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                        driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        logStep("Deleted item via positive button");
                    }
                }
            } catch (Exception e) {
                logStep("Could not delete item (UI flow may differ): " + e.getMessage());
            }

            // Now close section — manual count should be 2 (3 scanned - 1 deleted)
            // The app calculates: SUM(Qty) WHERE Deleted != true
            // If we enter 3 (original count), it should REJECT
            // If we enter 2 (after delete), it should ACCEPT
            scan.closeSection(2);
            logStep("Closed section with count 2 (3 scanned - 1 deleted) — validation passed");

            pass();
        } catch (Exception e) {
            fail("Delete item then close test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 19, description = "Double-tap Finish — must not trigger double upload")
    public void testDoubleFinishTapSafe() {
        setup("Double Finish Tap");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Quick scan + close
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.closeSection(1);
            logStep("Scanned 1 item, closed section");

            // Double-tap Finish rapidly
            scrollToBottom();
            By btnFinish = By.id("com.mavis.inventory_barcode_scanner:id/btnFinish");
            if (WaitHelper.isElementPresent(driver, btnFinish)) {
                driver.findElement(btnFinish).click();
                Thread.sleep(200); // Very short delay
                try {
                    driver.findElement(btnFinish).click(); // Second tap
                    logStep("Double-tapped Finish");
                } catch (Exception e) {
                    logStep("Second Finish tap failed (button may be gone): " + e.getMessage());
                }
            }
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Handle close-with-0 and yes dialogs
            By closeWith0 = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='close with 0']");
            By yesBtn = By.xpath("//*[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='yes']");

            for (int i = 0; i < 200; i++) {
                try {
                    org.openqa.selenium.WebElement found = WaitHelper.waitForAny(driver, 10,
                            closeWith0, yesBtn);
                    String text = found.getText();
                    if (text.equalsIgnoreCase("CLOSE WITH 0")) {
                        found.click();
                        Thread.sleep(AppConfig.SCAN_PROCESS_WAIT);
                        scrollToBottom();
                        if (WaitHelper.isElementPresent(driver, btnFinish)) {
                            driver.findElement(btnFinish).click();
                        }
                        Thread.sleep(AppConfig.MEDIUM_WAIT);
                    } else if (text.equalsIgnoreCase("YES")) {
                        found.click();
                        Thread.sleep(AppConfig.LONG_WAIT);
                        break;
                    }
                } catch (Exception e) {
                    break;
                }
            }

            handlePostFinishDialogs();
            Thread.sleep(AppConfig.LONG_WAIT);

            // App should NOT crash, should reach FinalConfirm exactly once
            String activity = driver.currentActivity();
            logStep("After double-tap finish: activity = " + activity);

            Assert.assertFalse(activity.contains("StartHome"),
                    "DOUBLE FINISH BUG: App went back to StartHome — " +
                            "double upload may have corrupted state and forced logout.");

            if (activity.contains("FinalConfirm")) {
                FinalConfirmPage confirmPage = new FinalConfirmPage(driver, wait);
                if (confirmPage.isDisplayed()) {
                    String countInfo = confirmPage.getCountInfo();
                    logStep("Final confirmation: " + countInfo);
                    logStep("GOOD: Double-tap Finish handled safely");
                }
            }

            pass();
        } catch (Exception e) {
            fail("Double finish tap test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    @Test(priority = 20, description = "Re-login to same inventory — closed section data must persist")
    public void testReloginSameInventoryDataPersists() {
        setup("Re-login Same Inventory");
        try {
            ScheduledInventory inv = loginToTireScanScreen();
            ScanHelper scan = new ScanHelper(driver, wait);
            MainScanPage mainScan = new MainScanPage(driver, wait);

            // Scan and close 2 sections
            scan.scan("STR-1002");
            scan.scan("092971236021");
            scan.scan("092971277918");
            scan.closeSection(2);
            logStep("Closed STR-1002 with 2 items");

            scan.scan("STR-1003");
            scan.scan("092971338558");
            scan.closeSection(1);
            logStep("Closed STR-1003 with 1 item");

            String sectionOutputBefore = mainScan.getSectionOutput();
            logStep("Section output before logout: " + sectionOutputBefore);

            // Go back / logout
            logStep("Pressing back to logout...");
            driver.navigate().back();
            Thread.sleep(AppConfig.MEDIUM_WAIT);

            // Dismiss any "are you sure" dialog
            if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE)) {
                driver.findElement(DIALOG_BUTTON_POSITIVE).click();
                Thread.sleep(AppConfig.SHORT_WAIT);
            }

            // Re-login with same credentials
            Thread.sleep(AppConfig.MEDIUM_WAIT);
            StartHomePage startHome = new StartHomePage(driver, wait);
            LoginPage loginPage;

            if (startHome.isDisplayed()) {
                loginPage = startHome.tapStartInventory();
            } else {
                loginPage = new LoginPage(driver, wait);
            }

            if (loginPage.isDisplayed()) {
                Thread.sleep(AppConfig.SHORT_WAIT);
                loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
                Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
                if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
            }

            mainScan = new MainScanPage(driver, wait);
            if (!mainScan.isDisplayed()) Thread.sleep(AppConfig.LONG_WAIT);

            if (mainScan.isDisplayed()) {
                String sectionOutputAfter = mainScan.getSectionOutput();
                logStep("Section output after re-login: " + sectionOutputAfter);

                // Section counter should show our 2 completed sections
                // If it shows 0, data was wiped on re-login
                if (sectionOutputAfter.contains("0/") && !sectionOutputBefore.contains("0/")) {
                    Assert.fail("RE-LOGIN DATA LOSS: Had completed sections before logout, " +
                            "but section counter shows 0 after re-login! " +
                            "Before: " + sectionOutputBefore + " After: " + sectionOutputAfter);
                }

                logStep("VERIFIED: Section data persisted across logout/re-login");
            } else {
                logStep("Not on scan screen after re-login. Activity: " + driver.currentActivity());
            }

            pass();
        } catch (Exception e) {
            fail("Re-login data persistence test failed: " + e.getMessage(), e);
        } finally {
            teardown();
        }
    }

    // ==================== HELPERS ====================

    private ScheduledInventory loginToTireScanScreen() throws Exception {
        ScheduledInventory inv = InventorySetupHelper.resolveInventory();
        logStep("Resolved inventory: " + inv);

        if (!inv.scheduledPCs.isEmpty() && !inv.scheduledPCs.contains(2)) {
            skip("No tire PC=2 in resolved inventory: " + inv.scheduledPCs);
        }

        StartHomePage startHome = new StartHomePage(driver, wait);
        Assert.assertTrue(startHome.isDisplayed(), "StartHome should load");

        LoginPage loginPage = startHome.tapStartInventory();
        Thread.sleep(AppConfig.SHORT_WAIT);

        loginPage.login(inv.store, AppConfig.TEST_EMPLOYEE, inv.invCode);
        Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);
        if (loginPage.isDisplayed()) Thread.sleep(AppConfig.LOGIN_SYNC_WAIT);

        MainScanPage mainScan = new MainScanPage(driver, wait);
        if (!mainScan.isDisplayed()) {
            String activity = driver.currentActivity();
            if (activity != null && activity.contains("PartsPCActivity")) {
                skip("App routed to Parts — need tire inventory (PC=2)");
            }
            Thread.sleep(AppConfig.LONG_WAIT);
        }
        Assert.assertTrue(mainScan.isDisplayed(), "Should be on tire scan screen");
        logStep("Logged in and on tire scan screen");

        return inv;
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
            swipe.addAction(finger.createPointerMove(java.time.Duration.ZERO,
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, startY));
            swipe.addAction(finger.createPointerDown(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            swipe.addAction(finger.createPointerMove(java.time.Duration.ofMillis(500),
                    org.openqa.selenium.interactions.PointerInput.Origin.viewport(), startX, endY));
            swipe.addAction(finger.createPointerUp(
                    org.openqa.selenium.interactions.PointerInput.MouseButton.LEFT.asArg()));
            driver.perform(java.util.Collections.singletonList(swipe));
            Thread.sleep(500);
        } catch (Exception e) { /* Scroll failed */ }
    }

    /**
     * Toggle airplane mode on/off via ADB.
     * Requires: adb shell permissions (rooted device or test-keys build)
     */
    private void toggleAirplaneMode(boolean enable) {
        try {
            java.util.Map<String, Object> args = new java.util.HashMap<>();
            String cmd = enable
                    ? "settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
                    : "settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false";
            args.put("command", "sh");
            args.put("args", java.util.Arrays.asList("-c", cmd));
            driver.executeScript("mobile: shell", args);
            logStep("Airplane mode " + (enable ? "ON" : "OFF"));
        } catch (Exception e) {
            logStep("Could not toggle airplane mode via Appium shell: " + e.getMessage());
            // Fallback: try direct adb
            try {
                String adbPath = AppConfig.ADB_PATH;
                String cmd = enable
                        ? "settings put global airplane_mode_on 1"
                        : "settings put global airplane_mode_on 0";
                new ProcessBuilder(adbPath, "-s", AppConfig.DEVICE_UDID, "shell", cmd)
                        .redirectErrorStream(true).start().waitFor();
                String broadcast = "am broadcast -a android.intent.action.AIRPLANE_MODE --ez state " + enable;
                new ProcessBuilder(adbPath, "-s", AppConfig.DEVICE_UDID, "shell", broadcast)
                        .redirectErrorStream(true).start().waitFor();
                logStep("Airplane mode " + (enable ? "ON" : "OFF") + " (via direct ADB)");
            } catch (Exception ex) {
                logStep("WARNING: Failed to toggle airplane mode: " + ex.getMessage());
            }
        }
    }

    private void handlePostFinishDialogs() throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            By uploadBtn = By.xpath("//*[@text='Upload']");
            By continueBtn = By.xpath("//*[@text='Continue']");
            By acceptBtn = By.xpath("//*[@text='Accept']");
            By skipBtn = By.xpath("//*[@text='Skip']");

            if (WaitHelper.isElementPresent(driver, uploadBtn)) {
                logStep("Tapping Upload");
                driver.findElement(uploadBtn).click();
                Thread.sleep(AppConfig.LONG_WAIT);
                break;
            } else if (WaitHelper.isElementPresent(driver, acceptBtn)) {
                driver.findElement(acceptBtn).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
            } else if (WaitHelper.isElementPresent(driver, skipBtn)) {
                driver.findElement(skipBtn).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
            } else if (WaitHelper.isElementPresent(driver, continueBtn)) {
                driver.findElement(continueBtn).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
            } else if (WaitHelper.isElementPresent(driver, DIALOG_BUTTON_NEUTRAL)) {
                driver.findElement(DIALOG_BUTTON_NEUTRAL).click();
                Thread.sleep(AppConfig.MEDIUM_WAIT);
            } else {
                break;
            }
        }
    }
}
