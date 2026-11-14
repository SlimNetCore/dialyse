package com.hemodialyse.backend.contexts.stock.contrat;

import com.hemodialyse.backend.contexts.stock.infrastructure.persistence.BonsDeSortieDeLaRepriseJdbc;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.BonsDeSortieDeLaReprise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contrat du contrôle des bons de sortie d'un patient repris sur H2 (mode PostgreSQL). La table est créée avec les seules colonnes lues
 * ou nécessaires ({@code id}, {@code center_id}, {@code patient_id}) : les noms réels de colonnes sont ceux de la plateforme.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:stock-reprise;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BonsDeSortieDeLaRepriseJdbcContratTest extends BonsDeSortieDeLaRepriseContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS bons_sortie (id UUID PRIMARY KEY, center_id UUID NOT NULL, patient_id UUID)");
        jdbc.update("DELETE FROM bons_sortie");
    }

    @AfterEach
    void apres() {
        jdbc.update("DELETE FROM bons_sortie");
    }

    @Override
    protected BonsDeSortieDeLaReprise reprise() {
        return new BonsDeSortieDeLaRepriseJdbc(jdbc);
    }

    @Override
    protected void bonDeSortie(UUID id, UUID centre, UUID patient) {
        jdbc.update("INSERT INTO bons_sortie (id, center_id, patient_id) VALUES (?, ?, ?)", id, centre, patient);
    }
}
