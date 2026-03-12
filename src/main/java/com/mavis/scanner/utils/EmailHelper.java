package com.mavis.scanner.utils;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.File;
import java.util.Properties;

public class EmailHelper {

    private static final String SMTP_HOST = "smtp.gmail.com";       // or your company SMTP
    private static final int SMTP_PORT = 587;
    private static final String SENDER_EMAIL = "ashafiq@mavis.com";
    private static final String SENDER_PASSWORD = "gvjhqmfsexmbvgee"; // use app password, not real password
    private static final String[] RECIPIENTS = {
            "ashafiq@mavis.com"
    };

    public static boolean sendReport(String reportPath, String suiteName, String summary) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));

            InternetAddress[] toAddresses = new InternetAddress[RECIPIENTS.length];
            for (int i = 0; i < RECIPIENTS.length; i++) {
                toAddresses[i] = new InternetAddress(RECIPIENTS[i]);
            }
            message.setRecipients(Message.RecipientType.TO, toAddresses);

            message.setSubject("Inventory Scanner Test Report - " + suiteName);

            // Email body
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(
                    "<h2>Inventory Scanner - Appium Test Results</h2>" +
                            "<p>" + summary + "</p>" +
                            "<p>See attached HTML report for full details.</p>",
                    "text/html"
            );

            // Attach the HTML report
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(reportPath));

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachmentPart);

            message.setContent(multipart);

            Transport.send(message);
            System.out.println("[EmailHelper] Report emailed to " + RECIPIENTS.length + " recipients");
            return true;

        } catch (Exception e) {
            System.err.println("[EmailHelper] Failed to send email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}