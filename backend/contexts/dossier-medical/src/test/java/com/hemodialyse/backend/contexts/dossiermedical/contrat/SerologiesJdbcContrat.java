package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.SerologiesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des sérologies sur la table réelle (H2 et PostgreSQL 16 : sous-classes). */
abstract class SerologiesJdbcContrat extends SerologiesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Schéma H2 de la table (colonnes et types du schéma réel V1 : aucune contrainte d'unicité). */
    static void creerLaTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS serologies_patient (id UUID PRIMARY KEY, center_id UUID NOT NULL, conduite_a_tenir TEXT, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_prelevement DATE NOT NULL, date_prochain_controle DATE, "
                + "laboratoire VARCHAR(255), marqueur VARCHAR(15) NOT NULL, patient_id UUID NOT NULL, resultat VARCHAR(15) NOT NULL, "
                + "titre NUMERIC(10, 3), unite VARCHAR(20), updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM serologies_patient WHERE center_id = ?", centre);
        }
    }

    @Override
    protected Serologies serologies() {
        return new SerologiesJdbc(jdbc());
    }

    private UUID poser(String marqueur, String resultat, LocalDate date) {
        UUID id = UUID.randomUUID();
        jdbc().update("INSERT INTO serologies_patient (id, patient_id, center_id, marqueur, resultat, date_prelevement, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)", id, PATIENT.valeur(), CENTRE_A.valeur(), marqueur, resultat, java.sql.Date.valueOf(date),
                Timestamp.from(T0), Timestamp.from(T0));
        return id;
    }

    @Test
    void une_ligne_incoherente_en_base_est_ignoree_par_les_lectures_sans_casser_les_autres() {
        UUID bonne = poser("TPHA", "NEGATIF", LocalDate.of(2026, 1, 1));
        UUID marqueurInconnu = poser("HIV", "NEGATIF", LocalDate.of(2026, 1, 2));
        UUID resultatInconnu = poser("TPHA", "INDETERMINE", LocalDate.of(2026, 1, 3));
        var page = serologies().duPatient(CENTRE_A, PATIENT, 0, 10);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(s -> s.id()).containsExactly(bonne);
        assertThat(serologies().toutesDuPatient(CENTRE_A, PATIENT)).extracting(s -> s.id()).containsExactly(bonne);
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, marqueurInconnu)).isEmpty();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, resultatInconnu)).isEmpty();
        assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, bonne)).isPresent();
    }

    /**
     * Limite consignée (KB-MED-49, ADR 0083, verifications-production.md section 48 requete 3) : une ligne illisible en base ne peut être
     * ni corrigée ni supprimée par le module (le port ne la retrouve pas, donc le cas d'usage répond « introuvable » ou 204 sans effet) ;
     * chaque lecture journalise en WARN son identifiant SEUL, sans marqueur, résultat, patient ni donnée de santé.
     */
    @Test
    void une_ligne_illisible_est_introuvable_pour_la_correction_et_la_suppression_et_journalisee_avec_son_seul_identifiant() {
        UUID illisible = poser("HIV", "INDETERMINE", LocalDate.of(2026, 1, 2));
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SerologiesJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            assertThat(serologies().trouverEnVerrouillant(CENTRE_A, PATIENT, illisible)).isEmpty();
            serologies().duPatient(CENTRE_A, PATIENT, 0, 10);
            serologies().toutesDuPatient(CENTRE_A, PATIENT);
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(3).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(e.getFormattedMessage()).contains(illisible.toString()).doesNotContain("HIV").doesNotContain("INDETERMINE")
                    .doesNotContain(PATIENT.valeur().toString());
        });
    }

    @Test
    void la_table_garde_ses_colonnes_et_aucune_colonne_n_est_ajoutee() {
        List<String> colonnes = jdbc().queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'serologies_patient'", String.class);
        assertThat(colonnes).containsExactlyInAnyOrder("id", "center_id", "conduite_a_tenir", "created_at", "date_prelevement",
                "date_prochain_controle", "laboratoire", "marqueur", "patient_id", "resultat", "titre", "unite", "updated_at");
    }

    @Test
    void sans_contrainte_d_unicite_en_base_le_double_ajout_est_accepte_par_l_adapter_le_verrou_et_le_cas_d_usage_l_empechent() {
        var premiere = serologie(CENTRE_A, PATIENT, com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique.TPHA,
                LocalDate.of(2026, 1, 5), T0);
        var doublon = serologie(CENTRE_A, PATIENT, com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique.TPHA,
                LocalDate.of(2026, 1, 5), T0.plusSeconds(1));
        serologies().ajouter(premiere);
        serologies().ajouter(doublon);
        assertThat(serologies().toutesDuPatient(CENTRE_A, PATIENT)).hasSize(2);
    }
}
