package com.mavis.scanner.listeners;

import com.mavis.scanner.utils.EmailHelper;
import org.testng.*;

import java.util.Map;

public class TestListener implements ISuiteListener, ITestListener {

    public void onStart(ISuite suite) {
        System.out.println("\n===============================");
        System.out.println("Starting Test Suite: " + suite.getName());
        System.out.println("===============================");
    }


    public void onFinish(ISuite suite) {
        System.out.println("\n===============================");
        System.out.println("Test Suite Completed: " + suite.getName());
        System.out.println("===============================");

        String testSummary = buildTestSummary(suite);
        System.out.println(testSummary);

        if(EmailHelper.isEnabled()){
            String reportPath = "test-reports/latest-report.html";
            System.out.println("[TestListener] Sending email with report");
            EmailHelper.sendReport(reportPath, testSummary);
        }
    }



    public void onTestStart(ITestResult testResult) {}

    public void onTestSuccess(ITestResult testResult) {
        System.out.println("[TestListener] Test Passed: " +  testResult.getName());
    }


    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getTestClass().getName() + "." + result.getName();
        String errorMessage = result.getThrowable() != null
                ? result.getThrowable().getMessage()
                : "Unknown error";
        String storeNumber = System.getProperty("TEST_STORE", "unknown");

        System.out.println("[TestListener] FAILED: " + testName + " — " + errorMessage);

        if (EmailHelper.isEnabled()) {
            System.out.println("[TestListener] Sending failure alert email...");
            EmailHelper.sendFailureAlert(testName, errorMessage, storeNumber);
        }
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        System.out.println("[TestListener] SKIPPED: " + result.getName());
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        // not used
    }

    @Override
    public void onStart(ITestContext context) {
        // test context started
    }

    @Override
    public void onFinish(ITestContext context) {
        // test context finished
    }

    /**
     * Builds a plain-text summary of all test results in the suite.
     */
    private String buildTestSummary(ISuite suite) {
        StringBuilder summary = new StringBuilder();

        int totalPassed = 0;
        int totalFailed = 0;
        int totalSkipped = 0;

        Map<String, ISuiteResult> results = suite.getResults();

        for (ISuiteResult suiteResult : results.values()) {
            ITestContext context = suiteResult.getTestContext();

            int passed = context.getPassedTests().size();
            int failed = context.getFailedTests().size();
            int skipped = context.getSkippedTests().size();

            totalPassed += passed;
            totalFailed += failed;
            totalSkipped += skipped;

            summary.append(String.format("Test: %s%n", context.getName()));
            summary.append(String.format("  Passed:  %d%n", passed));
            summary.append(String.format("  Failed:  %d%n", failed));
            summary.append(String.format("  Skipped: %d%n", skipped));
            summary.append(String.format("  Duration: %d ms%n%n",
                    context.getEndDate().getTime() - context.getStartDate().getTime()));
        }

        summary.append("-".repeat(40)).append("\n");
        summary.append(String.format("TOTAL RESULTS%n"));
        summary.append(String.format("  Passed:  %d%n", totalPassed));
        summary.append(String.format("  Failed:  %d%n", totalFailed));
        summary.append(String.format("  Skipped: %d%n", totalSkipped));
        summary.append(String.format("  Total:   %d%n", totalPassed + totalFailed + totalSkipped));

        String status = totalFailed == 0 ? "ALL TESTS PASSED" : "FAILURES DETECTED — Manual Testing Required";
        summary.append("\n").append(status);

        return summary.toString();
    }

}
