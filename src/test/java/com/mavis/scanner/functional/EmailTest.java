package com.mavis.scanner.functional;

import com.mavis.scanner.utils.EmailHelper;
import org.testng.annotations.Test;

/**
 * Standalone test to verify email sending works.
 * Run with: mvn test -Dtest=EmailHelperTest
 *
 * Override SMTP settings via system properties if needed:
 *   -Dmail.smtp.host=smtp.gmail.com
 *   -Dmail.smtp.port=587
 *   -Dmail.smtp.username=ashafiq@mavis.com
 *   -Dmail.smtp.password=YOUR_APP_PASSWORD
 *   -Dmail.to=ashafiq@mavis.com
 */
public class EmailTest {

    @Test(description = "Verify SMTP email delivery works end-to-end")
    public void testSendEmail() {
        String testSummary = "This is a test email to verify SMTP configuration.\n"
                + "If you receive this, email sending is working correctly.\n"
                + "Sent from: EmailHelperTest.testSendEmail()";

        boolean sent = EmailHelper.sendReport("test-reports/latest-report.html", "test", testSummary);

        if (sent) {
            System.out.println("SUCCESS: Test email was sent. Check your inbox.");
        } else {
            System.err.println("FAILURE: Email was NOT sent. Check SMTP config and credentials.");
        }

        assert sent : "Email failed to send. Check SMTP credentials and network connectivity. "
                + "Look for 'Failed to send email' in stderr for details.";
    }
}
