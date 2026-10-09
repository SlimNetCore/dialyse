package com.hemodialyse.backend.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuse de démarrer en production ({@code prod}) avec une configuration de développement : secret JWT par défaut ou
 * trop court, base H2 en mémoire. Un oubli de variable d'environnement se voit au démarrage, pas après une fuite.
 */
@Component
@Profile("prod")
public class ProductionSafetyGuard {

    static final int MIN_SECRET_LENGTH = 32;

    public ProductionSafetyGuard(@Value("${app.jwt.secret}") String jwtSecret,
                                 @Value("${spring.datasource.url}") String datasourceUrl) {
        List<String> violations = violations(jwtSecret, datasourceUrl);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Configuration de production refusée : " + String.join(" ; ", violations));
        }
    }

    /**
     * Liste, en français, ce qui interdit le démarrage en production ; vide si la configuration est acceptable.
     */
    static List<String> violations(String jwtSecret, String datasourceUrl) {
        List<String> violations = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.isBlank() || jwtSecret.startsWith("change-me")) {
            violations.add("JWT_SECRET absent ou égal à la valeur de développement");
        } else if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            violations.add("JWT_SECRET trop court (" + MIN_SECRET_LENGTH + " caractères minimum)");
        }
        if (datasourceUrl == null || datasourceUrl.toLowerCase().startsWith("jdbc:h2:")) {
            violations.add("DB_URL pointe vers H2 : la production utilise PostgreSQL");
        }
        return violations;
    }
}
