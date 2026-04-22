package com.docusphere.backend.authentication.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.scheduling.annotation.Async;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Async
    public void sendVerificationEmail(@org.springframework.lang.NonNull String to, @org.springframework.lang.NonNull String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject("Email Verification - DocuSphere");

            String userName = to.split("@")[0];

            // Prepare context for Thymeleaf template
            Context context = new Context();
            context.setVariable("userName", userName);
            context.setVariable("verificationLink", verificationLink);

            // Process template with variables
            String htmlContent = templateEngine.process("verification-email", context);
            if (htmlContent == null) {
                throw new MessagingException("Email content failed to generate");
            }
            helper.setText(htmlContent, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("Failed to send verification email to: {}", to, e);
        }
    }

    //Send Passwor Reset Email
    public void sendPasswordResetEmail(@org.springframework.lang.NonNull String to, @org.springframework.lang.NonNull String resetLink) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject("Reset Your Password - DocuSphere");

        String userName = to.split("@")[0];

        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("resetLink", resetLink);
        context.setVariable("expiryTime", "30 minutes");   // You can change this

        String htmlContent = templateEngine.process("reset-password-email", context);
        if (htmlContent == null) {
            throw new MessagingException("Email content failed to generate");
        }

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
