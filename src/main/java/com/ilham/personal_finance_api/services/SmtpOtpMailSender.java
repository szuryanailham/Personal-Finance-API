package com.ilham.personal_finance_api.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class SmtpOtpMailSender implements OtpMailSender {

    private static final String TEMPLATE_PATH = "mail-templates/registration-otp.html";
    private static final String SUBJECT = "Kode verifikasi registrasi";

    private final JavaMailSender mailSender;
    private final String from;
    private final String appName;
    private final String htmlTemplate;

    public SmtpOtpMailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.app-name}") String appName) {
        this.mailSender = mailSender;
        this.from = from;
        this.appName = appName;
        this.htmlTemplate = loadTemplate();
    }

    @Override
    public void send(String email, String recipientName, String otp, Duration validFor) {
        long minutes = validFor.toMinutes();
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(SUBJECT);
            helper.setText(plainText(otp, minutes), html(recipientName, otp, minutes));
        } catch (MessagingException e) {
            throw new MailPreparationException("Failed to build OTP email", e);
        }

        mailSender.send(message);
    }

    private String html(String recipientName, String otp, long minutes) {
        Map<String, String> values = Map.of(
                "{{name}}", recipientName,
                "{{otp}}", otp,
                "{{expiry_minutes}}", String.valueOf(minutes),
                "{{app_name}}", appName,
                "{{year}}", String.valueOf(Year.now().getValue()));

        String result = htmlTemplate;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), HtmlUtils.htmlEscape(entry.getValue()));
        }
        return result;
    }

    private static String plainText(String otp, long minutes) {
        return """
                Kode verifikasi Anda: %s

                Kode ini berlaku selama %d menit. Jangan bagikan kode ini kepada siapa pun.
                Abaikan email ini jika Anda tidak merasa mendaftar.
                """.formatted(otp, minutes);
    }

    private static String loadTemplate() {
        try {
            return new ClassPathResource(TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("OTP email template not found: " + TEMPLATE_PATH, e);
        }
    }
}
