package com.docusphere.backend.Common.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
@Configuration
public class AdminConfig {
    @Value("${app.admin-emails:}")
    private String adminEmailsString;

    public List<String> getAdminEmails() {
        if (adminEmailsString == null || adminEmailsString.trim().isEmpty()) {
            return List.of();   // return empty list if nothing is set
        }

        return Arrays.stream(adminEmailsString.split(","))
                .map(String::trim)                    // remove extra spaces
                .filter(email -> !email.isEmpty())    // remove empty entries
                .toList();
    }
}
