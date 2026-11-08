package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.RaccourcisDesCentres;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.RaccourcisDesCentresJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port sur la table réelle {@code seance_raccourcis_articles} (H2 et PostgreSQL 16 : sous-classes). */
abstract class RaccourcisJdbcContrat extends RaccourcisContrat {

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM seance_raccourcis_articles WHERE center_id = ?", centre);
        }
    }

    @Override
    protected RaccourcisDesCentres depot() {
        return new RaccourcisDesCentresJdbc(jdbc());
    }

    @Test
    void la_table_garde_ses_trois_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'seance_raccourcis_articles'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("center_id", "article_id", "ordre");
    }

    @Test
    void l_ordre_ecrit_est_la_position_a_partir_de_zero() {
        depot().remplacer(com.hemodialyse.backend.contexts.seance.domain.RaccourcisDeConsommables.restituer(CENTRE_A, List.of(
                new com.hemodialyse.backend.contexts.seance.domain.ArticleId(new java.util.UUID(4, 9)),
                new com.hemodialyse.backend.contexts.seance.domain.ArticleId(new java.util.UUID(4, 8)))));

        assertThat(jdbc().queryForList("SELECT ordre FROM seance_raccourcis_articles WHERE center_id = ? ORDER BY ordre", Integer.class,
                CENTRE_A.valeur())).containsExactly(0, 1);
    }
}
