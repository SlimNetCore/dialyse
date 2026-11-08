package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.HistoriqueFourni;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Contrat des lectures de la Séance sur H2 en mode PostgreSQL. */
class LecturesDeLaSeanceH2ContratTest extends LecturesDeLaSeanceContrat {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:lectures_seance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1", "sa", ""));

    @Override
    JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    HistoriqueFourni historique() {
        return new HistoriqueFourniLegacy(jdbc);
    }

    @Override
    ForfaitsFournis forfaits() {
        return new ForfaitsFournisLegacy(jdbc);
    }

    @Override
    ReferentielDeForfaitsFourni referentiel() {
        return new ReferentielDeForfaitsFourniLegacy(jdbc);
    }
}
