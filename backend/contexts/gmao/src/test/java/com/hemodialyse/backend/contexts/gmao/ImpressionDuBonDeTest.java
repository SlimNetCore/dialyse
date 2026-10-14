package com.hemodialyse.backend.contexts.gmao;

import com.hemodialyse.backend.contexts.gmao.application.intervention.DocumentImprime;
import com.hemodialyse.backend.contexts.gmao.application.intervention.ImpressionDuBon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Racine de test : l'impression du bon est fournie par la plateforme dans l'application (moteur de documents) ; sans elle
 * (tests du module seul), le bon est un document vide.
 */
@Configuration(proxyBeanMethods = false)
class ImpressionDuBonDeTest {

    @Bean
    ImpressionDuBon impressionDuBon() {
        return (centre, donnees, zone, editePar) -> new DocumentImprime("PDF", new byte[]{'%', 'P', 'D', 'F', '-'});
    }
}
