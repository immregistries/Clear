package org.immregistries.clear.service;

import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.Properties;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.immregistries.clear.utils.SystemSettingSupport;

public class EmailService {

    public static final String SMTP_HOST_KEY = "email.smtp.host";
    public static final String SMTP_PORT_KEY = "email.smtp.port";
    public static final String SMTP_USERNAME_KEY = "email.smtp.username";
    public static final String SMTP_PASSWORD_KEY = "email.smtp.password";
    public static final String SMTP_FROM_EMAIL_KEY = "email.smtp.from.email";
    public static final String SMTP_FROM_NAME_KEY = "email.smtp.from.name";
    public static final String SMTP_AUTH_KEY = "email.smtp.auth";
    public static final String SMTP_STARTTLS_KEY = "email.smtp.starttls";
    public static final String SMTP_SSL_KEY = "email.smtp.ssl";

    public static final String DEFAULT_SMTP_PORT = "587";
    public static final String DEFAULT_SMTP_AUTH = "true";
    public static final String DEFAULT_SMTP_STARTTLS = "true";
    public static final String DEFAULT_SMTP_SSL = "false";

    public void sendTestEmail(String recipient) {
        sendPlainTextEmail(recipient,
                "CLEAR SMTP Test Email",
                "This is a test email from CLEAR. Your SMTP configuration appears to be working.");
    }

    public void sendPlainTextEmail(String recipient, String subject, String body) {
        String to = required(recipient, "Recipient email is required.");
        String messageSubject = required(subject, "Email subject is required.");
        String messageBody = required(body, "Email body is required.");

        SmtpSettings settings = loadSettings();
        validate(settings);

        Properties props = new Properties();
        props.put("mail.smtp.host", settings.host);
        props.put("mail.smtp.port", String.valueOf(settings.port));
        props.put("mail.smtp.auth", String.valueOf(settings.auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(settings.starttls));
        props.put("mail.smtp.ssl.enable", String.valueOf(settings.ssl));
        props.put("mail.smtp.ssl.trust", "*");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Authenticator authenticator = null;
        if (settings.auth) {
            authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.username, settings.password);
                }
            };
        }

        Session mailSession = Session.getInstance(props, authenticator);

        try {
            Message message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(settings.fromEmail, settings.fromName));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(messageSubject);
            message.setText(messageBody);
            Transport.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new EmailSendException("Unable to send email using configured SMTP settings.", e);
        }
    }

    private SmtpSettings loadSettings() {
        Map<String, String> values = SystemSettingSupport.getValues(
                SMTP_HOST_KEY,
                SMTP_PORT_KEY,
                SMTP_USERNAME_KEY,
                SMTP_PASSWORD_KEY,
                SMTP_FROM_EMAIL_KEY,
                SMTP_FROM_NAME_KEY,
                SMTP_AUTH_KEY,
                SMTP_STARTTLS_KEY,
                SMTP_SSL_KEY);

        SmtpSettings settings = new SmtpSettings();
        settings.host = SystemSettingSupport.trimToNull(values.get(SMTP_HOST_KEY));
        settings.port = SystemSettingSupport.parseInteger(values.get(SMTP_PORT_KEY));
        settings.username = SystemSettingSupport.trimToNull(values.get(SMTP_USERNAME_KEY));
        settings.password = SystemSettingSupport.trimToNull(values.get(SMTP_PASSWORD_KEY));
        settings.fromEmail = SystemSettingSupport.trimToNull(values.get(SMTP_FROM_EMAIL_KEY));
        settings.fromName = SystemSettingSupport.trimToNull(values.get(SMTP_FROM_NAME_KEY));
        settings.auth = SystemSettingSupport.parseBoolean(values.get(SMTP_AUTH_KEY), true);
        settings.starttls = SystemSettingSupport.parseBoolean(values.get(SMTP_STARTTLS_KEY), true);
        settings.ssl = SystemSettingSupport.parseBoolean(values.get(SMTP_SSL_KEY), false);

        if (settings.port == null) {
            settings.port = Integer.valueOf(DEFAULT_SMTP_PORT);
        }
        if (settings.fromName == null) {
            settings.fromName = "CLEAR";
        }
        return settings;
    }

    private void validate(SmtpSettings settings) {
        required(settings.host, "SMTP host is required. Configure key: " + SMTP_HOST_KEY);
        if (settings.port == null || settings.port.intValue() < 1 || settings.port.intValue() > 65535) {
            throw new IllegalStateException("SMTP port must be between 1 and 65535. Configure key: "
                    + SMTP_PORT_KEY);
        }
        required(settings.fromEmail, "SMTP from email is required. Configure key: " + SMTP_FROM_EMAIL_KEY);
        if (settings.auth) {
            required(settings.username, "SMTP username is required when auth is enabled. Configure key: "
                    + SMTP_USERNAME_KEY);
            required(settings.password, "SMTP password is required when auth is enabled. Configure key: "
                    + SMTP_PASSWORD_KEY);
        }
    }

    private String required(String value, String message) {
        String normalized = SystemSettingSupport.trimToNull(value);
        if (normalized == null) {
            throw new IllegalStateException(message);
        }
        return normalized;
    }

    private static class SmtpSettings {
        private String host;
        private Integer port;
        private String username;
        private String password;
        private String fromEmail;
        private String fromName;
        private boolean auth;
        private boolean starttls;
        private boolean ssl;
    }

    public static class EmailSendException extends IllegalStateException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
