package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.gmao.service.AideDecisionMaintenance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Expose le service de domaine d'aide à la décision GMAO (le domaine reste exempt de Spring).
 * Seuil de réforme : ratio maintenance cumulée / prix d'acquisition (défaut 60 %).
 */
@Configuration
public class GmaoDecisionConfig {

    @Bean
    public AideDecisionMaintenance aideDecisionMaintenance(
            @Value("${hemodialyse.gmao.reforme-seuil-ratio:0.60}") BigDecimal seuilRatio) {
        return new AideDecisionMaintenance(seuilRatio);
    }
}
