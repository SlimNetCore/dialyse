package com.hemodialyse.backend.infrastructure.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hemodialyseOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("API Hémodialyse")
                .description("""
                    API REST pour la gestion multi-centre d'hémodialyse.

                    ## Authentification
                    1. Appelez `POST /api/v1/auth/login` avec :
                       ```json
                       { "centerId": "11111111-1111-1111-1111-111111111111", "username": "admin", "password": "admin123" }
                       ```
                    2. Copiez le `token` reçu
                    3. Cliquez sur **Authorize** et collez le token (sans le préfixe Bearer)

                    ## Comptes de test
                    | Login | Mot de passe | Rôle |
                    |-------|-------------|------|
                    | admin | admin123 | ADMIN |

                    ## Centres de test
                    | ID | Nom |
                    |----|-----|
                    | 11111111-1111-1111-1111-111111111111 | Centre Dakar Principal |
                    | 22222222-2222-2222-2222-222222222222 | Centre Saint-Louis |
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("Équipe Hémodialyse")
                    .email("dev@hemodialyse.dz")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Serveur de développement")))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName,
                    new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token JWT obtenu via POST /api/v1/auth/login")));
    }
}
