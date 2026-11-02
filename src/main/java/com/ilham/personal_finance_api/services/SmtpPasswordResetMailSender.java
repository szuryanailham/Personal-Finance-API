package com.ilham.personal_finance_api.services;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.mail.MessagingException;

@Service
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {
    private final JavaMailSender transport;
    private final String from;
    private final String appName;
    private final URI resetUri;
    private final String template;

    public SmtpPasswordResetMailSender(JavaMailSender transport,
            @Value("${app.mail.from}") String from,
            @Value("${app.mail.app-name}") String appName,
            @Value("${app.password-reset.url}") String resetUrl) {
        this.transport = transport;
        this.from = from;
        this.appName = appName;
        try {
            resetUri = URI.create(resetUrl);
            if (!("https".equalsIgnoreCase(resetUri.getScheme()) || "http".equalsIgnoreCase(resetUri.getScheme()))
                    || resetUri.getHost() == null || resetUri.getUserInfo() != null || resetUri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Password reset URL must be an absolute HTTP(S) URL without credentials or fragment");
        }
        try {
            template = new ClassPathResource("mail-templates/password-reset.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Password reset email template unavailable", e);
        }
    }

    @Override
    public void send(String email, String recipientName, String token) {
        String url = UriComponentsBuilder.fromUri(resetUri).replaceQueryParam("token", token)
                .build(true).toUriString();
        // Replace in one pass so user text cannot introduce additional template substitutions.
        String html = Pattern.compile("\\{\\{(name|app_name|url)\\}\\}").matcher(template).replaceAll(match ->
                Matcher.quoteReplacement(HtmlUtils.htmlEscape(switch (match.group(1)) {
                    case "name" -> recipientName == null ? "" : recipientName;
                    case "app_name" -> appName;
                    default -> url;
                })));
        var message = transport.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Reset Password");
            helper.setText("Reset your password: " + url
                    + "\n\nThis link expires in 15 minutes. If you did not request this, ignore this email. Your password will remain unchanged.", html);
        } catch (MessagingException e) {
            throw new MailPreparationException("Failed to build password reset email");
        }
        transport.send(message);
    }
}
