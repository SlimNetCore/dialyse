package com.hemodialyse.backend.contexts.facturation.contrat;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contrat des règlements exécuté contre les adaptateurs JDBC sur H2 (mode PostgreSQL), avec les tables du schéma figé : les
 * factures, les règlements (colonnes de la migration V41 et index unique de la clé d'idempotence) et les tables du référentiel
 * jointes par la lecture. Variante PostgreSQL 16 : {@code ReglementsPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-reglements;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReglementsJdbcContratTest extends ReglementsJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        creerLesTables(jdbc);
        viderLesLignesDeTest();
    }

    static void creerLesTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "numero_facture VARCHAR(120) NOT NULL, patient_code VARCHAR(80), patient_full_name VARCHAR(255), "
                + "patient_status_snapshot VARCHAR(120), numero_immatriculation_snapshot VARCHAR(120), "
                + "centre_payeur_id_snapshot UUID, agence_id_snapshot UUID, period_start DATE NOT NULL, period_end DATE NOT NULL, "
                + "date_facturation DATE NOT NULL, tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, "
                + "total_tva DECIMAL(14,2) NOT NULL, total_ttc DECIMAL(14,2) NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, emise_par UUID, "
                + "UNIQUE (center_id, numero_facture))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_reglements (id UUID PRIMARY KEY, facture_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, montant DECIMAL(14,2) NOT NULL, date_reglement DATE NOT NULL, saisi_par VARCHAR(120), "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, code_reglement VARCHAR(30), "
                + "mode_reglement VARCHAR(20), reference_paiement VARCHAR(120), cle_idempotence VARCHAR(120))");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_facture_reglements_cle_idempotence "
                + "ON facture_reglements (center_id, facture_id, cle_idempotence)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS patients (id UUID PRIMARY KEY, center_id UUID NOT NULL, numero_assurance VARCHAR(255), "
                + "nom VARCHAR(255), prenom VARCHAR(255), centre_payeur_id UUID)");
        // PL8b : les colonnes lues par l'annuaire des payeurs (code, adresse, type_caisse) en plus du nom
        jdbc.execute("CREATE TABLE IF NOT EXISTS centre_payeur (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), "
                + "nom VARCHAR(255), adresse VARCHAR(255), agence_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), "
                + "nom VARCHAR(255), caisse_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, code VARCHAR(255), "
                + "nom VARCHAR(255), type_caisse VARCHAR(50))");
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
