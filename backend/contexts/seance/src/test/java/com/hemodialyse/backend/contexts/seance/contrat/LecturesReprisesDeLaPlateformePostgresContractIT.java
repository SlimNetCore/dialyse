package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacygmao.GenerateursDesPatientsJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacypatient.PlanningDesPatientsJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyplanification.PlaceDuPatientJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyreferentiel.ForfaitsDuCentreJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.ForfaitsDeSeanceJdbc;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.HistoriqueDesSeancesJdbc;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Contrat des lectures reprises de la plateforme sur un PostgreSQL 16 JETABLE ({@code -Dseance.pg16.url}, utilisateur {@code seance.pg16.user},
 * mot de passe dans la variable d'environnement {@code SEANCE_PG16_PASSWORD}) : mêmes tests que H2. Les tables sont créées dans un schéma
 * dédié {@code se7f_lectures} (une seule connexion, {@code search_path}), jamais dans le schéma Flyway ; le script
 * {@code seance-se7f-pg16-rejeu.sh} rejoue les mêmes requêtes contre le schéma Flyway réel.
 */
@EnabledIfSystemProperty(named = "seance.pg16.url", matches = ".+")
class LecturesReprisesDeLaPlateformePostgresContractIT extends LecturesReprisesDeLaPlateformeContrat {

    private static final SingleConnectionDataSource SOURCE = new SingleConnectionDataSource(System.getProperty("seance.pg16.url", ""),
            System.getProperty("seance.pg16.user", "seance_test"), System.getenv().getOrDefault("SEANCE_PG16_PASSWORD", ""), true);
    private static JdbcTemplate jdbc;

    private static synchronized JdbcTemplate connexion() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(SOURCE);
            jdbc.execute("DROP SCHEMA IF EXISTS se7f_lectures CASCADE");
            jdbc.execute("CREATE SCHEMA se7f_lectures");
            jdbc.execute("SET search_path TO se7f_lectures");
        }
        return jdbc;
    }

    @AfterAll
    static void nettoyer() {
        if (jdbc != null) {
            jdbc.execute("DROP SCHEMA IF EXISTS se7f_lectures CASCADE");
            SOURCE.destroy();
            jdbc = null;
        }
    }

    @Override
    JdbcTemplate jdbc() {
        return connexion();
    }

    @Override
    HistoriqueDesSeances historique() {
        return new HistoriqueDesSeancesJdbc(connexion());
    }

    @Override
    ForfaitsDeSeance forfaits() {
        return new ForfaitsDeSeanceJdbc(connexion());
    }

    @Override
    ForfaitsDuCentre referentiel() {
        return new ForfaitsDuCentreJdbc(connexion());
    }

    @Override
    PlanningDesPatients planning() {
        return new PlanningDesPatientsJdbc(connexion());
    }

    @Override
    GenerateursDesPatients generateurs() {
        return new GenerateursDesPatientsJdbc(connexion());
    }

    @Override
    PlaceDuPatient place(PlacesDesPatients places) {
        return new PlaceDuPatientJdbc(connexion(), places);
    }
}
