package com.mavis.scanner.pages;

import com.mavis.scanner.config.AppConfig;
import com.mavis.scanner.utils.WaitHelper;
import io.appium.java_client.android.AndroidDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for BatteryReturnWizard - 3-step wizard for collecting all return types.
 *
 * Wizard layout: batteryReturnWizard.xml (questions)
 * Summary layout: batteryReturnSummary.xml
 * Cores edit layout: batteryReturnAddCores.xml
 * Activity: BatteryReturnWizard
 *
 * Steps:
 *   1. "Are stock rotate batteries being scanned today?" -> Yes/No
 *   2. "Are warranty batteries being scanned out today?" -> Yes/No
 *   3. "Please enter the number of used batteries (cores) being returned" -> count + Submit
 *   Summary -> Edit each type or Complete
 */
public class BatteryReturnWizardPage {

    private final AndroidDriver driver;
    private final WebDriverWait wait;

    private static final String PKG = "com.mavis.inventory_barcode_scanner:id/";

    // Wizard question dialog (batteryReturnWizard.xml)
    private static final By WIZARD_TITLE = By.id(PKG + "txtTitleUp");
    private static final By STEP_COUNTER = By.id(PKG + "txtCount");
    private static final By QUESTION_TEXT = By.id(PKG + "txtTitle");
    private static final By BTN_YES = By.id(PKG + "btnRetYes");
    private static final By BTN_NO = By.id(PKG + "btnRetNo");
    private static final By BTN_EXIT = By.id(PKG + "btnRetExit");
    private static final By BTN_SUBMIT = By.id(PKG + "btnRetComplete");
    private static final By CORE_COUNT_INPUT = By.id(PKG + "editBattManualCount");
    private static final By PO_NUMBER = By.id(PKG + "txtSectionOutputP");

    // Summary dialog (batteryReturnSummary.xml)
    private static final By SUMMARY_TITLE = By.id(PKG + "enterPcTitle");
    private static final By ROTATES_COUNT = By.id(PKG + "rotatesSummaryCount");
    private static final By WARRANTIES_COUNT = By.id(PKG + "warrantiesSummaryCount");
    private static final By CORES_COUNT = By.id(PKG + "coreSummaryCount");
    private static final By BTN_EDIT_ROTATES = By.id(PKG + "btnEditRotate");
    private static final By BTN_EDIT_WARRANTY = By.id(PKG + "btnEditWarranty");
    private static final By BTN_EDIT_CORES = By.id(PKG + "btnEditCore");
    private static final By BTN_COMPLETE_RETURNS = By.id(PKG + "btnCompleteBateryReturns");

    // Core edit dialog (batteryReturnAddCores.xml)
    private static final By BTN_MINUS = By.id(PKG + "minus_button");
    private static final By BTN_PLUS = By.id(PKG + "plus_button");

    // AlertDialog buttons (for completion/upload dialogs)
    private static final By DIALOG_BUTTON_POSITIVE = By.id("android:id/button1");

    public BatteryReturnWizardPage(AndroidDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    // ==================== WIZARD QUESTIONS ====================

    public boolean isWizardDisplayed() {
        return WaitHelper.isElementPresent(driver, WIZARD_TITLE)
                && (WaitHelper.isElementPresent(driver, BTN_YES)
                || WaitHelper.isElementPresent(driver, CORE_COUNT_INPUT));
    }

    public String getStepCounter() {
        return WaitHelper.getTextSafe(driver, STEP_COUNTER);
    }

    public String getQuestionText() {
        return WaitHelper.getTextSafe(driver, QUESTION_TEXT);
    }

    public String getPoNumber() {
        return WaitHelper.getTextSafe(driver, PO_NUMBER);
    }

    /**
     * Answer "Yes" to the current wizard question.
     * This navigates to BatteryReturnActivity for that return type.
     */
    public void tapYes() {
        WaitHelper.waitAndClick(wait, BTN_YES);
    }

    /**
     * Answer "No" to the current wizard question.
     * Inserts a placeholder record and moves to the next question.
     */
    public void tapNo() {
        WaitHelper.waitAndClick(wait, BTN_NO);
    }

    public void tapExit() {
        WaitHelper.waitAndClick(wait, BTN_EXIT);
    }

    // ==================== CORES STEP (Question 3) ====================

    public boolean isCoreCountInputDisplayed() {
        return WaitHelper.isElementPresent(driver, CORE_COUNT_INPUT);
    }

    public String getCoreCountValue() {
        return WaitHelper.getTextSafe(driver, CORE_COUNT_INPUT);
    }

    /**
     * Enter the number of cores being returned.
     */
    public void enterCoreCount(String count) {
        WaitHelper.waitAndType(wait, CORE_COUNT_INPUT, count);
    }

    /**
     * Tap Submit to confirm the core count.
     */
    public void tapSubmit() {
        WaitHelper.waitAndClick(wait, BTN_SUBMIT);
    }

    /**
     * Tap the + button to increment core count.
     */
    public void tapPlus() {
        WaitHelper.waitAndClick(wait, BTN_PLUS);
    }

    /**
     * Tap the - button to decrement core count.
     */
    public void tapMinus() {
        WaitHelper.waitAndClick(wait, BTN_MINUS);
    }

    // ==================== SUMMARY VIEW ====================

    public boolean isSummaryDisplayed() {
        return WaitHelper.isElementPresent(driver, SUMMARY_TITLE)
                || WaitHelper.isElementPresent(driver, BTN_COMPLETE_RETURNS);
    }

    public String getRotatesCount() {
        return WaitHelper.getTextSafe(driver, ROTATES_COUNT);
    }

    public String getWarrantiesCount() {
        return WaitHelper.getTextSafe(driver, WARRANTIES_COUNT);
    }

    public String getCoresCount() {
        return WaitHelper.getTextSafe(driver, CORES_COUNT);
    }

    public void tapEditRotates() {
        WaitHelper.waitAndClick(wait, BTN_EDIT_ROTATES);
    }

    public void tapEditWarranty() {
        WaitHelper.waitAndClick(wait, BTN_EDIT_WARRANTY);
    }

    public void tapEditCores() {
        WaitHelper.waitAndClick(wait, BTN_EDIT_CORES);
    }

    /**
     * Tap "Complete battery returns" to trigger the upload.
     */
    public void tapCompleteReturns() {
        WaitHelper.waitAndClick(wait, BTN_COMPLETE_RETURNS);
    }

    // ==================== UPLOAD COMPLETION DIALOG ====================

    /**
     * Check if the upload completion dialog is showing (shows PO number).
     */
    public boolean isCompletionDialogDisplayed() {
        return WaitHelper.isElementPresent(driver, DIALOG_BUTTON_POSITIVE);
    }

    /**
     * Dismiss the completion dialog (OK button).
     */
    public void tapCompletionOk() {
        WaitHelper.waitAndClick(wait, DIALOG_BUTTON_POSITIVE);
    }
}
