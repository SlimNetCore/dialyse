package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * Contrat des lectures de la Séance sur un PostgreSQL 16 JETABLE ({@code -Dseance.pg16.url}, utilisateur {@code seance.pg16.user}, mot de
 * passe dans la variable d'environnement {@code SEANCE_PG16_PASSWORD}) : mêmes tests que H2. Les tables sont créées dans un schéma
 * dédié {@code se1_lectures} (une seule connexion, {@code search_path}), jamais dans le schéma Flyway.
 */
@EnabledIfSystemProperty(named = "seance.pg16.url", matches = ".+")
class LecturesDeLaSeancePostgresContractIT extends LecturesDeLaSeanceContrat {

    private static final SingleConnectionDataSource SOURCE = new SingleConnectionDataSource(System.getProperty("seance.pg16.url", ""),
            System.getProperty("seance.pg16.user", "seance_test"), System.getenv().getOrDefault("SEANCE_PG16_PASSWORD", ""), true);
    private static JdbcTemplate jdbc;

    private static synchronized JdbcTemplate connexion() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(SOURCE);
            jdbc.execute("DROP SCHEMA IF EXISTS se1_lectures CASCADE");
            jdbc.execute("CREATE SCHEMA se1_lectures");
            jdbc.execute("SET search_path TO se1_lectures");
        }
        return jdbc;
    }

    @AfterAll
    static void nettoyer() {
        if (jdbc != null) {
            jdbc.execute("DROP SCHEMA IF EXISTS se1_lectures CASCADE");
            SOURCE.destroy();
            jdbc = null;
        }
    }

    @Override
    JdbcTemplate jdbc() {
        return connexion();
    }

    @Override
    HistoriqueFourni historique() {
        return new HistoriqueFourniLegacy(connexion());
    }

    @Override
    ForfaitsFournis forfaits() {
        return new ForfaitsFournisLegacy(connexion());
    }

    @Override
    ReferentielDeForfaitsFourni referentiel() {
        return new ReferentielDeForfaitsFourniLegacy(connexion());
    }
}
