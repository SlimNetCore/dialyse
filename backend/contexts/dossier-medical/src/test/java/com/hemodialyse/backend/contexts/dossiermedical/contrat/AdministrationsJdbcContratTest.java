package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Administrations;
import com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.AdministrationsJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Contrat des administrations sur H2 (mode PostgreSQL). Variante PostgreSQL 16 : {@code AdministrationsPostgresContractIT}. */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:dossier-medical-administrations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdministrationsJdbcContratTest extends AdministrationsContrat {

    @Autowired
    private JdbcTemplate jdbc;

    /** Schéma H2 de la table (colonnes et longueurs du schéma réel V1). */
    static void creerLaTable(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS administrations_anemie (id UUID PRIMARY KEY, patient_id UUID NOT NULL, center_id UUID NOT NULL, "
                + "prescription_medicale_id UUID, type_traitement VARCHAR(20) NOT NULL, molecule VARCHAR(100), dose NUMERIC(10, 3), "
                + "unite_dose VARCHAR(20), voie VARCHAR(10), date_administration DATE NOT NULL, seance_id UUID, administre_par VARCHAR(100), "
                + "administree BOOLEAN NOT NULL, motif_non_administration VARCHAR(500), article_id UUID, quantite_article NUMERIC(10, 3), "
                + "created_at TIMESTAMP WITH TIME ZONE NOT NULL)");
    }

    static void vider(JdbcTemplate jdbc) {
        for (Object centre : new Object[]{CENTRE_A.valeur(), CENTRE_B.valeur()}) {
            jdbc.update("DELETE FROM administrations_anemie WHERE center_id = ?", centre);
        }
    }

    @BeforeEach
    void avant() {
        creerLaTable(jdbc);
        vider(jdbc);
    }

    @AfterEach
    void apres() {
        vider(jdbc);
    }

    @Override
    protected Administrations administrations() {
        return new AdministrationsJdbc(jdbc);
    }
}
