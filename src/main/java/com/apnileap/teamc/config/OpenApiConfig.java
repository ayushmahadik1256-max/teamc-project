package com.apnileap.teamc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI teamCOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("APNILEAP - Team C: Metadata, Version & Restore Manager")
                        .description("Authoritative backup/restore transaction, versioning and audit API")
                        .version("v1")
                        .contact(new Contact().name("Team C")));
    }
}
