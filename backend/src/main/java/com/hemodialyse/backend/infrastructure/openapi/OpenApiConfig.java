package com.hemodialyse.backend.infrastructure.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI hemodialyseOpenApi() {
        return new OpenAPI().info(new Info()
            .title("Hemodialyse API")
            .description("API backend pour la gestion multi-centre d hemodialyse")
            .version("v1"));
    }
}

