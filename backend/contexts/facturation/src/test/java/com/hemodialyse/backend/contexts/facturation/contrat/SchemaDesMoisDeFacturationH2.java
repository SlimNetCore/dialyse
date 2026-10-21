package com.hemodialyse.backend.contexts.facturation.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les deux tables de la clôture mensuelle (migration V42) pour H2. Sur PostgreSQL 16, le schéma est celui de Flyway.
 */
final class SchemaDesMoisDeFacturationH2 {

    private SchemaDesMoisDeFacturationH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS periodes_facturation (center_id UUID NOT NULL, annee INTEGER NOT NULL, "
                + "mois INTEGER NOT NULL, statut VARCHAR(10) NOT NULL DEFAULT 'OUVERT', cloturee_par UUID, "
                + "cloturee_le TIMESTAMP WITH TIME ZONE, rouverte_par UUID, rouverte_le TIMESTAMP WITH TIME ZONE, "
                + "motif_reouverture VARCHAR(500), PRIMARY KEY (center_id, annee, mois), "
                + "CONSTRAINT ck_periodes_facturation_mois CHECK (mois BETWEEN 1 AND 12), "
                + "CONSTRAINT ck_periodes_facturation_statut CHECK (statut IN ('OUVERT', 'CLOTURE')))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS periodes_facturation_journal (id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, "
                + "center_id UUID NOT NULL, annee INTEGER NOT NULL, mois INTEGER NOT NULL, type_acte VARCHAR(12) NOT NULL, "
                + "effectue_par UUID NOT NULL, effectue_le TIMESTAMP WITH TIME ZONE NOT NULL, motif VARCHAR(500), "
                + "seances_eligibles_restantes INTEGER, "
                + "CONSTRAINT ck_periodes_facturation_journal_type CHECK (type_acte IN ('CLOTURE', 'REOUVERTURE')))");
    }

    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (Object centre : centres) {
            jdbc.update("DELETE FROM periodes_facturation_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM periodes_facturation WHERE center_id = ?", centre);
        }
    }
}
