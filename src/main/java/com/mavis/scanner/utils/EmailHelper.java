package com.mavis.scanner.utils;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Sends test report emails with attachments.
 *
 * On weekends (Sat/Sun), emails only go to the primary recipient.
 */
public class EmailHelper {

    private static final String SMTP_HOST = System.getProperty("mail.smtp.host", "smtp.gmail.com");
    private static final String SMTP_PORT = System.getProperty("mail.smtp.port", "587");
    private static final String SMTP_USERNAME = System.getProperty("mail.smtp.username", "ashafiq@mavis.com");
    private static final String SMTP_PASSWORD = System.getProperty("mail.smtp.password", "xvtywbxatqbadsvx");
    private static final String EMAIL_FROM = System.getProperty("mail.from", "ashafiq@mavis.com");
    private static final String EMAIL_TO = System.getProperty("mail.to", "ashafiq@mavis.com");
    private static final String EMAIL_CC = System.getProperty("mail.cc", "mperson@mavis.com,mandjelkovic@mavis.com");
    private static final String EMAIL_BCC = System.getProperty("mail.bcc", "sgibbons@mavis.com");

    //

    private static final String WEEKEND_ONLY_RECIPIENT = "ashafiq@mavis.com";
    private static boolean enabled = false;

    private static boolean isWeekend() {
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        return today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY;
    }


    public static void enable(){
        enabled= true;
        System.out.println("[EmailHelper] Email enabled");
    }

    public static boolean isEnabled(){
        return enabled;
    }

    /**
     * Send test report email with attachment.
     */
    public static boolean sendReport(String reportPath, String testSummary) {
        if (SMTP_USERNAME.isEmpty() || SMTP_PASSWORD.isEmpty()) {
            System.out.println("[EmailHelper] SMTP not configured, skipping email");
            return false;
        }

        // Weekend policy: only primary recipient
        String to, cc, bcc;
        if (isWeekend()) {
            System.out.println("[EmailHelper] Weekend — sending only to " + WEEKEND_ONLY_RECIPIENT);
            to = WEEKEND_ONLY_RECIPIENT;
            cc = null;
            bcc = null;
        } else {
            to = EMAIL_TO;
            cc = EMAIL_CC;
            bcc = EMAIL_BCC;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_FROM));

            // Use InternetAddress.parse() for comma-separated recipients (NOT new InternetAddress())
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

            if (cc != null && !cc.trim().isEmpty()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            if (bcc != null && !bcc.trim().isEmpty()) {
                message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc));
            }

            message.setSubject("Inventory Scanner Test Report - " + LocalDate.now());

            Multipart multipart = new MimeMultipart();

            // Body
            MimeBodyPart textPart = new MimeBodyPart();
            String body = buildBody(testSummary, reportPath);
            textPart.setContent(body, "text/html; charset=utf-8");
            multipart.addBodyPart(textPart);

            // Attachment
            if (reportPath != null) {
                File reportFile = new File(reportPath);
                if (reportFile.exists()) {
                    MimeBodyPart attachmentPart = new MimeBodyPart();
                    DataSource source = new FileDataSource(reportFile);
                    attachmentPart.setDataHandler(new DataHandler(source));
                    attachmentPart.setFileName(reportFile.getName());
                    multipart.addBodyPart(attachmentPart);
                }
            }

            message.setContent(multipart);
            Transport.send(message);

            System.out.println("[EmailHelper] Report sent to: " + to +
                    (cc != null && !cc.isEmpty() ? "; CC: " + cc : ""));
            return true;

        } catch (MessagingException e) {
            System.err.println("[EmailHelper] Failed to send email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean sendFailureAlert(String testName, String errorMessage, String storeNumber) {
        if (!enabled) return false;
        if (SMTP_USERNAME.isEmpty() || SMTP_PASSWORD.isEmpty()) return false;

        String to = isWeekend() ? WEEKEND_ONLY_RECIPIENT : EMAIL_TO;

        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.checkserveridentity", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("TEST FAILURE ALERT — Store " + storeNumber + " — " + LocalDate.now());

            String body = "<html><body style='font-family: Arial, sans-serif;'>"
                    + "<h2 style='color: #d32f2f;'>Test Failure Alert</h2>"
                    + "<table style='border-collapse: collapse;'>"
                    + "<tr><td style='padding: 4px 12px; font-weight: bold;'>Test:</td><td>" + escapeHtml(testName) + "</td></tr>"
                    + "<tr><td style='padding: 4px 12px; font-weight: bold;'>Store:</td><td>" + escapeHtml(storeNumber) + "</td></tr>"
                    + "<tr><td style='padding: 4px 12px; font-weight: bold;'>Error:</td><td><pre style='background:#f5f5f5; padding:8px;'>" + escapeHtml(errorMessage) + "</pre></td></tr>"
                    + "<tr><td style='padding: 4px 12px; font-weight: bold;'>Time:</td><td>" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")) + "</td></tr>"
                    + "</table>"
                    + "<p style='color: #999; font-size: 11px;'>Automated alert from Inventory Scanner Test Suite</p>"
                    + "</body></html>";

            message.setContent(body, "text/html; charset=utf-8");
            Transport.send(message);

            System.out.println("[EmailHelper] Failure alert sent for: " + testName);
            return true;
        } catch (MessagingException e) {
            System.err.println("[EmailHelper] Failed to send failure alert: " + e.getMessage());
            return false;
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static boolean sendReport(String reportPath) {
        return sendReport(reportPath, null);
    }

    private static String buildBody(String testSummary, String reportPath) {
        StringBuilder body = new StringBuilder();
        body.append("<html><body style='font-family: Arial, sans-serif;'>");
        body.append("<h2>Inventory Scanner Test Report</h2>");
        body.append("<p>Completed: <strong>")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")))
                .append("</strong></p>");

        if (testSummary != null && !testSummary.isEmpty()) {
            body.append("<h3>Summary</h3>");
            body.append("<pre style='background-color: #f5f5f5; padding: 15px; border-radius: 5px;'>");
            body.append(testSummary);
            body.append("</pre>");
        }

        if (reportPath != null) {
            body.append("<p>Report file: ").append(new File(reportPath).getName()).append("</p>");
        }

        body.append("<p style='color: #999; font-size: 11px;'>Automated message from Inventory Scanner Test Suite</p>");
        body.append("</body></html>");
        return body.toString();
    }
}
