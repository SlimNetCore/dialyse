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
 * Contrat des lectures de pilotage exécuté contre les adaptateurs JDBC sur H2 (mode PostgreSQL), avec les tables du schéma
 * figé lues par les requêtes : factures, règlements, séances, agences et caisses. Variante PostgreSQL 16 :
 * {@code PilotagePostgresContractIT}.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:facturation-contrat-pilotage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PilotageJdbcContratTest extends PilotageJdbcContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void creerLesTablesEtViderLesLignes() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS factures (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "numero_facture VARCHAR(120) NOT NULL, patient_status_snapshot VARCHAR(120), agence_id_snapshot UUID, "
                + "period_start DATE NOT NULL, period_end DATE NOT NULL, date_facturation DATE NOT NULL, "
                + "tva_rate DECIMAL(5,2) NOT NULL, total_ht DECIMAL(14,2) NOT NULL, total_tva DECIMAL(14,2) NOT NULL, "
                + "total_ttc DECIMAL(14,2) NOT NULL, UNIQUE (center_id, numero_facture))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS facture_reglements (id UUID PRIMARY KEY, facture_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, montant DECIMAL(14,2) NOT NULL, date_reglement DATE NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS seances (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID NOT NULL, "
                + "date_seance DATE NOT NULL, statut VARCHAR(255) NOT NULL, facture_id UUID)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agence (id UUID PRIMARY KEY, center_id UUID NOT NULL, caisse_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, nom VARCHAR(255) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS caisse_assurance (id UUID PRIMARY KEY, center_id UUID NOT NULL, "
                + "code VARCHAR(50) NOT NULL, nom VARCHAR(255) NOT NULL)");
        viderLesLignesDeTest();
    }

    @Override
    protected JdbcTemplate jdbc() {
        return jdbc;
    }
}
