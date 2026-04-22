package com.docusphere.backend.common.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class AppConfig {
    
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;
}
