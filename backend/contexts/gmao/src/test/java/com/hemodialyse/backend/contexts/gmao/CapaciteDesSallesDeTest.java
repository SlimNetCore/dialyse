package com.hemodialyse.backend.contexts.gmao;

import com.hemodialyse.backend.contexts.gmao.application.equipement.CapaciteDesSalles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Racine de test : la capacité des salles est fournie par l'ACL du legacy dans l'application ; sans elle (tests du module
 * seul), aucune salle n'impose de limite.
 */
@Configuration(proxyBeanMethods = false)
class CapaciteDesSallesDeTest {

    @Bean
    CapaciteDesSalles capaciteDesSalles() {
        return (centre, salle, equipement) -> {
        };
    }
}
