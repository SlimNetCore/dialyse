package com.hemodialyse.backend.contexts.absencespatients.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les deux tables de la clôture mensuelle des absences (migration V44) pour H2. Sur PostgreSQL 16, le schéma est celui de Flyway.
 */
final class SchemaDesMoisDAbsencesH2 {

    private SchemaDesMoisDAbsencesH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS mois_absences (center_id UUID NOT NULL, annee INTEGER NOT NULL, "
                + "mois INTEGER NOT NULL, statut VARCHAR(10) NOT NULL DEFAULT 'OUVERT', cloture_par UUID, "
                + "cloture_le TIMESTAMP WITH TIME ZONE, rouvert_par UUID, rouvert_le TIMESTAMP WITH TIME ZONE, "
                + "motif_reouverture VARCHAR(500), PRIMARY KEY (center_id, annee, mois), "
                + "CONSTRAINT ck_mois_absences_mois CHECK (mois BETWEEN 1 AND 12), "
                + "CONSTRAINT ck_mois_absences_statut CHECK (statut IN ('OUVERT', 'CLOTURE')))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS mois_absences_journal (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "center_id UUID NOT NULL, annee INTEGER NOT NULL, mois INTEGER NOT NULL, type_acte VARCHAR(12) NOT NULL, "
                + "effectue_par UUID NOT NULL, effectue_le TIMESTAMP WITH TIME ZONE NOT NULL, motif VARCHAR(500), "
                + "a_qualifier_restantes BIGINT, en_retard BIGINT, "
                + "CONSTRAINT ck_mois_absences_journal_type CHECK (type_acte IN ('CLOTURE', 'REOUVERTURE')))");
    }

    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            jdbc.update("DELETE FROM mois_absences_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mois_absences WHERE center_id = ?", centre);
        }
    }
}
