package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.MotifDeSuppression;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.JournalDesSuppressionsJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du journal des suppressions sur la table réelle {@code seance_suppression} (V27) : H2 et PostgreSQL 16 (sous-classes). */
abstract class JournalDesSuppressionsContrat {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final Instant T = Instant.parse("2026-06-15T10:00:00Z");

    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        jdbc().update("DELETE FROM seance_suppression WHERE center_id = ?", CENTRE.valeur());
    }

    private static SuppressionDeSeance suppression(int n, String motif, StatutDeSeance statut) {
        return new SuppressionDeSeance(new UUID(9, n), CENTRE, new SeanceId(new UUID(2, n)), new PatientId(new UUID(1, 1)),
                LocalDate.of(2026, 6, 15), statut, new MotifDeSuppression(motif), "u-admin", T);
    }

    @Test
    void should_write_one_line_with_the_status_the_trimmed_reason_the_author_and_the_instant() {
        new JournalDesSuppressionsJdbc(jdbc()).enregistrer(suppression(1, "  Séance saisie en double  ", StatutDeSeance.SIGNEE));

        var lignes = jdbc().queryForList("SELECT id, center_id, seance_id, patient_id, date_seance, statut, motif, supprime_par, supprime_le "
                + "FROM seance_suppression WHERE center_id = ?", CENTRE.valeur());
        assertThat(lignes).hasSize(1);
        var ligne = lignes.get(0);
        assertThat(ligne.get("id")).isEqualTo(new UUID(9, 1));
        assertThat(ligne.get("seance_id")).isEqualTo(new UUID(2, 1));
        assertThat(ligne.get("patient_id")).isEqualTo(new UUID(1, 1));
        assertThat(ligne.get("date_seance").toString()).isEqualTo("2026-06-15");
        assertThat(ligne.get("statut")).isEqualTo("SIGNEE");
        assertThat(ligne.get("motif")).isEqualTo("Séance saisie en double");
        assertThat(ligne.get("supprime_par")).isEqualTo("u-admin");
        assertThat(jdbc().queryForObject("SELECT supprime_le FROM seance_suppression WHERE id = ?", java.sql.Timestamp.class, new UUID(9, 1))
                .toInstant()).isEqualTo(T);
    }

    @Test
    void should_keep_the_longest_reason_and_every_removal_of_the_same_session() {
        var journal = new JournalDesSuppressionsJdbc(jdbc());
        journal.enregistrer(suppression(1, "a".repeat(500), StatutDeSeance.CREE));
        journal.enregistrer(new SuppressionDeSeance(new UUID(9, 2), CENTRE, new SeanceId(new UUID(2, 1)), new PatientId(new UUID(1, 1)),
                LocalDate.of(2026, 6, 15), StatutDeSeance.CREE, new MotifDeSuppression("Recréée puis supprimée"), "u-admin", T));

        assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM seance_suppression WHERE center_id = ?", Integer.class, CENTRE.valeur()))
                .isEqualTo(2);
        assertThat(jdbc().queryForObject("SELECT LENGTH(motif) FROM seance_suppression WHERE id = ?", Integer.class, new UUID(9, 1)))
                .isEqualTo(500);
    }

    @Test
    void should_have_exactly_the_nine_columns_of_V27_and_add_none() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'seance_suppression'", String.class);

        assertThat(colonnes).containsExactlyInAnyOrder("id", "center_id", "seance_id", "patient_id", "date_seance", "statut", "motif",
                "supprime_par", "supprime_le");
    }
}
