package com.hemodialyse.backend.contexts.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.CapaciteDesSallesFournie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Racine de test : la capacité des salles est fournie par la Planification (port fourni, ADR 0192) dans l'application ; sans elle (tests
 * du module seul), aucune salle n'impose de limite.
 */
@Configuration(proxyBeanMethods = false)
class CapaciteDesSallesDeTest {

    @Bean
    CapaciteDesSallesFournie capaciteDesSallesFournie() {
        return (centre, salle, equipement) -> {
        };
    }
}
