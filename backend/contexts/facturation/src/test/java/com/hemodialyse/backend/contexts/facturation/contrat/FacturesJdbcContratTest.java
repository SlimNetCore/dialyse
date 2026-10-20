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
 * Contrat des factures exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL), avec les tables du schéma figé (dont la
 * contrainte d'unicité du numéro par centre et la colonne {@code emise_par} de la migration V40). Variante PostgreSQL 16 :
 * {@code FacturesPostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-factures;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FacturesJdbcContratTest extends FacturesJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "numero_facture VARCHAR(120) NOT NULL, patient_code VARCHAR(80), patient_full_name VARCHAR(255), "
                + "patient_status_snapshot VARCHAR(120), numero_immatriculation_snapshot VARCHAR(120), "
                + "centre_payeur_id_snapshot UUID, agence_id_snapshot UUID, period_start DATE NOT NULL, period_end DATE NOT NULL, "
                + "date_facturation DATE NOT NULL, tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, "
                + "total_tva DECIMAL(14,2) NOT NULL, total_ttc DECIMAL(14,2) NOT NULL, "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, emise_par UUID, "
                + "UNIQUE (center_id, numero_facture))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_lignes (id UUID PRIMARY KEY, facture_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "forfait_id UUID, forfait_label VARCHAR(255) NOT NULL, unit_price_ht DECIMAL(14,2) NOT NULL, "
                + "seance_count INTEGER NOT NULL, line_ht DECIMAL(14,2) NOT NULL)");
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
