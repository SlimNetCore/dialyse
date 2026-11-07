package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AntecedentsJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des antécédents sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class AntecedentsJdbcContrat extends AntecedentsContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM antecedents_medicaux WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Antecedents antecedents() {
        return new AntecedentsJdbc(jdbc());
    }

    private UUID poser(String type, String statut, String systeme, String code, LocalDate debut) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO antecedents_medicaux (id, patient_id, center_id, type_antecedent, diagnostic_code_system, diagnostic_code, "
                        + "libelle_libre, date_debut, statut_clinique, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", id,
                PATIENT.valeur(), CENTRE_A.valeur(), type, systeme, code, "Libre", java.sql.Date.valueOf(debut), statut, Timestamp.from(T0),
                Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_incoherente_en_base_est_ignoree_par_les_lectures_sans_casser_les_autres() {
        UUID bonne = poser("MEDICAL", "ACTIF", "CIM10", "N18", LocalDate.of(2026, 1, 1));
        UUID typeInconnu = poser("AUTRE", "ACTIF", null, null, LocalDate.of(2026, 1, 2));
        UUID statutInconnu = poser("MEDICAL", "GUERI", null, null, LocalDate.of(2026, 1, 3));
        UUID systemeInconnu = poser("MEDICAL", "ACTIF", "ICD10", "B20", LocalDate.of(2026, 1, 4));
        UUID codeSansSysteme = poser("MEDICAL", "ACTIF", null, "B20", LocalDate.of(2026, 1, 5));
        var page = antecedents().duPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(a -> a.id()).containsExactly(bonne);
        for (UUID illisible : List.of(typeInconnu, statutInconnu, systemeInconnu, codeSansSysteme)) {
            assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
        }
        assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    /**
     * Limite consignée (ADR 0084, verifications-production.md section 49) : une ligne illisible ne peut être ni corrigée ni supprimée par le
     * module ; chaque lecture journalise en WARN son identifiant SEUL, sans type, statut, code, patient ni donnée de santé.
     */
    @Test
    void une_ligne_illisible_est_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("AUTRE", "GUERI", "ICD10", "B20-SECRET", LocalDate.of(2026, 1, 2));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AntecedentsJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(antecedents().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
            antecedents().duPatient(CENTRE_A, PATIENT, 0, 10);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(2).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(e.getFormattedMessage()).contains(illisible.toString()).doesNotContain("B20-SECRET").doesNotContain("GUERI")
                    .doesNotContain(PATIENT.valeur().toString());
        });
    }

    @Test
    void sans_ligne_illisible_aucun_avertissement_n_est_emis() {
        poser("MEDICAL", "ACTIF", "CIM10", "N18", LocalDate.of(2026, 1, 1));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AntecedentsJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(antecedents().duPatient(CENTRE_A, PATIENT, 0, 10).total()).isEqualTo(1);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).isEmpty();
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'antecedents_medicaux'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "center_id", "created_at", "date_debut", "date_fin", "diagnostic_code",
                "diagnostic_code_display", "diagnostic_code_system", "libelle_libre", "note", "patient_id", "severite", "statut_clinique",
                "type_antecedent", "updated_at");
    }

    @Test
    void sans_contrainte_d_unicite_en_base_le_double_ajout_est_accepte_par_l_adapter_le_verrou_et_le_cas_d_usage_l_empechent() {
        antecedents().ajouter(code(CENTRE_A, PATIENT, com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage.CIM10, "N18",
                LocalDate.of(2026, 1, 5)));
        antecedents().ajouter(code(CENTRE_A, PATIENT, com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage.CIM10, "N18",
                LocalDate.of(2026, 1, 6)));
        assertThat(antecedents().duPatient(CENTRE_A, PATIENT, 0, 10).total()).isEqualTo(2);
    }
}
