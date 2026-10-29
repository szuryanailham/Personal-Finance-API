package com.ilham.personal_finance_api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Year;
import java.util.Properties;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpOtpMailSenderTest {

    @Test
    void sendsUtf8HtmlWithDynamicOtpAndExpirationAndPlainTextFallback() throws Exception {
        JavaMailSender transport = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(message);
        SmtpOtpMailSender sender = new SmtpOtpMailSender(transport, "no-reply@artoku.test", "Artoku");

        sender.send("student@example.com", "Dina <script>alert('x')</script> & teman", "012345", Duration.ofMinutes(7));
        message.saveChanges();

        verify(transport).send(message);
        assertEquals("no-reply@artoku.test", message.getFrom()[0].toString());
        assertEquals("student@example.com", message.getAllRecipients()[0].toString());
        assertEquals("Kode verifikasi registrasi", message.getSubject());
        String html = body(message, "text/html");
        String plain = body(message, "text/plain");
        assertNotNull(html);
        assertNotNull(plain);
        assertTrue(html.contains("012345"));
        assertTrue(html.contains("7 menit"));
        assertTrue(plain.contains("012345"));
        assertTrue(plain.contains("7 menit"));
        assertTrue(html.contains("Dina &lt;script&gt;"));
        assertTrue(html.contains("&amp; teman"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("{{"), "All template variables must be resolved");
        assertTrue(html.contains("lang=\"id\""));
        assertTrue(html.contains("Verifikasi Email Kamu"));
        assertTrue(html.contains("Make Money Make Sense."));
        assertTrue(html.contains("&copy; " + Year.now().getValue() + " Artoku. All rights reserved."));
        assertTrue(html.contains("alt=\"Logo Artoku\""));
        assertTrue(html.contains("#F0F7F0"));
    }

    @Test
    void preservesSmtpFailureForRegistrationErrorHandling() {
        JavaMailSender transport = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP unavailable")).when(transport).send(message);
        SmtpOtpMailSender sender = new SmtpOtpMailSender(transport, "no-reply@artoku.test", "Artoku");

        assertThrows(MailSendException.class,
                () -> sender.send("student@example.com", "Dina", "987654", Duration.ofMinutes(11)));
    }

    private static String body(jakarta.mail.Part part, String contentType) throws Exception {
        if (part.isMimeType(contentType)) {
            assertTrue(part.getContentType().toUpperCase().contains("UTF-8"));
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                String result = body(child, contentType);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
