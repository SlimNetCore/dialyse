package com.hemodialyse.backend.contexts.facturation.contrat;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Les tables du schéma figé lues et écrites par les ports publiés de la slice F6 (factures, lignes, règlements, payeurs), pour H2.
 * Sur PostgreSQL 16, le schéma est celui de Flyway.
 */
final class SchemaDesFacturesH2 {

    private SchemaDesFacturesH2() {
    }

    static void creer(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "numero_facture VARCHAR(120) NOT NULL, patient_code VARCHAR(120), patient_full_name VARCHAR(255), "
                + "patient_status_snapshot VARCHAR(120), numero_immatriculation_snapshot VARCHAR(120), "
                + "centre_payeur_id_snapshot UUID, agence_id_snapshot UUID, period_start DATE NOT NULL, period_end DATE NOT NULL, "
                + "date_facturation DATE NOT NULL, tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, "
                + "total_tva DECIMAL(14,2) NOT NULL, total_ttc DECIMAL(14,2) NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, UNIQUE (center_id, numero_facture))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_lignes (id UUID PRIMARY KEY, facture_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "forfait_id UUID, forfait_label VARCHAR(255) NOT NULL, unit_price_ht DECIMAL(14,2) NOT NULL, "
                + "seance_count INTEGER NOT NULL, line_ht DECIMAL(14,2) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_reglements (id UUID PRIMARY KEY, facture_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, montant DECIMAL(14,2) NOT NULL, date_reglement DATE NOT NULL, saisi_par VARCHAR(120), "
                + "created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, code_reglement VARCHAR(30), "
                + "mode_reglement VARCHAR(30))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, agence_id UUID, "
                + "code VARCHAR(255), nom VARCHAR(255), adresse VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, caisse_id UUID, "
                + "code VARCHAR(255), nom VARCHAR(255))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), "
                + "nom VARCHAR(255), type_caisse VARCHAR(50))");
    }

    static void vider(JdbcTemplate jdbc, Object... centres) {
        for (String table : new String[]{"facture_reglements", "facture_lignes", "factures"}) {
            for (Object centre : centres) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
        }
    }
}
