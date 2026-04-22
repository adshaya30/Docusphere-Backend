package com.docusphere.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.docusphere")
@org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.docusphere")
@org.springframework.boot.autoconfigure.domain.EntityScan("com.docusphere")
@org.springframework.scheduling.annotation.EnableAsync
public class BackendApplication {


	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
