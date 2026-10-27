package com.campus.lab_resource_platform.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String frontendUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from:${MAIL_FROM:noreply@campus.edu}}") String fromEmail,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl
    ) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        if (toEmail == null || toEmail.isBlank() || resetToken == null || resetToken.isBlank()) {
            log.warn("Operational diagnostic: Password reset email dispatch aborted due to missing recipient or token.");
            return;
        }

        String resetLink = frontendUrl + "/forgot-password?token=" + resetToken;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail.trim());
            message.setSubject("Password Reset Request - Lab Resource Platform");
            message.setText(
                    "Hello,\n\n" +
                    "A password reset was requested for your account on the Intelligent Laboratory Equipment Sharing and Resource Utilization Optimization Platform.\n\n" +
                    "To reset your password, please click the following link or copy it into your browser:\n" +
                    resetLink + "\n\n" +
                    "Alternatively, you may enter the reset token manually on the reset page:\n" +
                    resetToken + "\n\n" +
                    "For security reasons, this link and token are single-use and will expire in 15 minutes.\n\n" +
                    "If you did not request this password reset, please ignore this email. Your account remains secure.\n\n" +
                    "Best regards,\n" +
                    "Laboratory Resource Optimization Team"
            );

            mailSender.send(message);
            log.info("Operational diagnostic: Password reset email successfully dispatched.");
        } catch (MailException e) {
            log.error("Operational diagnostic: Mail transport error during password reset dispatch: {}", e.getClass().getSimpleName());
            throw e;
        }
    }
}
