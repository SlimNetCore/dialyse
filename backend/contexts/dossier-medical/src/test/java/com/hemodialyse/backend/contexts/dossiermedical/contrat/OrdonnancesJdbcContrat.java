package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.application.NumerotationDesOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.NumerotationDesOrdonnancesJdbc;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.OrdonnancesJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat des ordonnances et de la numérotation sur les tables réelles (H2 et PostgreSQL 16 : sous-classes). */
abstract class OrdonnancesJdbcContrat extends OrdonnancesContrat {

    protected abstract JdbcTemplate jdbc();

    /** Schéma H2 des trois tables (colonnes et contraintes du schéma réel V1). */
    static void creerLesTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ordonnances (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL, date_prescription DATE NOT NULL, medecin_id VARCHAR(100), "
                + "numero VARCHAR(30), patient_id UUID NOT NULL, signed_at TIMESTAMP WITH TIME ZONE, statut VARCHAR(20) NOT NULL, "
                + "updated_at TIMESTAMP WITH TIME ZONE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS lignes_ordonnance (id UUID PRIMARY KEY, duree_jours INTEGER, instructions VARCHAR(500), "
                + "libelle VARCHAR(255), medicament_code VARCHAR(20), medicament_code_display VARCHAR(255), "
                + "medicament_code_system VARCHAR(10), ordonnance_id UUID NOT NULL, posologie VARCHAR(255) NOT NULL, quantite INTEGER, "
                + "voie VARCHAR(50))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS app_settings (center_id UUID NOT NULL, cle VARCHAR(50) NOT NULL, "
                + "dernier_compteur BIGINT NOT NULL, prefixe VARCHAR(20) NOT NULL, PRIMARY KEY (center_id, cle))");
    }

    protected void vider() {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc().update("DELETE FROM lignes_ordonnance WHERE ordonnance_id IN (SELECT id FROM ordonnances WHERE center_id = ?)", centre);
            jdbc().update("DELETE FROM ordonnances WHERE center_id = ?", centre);
            jdbc().update("DELETE FROM app_settings WHERE center_id = ? AND cle = 'SEQ_ORD'", centre);
        }
    }

    @Override
    protected Ordonnances ordonnances() {
        return new OrdonnancesJdbc(jdbc());
    }

    @Test
    void une_ordonnance_au_statut_illisible_est_ignoree_par_la_lecture_et_journalisee_avec_son_seul_identifiant() {
        java.util.UUID illisible = java.util.UUID.randomUUID();
        java.sql.Timestamp t0 = java.sql.Timestamp.from(java.time.Instant.parse("2026-11-07T10:00:00Z"));
        jdbc().update("INSERT INTO ordonnances (id, center_id, created_at, date_prescription, medecin_id, patient_id, statut, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?)", illisible, CENTRE_A.valeur(), t0, java.sql.Date.valueOf("2026-11-07"), "medecin-fictif",
                java.util.UUID.fromString("00000000-0000-0000-0000-0000000d7001"), "STATUT_DISPARU", t0);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OrdonnancesJdbc.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            var page = ordonnances().duPatient(CENTRE_A, new com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId(
                    java.util.UUID.fromString("00000000-0000-0000-0000-0000000d7001")), 0, 10);
            assertThat(page.items()).isEmpty();
        } finally {
            logger.detachAppender(capture);
        }
        assertThat(capture.list).hasSize(1);
        assertThat(capture.list.getFirst().getFormattedMessage()).contains(illisible.toString()).doesNotContain("STATUT_DISPARU")
                .doesNotContain("medecin-fictif");
    }

    @Test
    void la_numerotation_est_sequentielle_par_centre_sur_la_ligne_seq_ord() {
        NumerotationDesOrdonnances numerotation = new NumerotationDesOrdonnancesJdbc(jdbc());
        assertThat(numerotation.suivant(CENTRE_A)).isEqualTo("ORD-00001");
        assertThat(numerotation.suivant(CENTRE_A)).isEqualTo("ORD-00002");
        assertThat(numerotation.suivant(CENTRE_B)).isEqualTo("ORD-00001");
        assertThat(jdbc().queryForObject("SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = 'SEQ_ORD'", Long.class,
                CENTRE_A.valeur())).isEqualTo(2L);
        assertThat(jdbc().queryForObject("SELECT prefixe FROM app_settings WHERE center_id = ? AND cle = 'SEQ_ORD'", String.class,
                CENTRE_A.valeur())).isEqualTo("ORD-");
    }
}
