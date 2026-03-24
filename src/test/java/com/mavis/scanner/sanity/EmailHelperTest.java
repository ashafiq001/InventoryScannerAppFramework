package com.mavis.scanner.sanity;

import com.mavis.scanner.utils.EmailHelper;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Quick test to verify email sending works.
 * Run this standalone to check SMTP credentials.
 */
public class EmailHelperTest {

    @Test(description = "Verify email sending with current SMTP credentials")
    public void testSendEmail() {
        EmailHelper.enable();
        boolean sent = EmailHelper.sendReport(null, "This is a test email from EmailHelperTest.\n\nIf you received this, email is working.");
        Assert.assertTrue(sent, "Email failed to send — check SMTP credentials (app password may be expired)");
    }
}
