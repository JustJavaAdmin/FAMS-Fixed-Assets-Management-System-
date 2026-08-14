package com.example.fams.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Sends an email synchronously (blocking).
     * Use {@link #sendEmailAsync(String, String, String)} for fire-and-forget scenarios.
     */
    public void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);

            log.debug("Email sent successfully to: {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw ex; // Re-throw so callers can handle if needed
        }
    }

    /**
     * Sends an email asynchronously (non-blocking) using the dedicated email thread pool.
     * Returns a CompletableFuture for callers who want to track completion/failure.
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Void> sendEmailAsync(String to, String subject, String text) {
        try {
            sendEmail(to, subject, text);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            // Email failures shouldn't crash the application
            log.error("Async email failed to {}: {}", to, ex.getMessage());
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * Fire-and-forget email sending. Does not return anything, just queues the email.
     */
    @Async("emailTaskExecutor")
    public void sendEmailFireAndForget(String to, String subject, String text) {
        try {
            sendEmail(to, subject, text);
        } catch (Exception ex) {
            // Email failures shouldn't crash the application
            log.error("Fire-and-forget email failed to {}: {}", to, ex.getMessage());
        }
    }
}