package com.docusphere.backend.authentication.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public void sendVerificationEmail(@org.springframework.lang.NonNull String to, @org.springframework.lang.NonNull String verificationLink) throws MessagingException {
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
    }
}