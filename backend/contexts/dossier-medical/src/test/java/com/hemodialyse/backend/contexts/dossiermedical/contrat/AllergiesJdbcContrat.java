package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AllergiesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des allergies sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class AllergiesJdbcContrat extends AllergiesContrat {
    protected abstract JdbcTemplate jdbc();

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM allergies_patient WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Allergies allergies() {
        return new AllergiesJdbc(jdbc());
    }

    private UUID poser(String categorie, String criticite, String reaction, String statut, String systeme, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO allergies_patient (id, patient_id, center_id, categorie, criticite, type_reaction, statut_verification, "
                        + "substance_code_system, substance_code, date_constatation, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", id,
                PATIENT.valeur(), CENTRE_A.valeur(), categorie, criticite, reaction, statut, systeme, "arachide", java.sql.Date.valueOf(date),
                Timestamp.from(T0), Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_incoherente_en_base_est_ignoree_par_les_lectures_sans_casser_les_autres() {
        UUID bonne = poser("ALIMENT", "HAUTE", "ALLERGIE", "SUSPECTEE", "LOCAL", LocalDate.of(2026, 1, 1));
        UUID categorie = poser("AUTRE", "HAUTE", "ALLERGIE", "SUSPECTEE", "LOCAL", LocalDate.of(2026, 1, 2));
        UUID criticite = poser("ALIMENT", "MOYENNE", "ALLERGIE", "SUSPECTEE", "LOCAL", LocalDate.of(2026, 1, 3));
        UUID reaction = poser("ALIMENT", "HAUTE", "ANAPHYLAXIE", "SUSPECTEE", "LOCAL", LocalDate.of(2026, 1, 4));
        UUID statut = poser("ALIMENT", "HAUTE", "ALLERGIE", "PROBABLE", "LOCAL", LocalDate.of(2026, 1, 5));
        UUID systeme = poser("ALIMENT", "HAUTE", "ALLERGIE", "SUSPECTEE", "SNOMED", LocalDate.of(2026, 1, 6));
        UUID sansSysteme = poser("ALIMENT", "HAUTE", "ALLERGIE", "SUSPECTEE", null, LocalDate.of(2026, 1, 7));
        var page = allergies().duPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(a -> a.id()).containsExactly(bonne);
        assertThat(allergies().critiquesDuPatient(CENTRE_A, PATIENT)).extracting(a -> a.id()).containsExactly(bonne);
        for (UUID illisible : List.of(categorie, criticite, reaction, statut, systeme, sansSysteme)) {
            assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
        }
        assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    @Test
    void un_code_importe_en_minuscules_est_rendu_en_majuscules_comme_le_legacy() {
        UUID id = poser("ALIMENT", "BASSE", "ALLERGIE", "SUSPECTEE", "LOCAL", LocalDate.of(2026, 1, 1));
        assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, id).orElseThrow().substance().code()).isEqualTo("ARACHIDE");
    }

    /**
     * Limite consignée (ADR 0084, verifications-production.md section 49) : une ligne illisible ne peut être ni corrigée ni supprimée par le
     * module ; chaque lecture journalise en WARN son identifiant SEUL, sans catégorie, criticité, code, patient ni donnée de santé.
     */
    @Test
    void une_ligne_illisible_est_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("AUTRE", "MOYENNE", "ANAPHYLAXIE", "PROBABLE", "SNOMED", LocalDate.of(2026, 1, 2));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AllergiesJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(allergies().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
            allergies().duPatient(CENTRE_A, PATIENT, 0, 10);
            allergies().critiquesDuPatient(CENTRE_A, PATIENT);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(3).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(e.getFormattedMessage()).contains(illisible.toString()).doesNotContain("ANAPHYLAXIE").doesNotContain("SNOMED")
                    .doesNotContain("arachide").doesNotContain(PATIENT.valeur().toString());
        });
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'allergies_patient'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "categorie", "center_id", "created_at", "criticite", "date_constatation",
                "manifestations", "patient_id", "statut_verification", "substance_code", "substance_code_display", "substance_code_system",
                "type_reaction", "updated_at");
    }
}
