package com.ilham.personal_finance_api.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Properties;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpPasswordResetMailSenderTest {
    @Test void sendsEscapedMultipartEmailAndPreservesConfiguredQuery() throws Exception {
        var transport = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(message);
        var sender = new SmtpPasswordResetMailSender(transport, "sender@example.com", "App <name>",
                "https://example.com/reset?return=%2Fhome&token=old");
        sender.send("user@example.com", "<script>{{url}}</script>", "abc_123-xyz");
        message.saveChanges();
        verify(transport).send(message);
        assertEquals("Reset Password", message.getSubject());
        assertEquals("user@example.com", message.getAllRecipients()[0].toString());
        String html = body(message, "text/html"); String plain = body(message, "text/plain");
        assertTrue(html.contains("&lt;script&gt;{{url}}&lt;/script&gt;"));
        assertTrue(html.contains("App &lt;name&gt;"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("href=\"https://example.com/reset?return=%2Fhome&amp;token=abc_123-xyz\""));
        assertTrue(plain.contains("https://example.com/reset?return=%2Fhome&token=abc_123-xyz"));
        for (String content : new String[]{html, plain}) {
            assertTrue(content.contains("15 minutes"));
            assertTrue(content.contains("If you did not request this"));
        }
    }

    @Test void validatesConfigurationWithoutExposingTheUrl() {
        for (String url : new String[]{"/relative", "javascript:alert(1)", "https://user:secret@example.com/reset", "https://example.com/#secret", "not a URI"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> new SmtpPasswordResetMailSender(
                    mock(JavaMailSender.class), "sender@example.com", "App", url));
            assertFalse(error.getMessage().contains(url)); assertNull(error.getCause());
        }
    }

    @Test void propagatesSmtpFailureToTransactionalService() {
        var transport = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        when(transport.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("failure")).when(transport).send(message);
        var sender = new SmtpPasswordResetMailSender(transport, "sender@example.com", "App", "http://localhost:3000/reset-password");
        assertThrows(MailSendException.class, () -> sender.send("user@example.com", "User", "token"));
    }

    static String body(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart content = (Multipart) part.getContent();
            for (int i = 0; i < content.getCount(); i++) {
                String result = body(content.getBodyPart(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
