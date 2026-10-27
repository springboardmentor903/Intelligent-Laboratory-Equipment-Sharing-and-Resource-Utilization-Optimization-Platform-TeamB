package com.campus.lab_resource_platform.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        emailService = new EmailService(
                mailSender,
                "noreply@campus.edu",
                "http://localhost:5173"
        );
    }

    @Test
    @DisplayName("sendPasswordResetEmail dispatches properly formatted message with reset link and instructions")
    void sendPasswordResetEmail_success() {
        String recipient = "researcher@campus.edu";
        String rawToken = "abc123def456token789";

        emailService.sendPasswordResetEmail(recipient, rawToken);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertNotNull(message);
        assertArrayEquals(new String[]{recipient}, message.getTo());
        assertEquals("noreply@campus.edu", message.getFrom());
        assertTrue(message.getSubject().contains("Password Reset Request"));

        String body = message.getText();
        assertNotNull(body);
        assertTrue(body.contains("http://localhost:5173/forgot-password?token=" + rawToken),
                "Email body must include direct reset link with token query parameter");
        assertTrue(body.contains(rawToken),
                "Email body must include manual token fallback code");
        assertTrue(body.contains("15 minutes"),
                "Email body must inform user of the 15-minute expiration period");
        assertTrue(body.contains("single-use"),
                "Email body must inform user that token is single-use");
    }

    @Test
    @DisplayName("sendPasswordResetEmail propagates MailException so caller can invalidate token and return generic public response")
    void sendPasswordResetEmail_mailException_failsSafely() {
        doThrow(new MailSendException("SMTP connection refused: connection timed out"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        MailException ex = assertThrows(
                MailException.class,
                () -> emailService.sendPasswordResetEmail("user@campus.edu", "secret-token-xyz")
        );

        assertTrue(ex.getMessage().contains("SMTP connection refused"));
        assertFalse(ex.getMessage().contains("secret-token-xyz"),
                "Exception must never disclose the raw reset token");
    }

    @Test
    @DisplayName("sendPasswordResetEmail aborts gracefully when recipient or token is empty")
    void sendPasswordResetEmail_emptyInputs_abortsWithoutException() {
        emailService.sendPasswordResetEmail("", "token");
        emailService.sendPasswordResetEmail("user@campus.edu", "");
        emailService.sendPasswordResetEmail(null, "token");
        emailService.sendPasswordResetEmail("user@campus.edu", null);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
