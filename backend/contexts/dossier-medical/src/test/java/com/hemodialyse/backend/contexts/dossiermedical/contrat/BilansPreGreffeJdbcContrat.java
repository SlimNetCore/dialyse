package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffeIllisible;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.BilansPreGreffeJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contrat des bilans pré-greffe sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class BilansPreGreffeJdbcContrat extends BilansPreGreffeContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        TablesDuBilanPreGreffe.vider(jdbc(), CENTRE_A.valeur(), CENTRE_B.valeur());
    }

    @Override
    protected BilansPreGreffe bilans() {
        return new BilansPreGreffeJdbc(jdbc());
    }

    private UUID poser(String statut) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO bilans_pre_greffe (id, center_id, patient_id, statut, created_at, updated_at) VALUES (?,?,?,?,?,?)", id,
                CENTRE_A.valeur(), PATIENT.valeur(), statut, Timestamp.from(T0), Timestamp.from(T0));
        return id;
    }

    @Test
    void un_statut_illisible_en_base_leve_une_erreur_metier_dediee_et_journalise_l_identifiant_seul() {
        UUID illisible = poser("STATUT_DISPARU");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(BilansPreGreffeJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThatThrownBy(() -> bilans().trouver(CENTRE_A, PATIENT)).isInstanceOf(BilanPreGreffeIllisible.class);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(1);
        assertThat(capture.list.getFirst().getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(capture.list.getFirst().getFormattedMessage()).contains(illisible.toString()).doesNotContain("STATUT_DISPARU")
                .doesNotContain(PATIENT.valeur().toString());
    }

    @Test
    void un_doublon_est_journalise_avec_son_seul_identifiant_et_jamais_en_erreur() {
        poser("NON_DEBUTE");
        UUID doublon = UUID.randomUUID();
        jdbc().update("INSERT INTO bilans_pre_greffe (id, center_id, patient_id, statut, created_at, updated_at) VALUES (?,?,?,?,?,?)", doublon,
                CENTRE_A.valeur(), PATIENT.valeur(), "NON_DEBUTE", Timestamp.from(T0.plusSeconds(5)), Timestamp.from(T0));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(BilansPreGreffeJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(bilans().trouver(CENTRE_A, PATIENT)).isPresent();
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(1);
        assertThat(capture.list.getFirst().getFormattedMessage()).contains(doublon.toString()).doesNotContain(PATIENT.valeur().toString());
    }

    @Test
    void une_decision_dont_l_avis_est_illisible_est_ignoree_sans_casser_les_autres() {
        UUID bilan = poser("NON_DEBUTE");
        jdbc().update("INSERT INTO decisions_rcp (id, bilan_id, date_reunion, avis) VALUES (?,?,?,?)", UUID.randomUUID(), bilan,
                java.sql.Date.valueOf(JOUR), "FAVORABLE");
        jdbc().update("INSERT INTO decisions_rcp (id, bilan_id, date_reunion, avis) VALUES (?,?,?,?)", UUID.randomUUID(), bilan,
                java.sql.Date.valueOf(JOUR), "AVIS_DISPARU");
        assertThat(bilans().trouver(CENTRE_A, PATIENT).orElseThrow().decisions()).hasSize(1);
    }

    @Test
    void les_tables_gardent_leurs_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'bilans_pre_greffe'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "center_id", "conclusion_nephrologue", "contre_indications", "created_at",
                "date_debut_bilan", "date_greffe", "date_inscription_liste_attente", "groupe_sanguin_confirme", "patient_id", "pra_classe_i",
                "pra_classe_ii", "statut", "typage_hla", "updated_at");
    }
}
