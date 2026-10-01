package com.zoner.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI zonerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Zoner API")
                .version("v1")
                .description("REST API for Zoner, a calendar built around time zones. "
                        + "Errors use a single JSON shape: status, code, message, details, traceId, timestamp."));
    }
}
