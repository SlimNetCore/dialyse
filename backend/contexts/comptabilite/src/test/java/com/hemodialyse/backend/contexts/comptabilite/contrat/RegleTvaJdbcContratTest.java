package com.hemodialyse.backend.contexts.comptabilite.contrat;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;
import com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence.RegleTvaRepositoryJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat des règles de TVA exécuté contre l'adaptateur JDBC sur H2 (mode PostgreSQL). La table {@code tva_types}, tenue par la
 * migration V1 et partagée avec la Facturation, est créée ici avec ses colonnes. Variante PostgreSQL 16 :
 * {@code RegleTvaPostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:comptabilite-contrat-tva;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegleTvaJdbcContratTest extends RegleTvaContrat {

    @Autowired
    private JdbcTemplate jdbc;

    private RegleTvaRepository adaptateur;

    @BeforeEach
    void creerLaTableEtLAdaptateur() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tva_types (id UUID NOT NULL PRIMARY KEY, center_id UUID NOT NULL, "
                + "libelle VARCHAR(255) NOT NULL, taux NUMERIC(5, 2) NOT NULL, "
                + "type_prestation VARCHAR(120) DEFAULT 'HEMODIALYSE' NOT NULL, exonere BOOLEAN DEFAULT FALSE NOT NULL, "
                + "date_debut_validite DATE NOT NULL, date_fin_validite DATE, texte_reference VARCHAR(500), "
                + "actif BOOLEAN DEFAULT TRUE NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                + "created_by VARCHAR(120))");
        jdbc.update("DELETE FROM tva_types");
        adaptateur = new RegleTvaRepositoryJdbc(jdbc);
    }

    @Override
    protected RegleTvaRepository repository() {
        return adaptateur;
    }

    @Override
    protected void regleDesactivee(CentreId centre, String type, String debut) {
        jdbc.update("INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere, date_debut_validite, actif) "
                + "VALUES (?, ?, 'ancienne', 7.00, ?, FALSE, CAST(? AS DATE), FALSE)", UUID.randomUUID(), centre.valeur(), type, debut);
    }
}
