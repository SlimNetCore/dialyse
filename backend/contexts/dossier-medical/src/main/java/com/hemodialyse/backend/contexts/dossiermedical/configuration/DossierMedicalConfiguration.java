package com.hemodialyse.backend.contexts.dossiermedical.configuration;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesConstantesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.domain.HistoriqueDesConstantes;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.legacyidentite.SessionLegacy;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.HistoriqueDesConstantesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.ConstantesControleur;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composition du contexte Dossier médical (slice DM1, refonte sur place, ADR 0024) : mêmes tables ({@code seances},
 * {@code volet_paramedical}), même URL. Seul endroit qui connaît toutes les couches.
 */
@Configuration(proxyBeanMethods = false)
public class DossierMedicalConfiguration {

    @Bean
    SessionCourante sessionCouranteDossierMedical() {
        return new SessionLegacy();
    }

    @Bean
    HistoriqueDesConstantes historiqueDesConstantes(JdbcTemplate jdbc) {
        return new HistoriqueDesConstantesJdbc(jdbc);
    }

    @Bean
    ConsulterLesConstantesDuPatient consulterLesConstantesDuPatient(SessionCourante session, HistoriqueDesConstantes historique) {
        return new ConsulterLesConstantesDuPatient(session, historique);
    }

    @Bean
    ConstantesControleur constantesControleur(ConsulterLesConstantesDuPatient consulter) {
        return new ConstantesControleur(consulter);
    }
}
