package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.ObservationsBiologiquesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des observations biologiques sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class ObservationsBiologiquesJdbcContrat extends ObservationsBiologiquesContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM observations_biologiques WHERE center_id = ?", centre);
        }
    }

    @Override
    protected ObservationsBiologiques observations() {
        return new ObservationsBiologiquesJdbc(jdbc());
    }

    private UUID poser(String systeme, String statut, String source, BigDecimal valeur, String unite, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO observations_biologiques (id, center_id, patient_id, analyte_code_system, analyte_code, date_prelevement, "
                        + "statut, source, valeur_num, unite, valeur_texte, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", id,
                CENTRE_A.valeur(), PATIENT.valeur(), systeme, "718-7", java.sql.Date.valueOf(date), statut, source, valeur, unite,
                valeur == null ? "Indosable" : null, Timestamp.from(T0), Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_incoherente_en_base_est_ignoree_par_les_lectures_sans_casser_les_autres() {
        UUID bonne = poser("LOINC", "FINAL", "IMPORT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 1));
        UUID systeme = poser("SNOMED", "FINAL", "IMPORT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 2));
        UUID statut = poser("LOINC", "ANNULE", "IMPORT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 3));
        UUID source = poser("LOINC", "FINAL", "ROBOT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 4));
        UUID sansUnite = poser("LOINC", "FINAL", "IMPORT", new BigDecimal("1.5"), null, LocalDate.of(2026, 1, 5));
        UUID uniteBlanche = poser("LOINC", "FINAL", "IMPORT", new BigDecimal("1.5"), " ", LocalDate.of(2026, 1, 6));
        var page = observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(o -> o.id()).containsExactly(bonne);
        for (UUID illisible : List.of(systeme, statut, source, sansUnite, uniteBlanche)) {
            assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
        }
        assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    @Test
    void une_observation_derivee_d_un_bilan_est_relue_avec_sa_source() {
        UUID id = poser("LOINC", "FINAL", "DERIVEE_BILAN", new BigDecimal("9"), "g/dL", LocalDate.of(2026, 1, 1));
        assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, id).orElseThrow().source().name()).isEqualTo("DERIVEE_BILAN");
    }

    /**
     * Limite consignée (ADR 0085, verifications-production.md section 50) : une ligne illisible ne peut être ni corrigée ni supprimée par le
     * module ; chaque lecture qui l'écarte journalise en WARN son identifiant SEUL, sans code, patient ni valeur.
     */
    @Test
    void une_ligne_illisible_est_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("SNOMED", "ANNULE", "ROBOT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 2));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ObservationsBiologiquesJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(observations().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
            observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 10);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(2).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(e.getFormattedMessage()).contains(illisible.toString()).doesNotContain("SNOMED").doesNotContain("718-7")
                    .doesNotContain("ROBOT").doesNotContain(PATIENT.valeur().toString());
        });
    }

    @Test
    void sans_ligne_illisible_aucun_avertissement_n_est_emis() {
        poser("LOINC", "FINAL", "IMPORT", new BigDecimal("1.5"), "g/dL", LocalDate.of(2026, 1, 1));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ObservationsBiologiquesJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(observations().duPatient(CENTRE_A, PATIENT, null, null, null, 0, 10).total()).isEqualTo(1);
            assertThat(observations().deLaDemande(CENTRE_A, PATIENT, DEMANDE)).isEmpty();
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).isEmpty();
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'observations_biologiques'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "analyte_code", "analyte_code_display", "analyte_code_system", "center_id",
                "created_at", "date_prelevement", "demande_examen_id", "patient_id", "source", "statut", "unite", "updated_at", "valeur_num",
                "valeur_texte");
    }
}
