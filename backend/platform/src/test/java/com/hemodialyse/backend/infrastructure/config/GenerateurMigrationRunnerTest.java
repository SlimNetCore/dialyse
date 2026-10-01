package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie la migration idempotente de l'ancien référentiel "generateur" vers l'agrégat GMAO
 * {@code Equipement} (module GMAO v2) : l'id d'origine est préservé, le statut est correctement
 * mappé, et exécuter la migration deux fois ne duplique rien.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.datasource.url=jdbc:h2:mem:generateur-migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class GenerateurMigrationRunnerTest {

    @Autowired
    private GenerateurMigrationRunner runner;

    @Autowired
    private EquipementRepositoryPort equipementRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrates_legacy_generateur_rows_preserving_id_and_mapping_statut_idempotently() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS generateur (" +
                "id UUID PRIMARY KEY, salle_id UUID, center_id UUID NOT NULL, " +
                "numero VARCHAR(50) NOT NULL, marque VARCHAR(100), modele VARCHAR(100), " +
                "etat VARCHAR(30) DEFAULT 'FONCTIONNEL')");

        UUID generatorId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();
        UUID salleId = UUID.randomUUID();
        jdbc.update("INSERT INTO generateur (id, salle_id, center_id, numero, marque, modele, etat) " +
                        "VALUES (?, ?, ?, 'G01', 'Fresenius', '5008S', 'EN_PANNE')",
                generatorId, salleId, centreId);

        runner.run();

        Optional<Equipement> migrated = equipementRepository.findById(generatorId);
        assertTrue(migrated.isPresent(), "le générateur doit être migré en Equipement avec le même id");
        assertEquals("G01", migrated.get().getCode());
        assertEquals(centreId, migrated.get().getCentreId());
        assertEquals(salleId, migrated.get().getSalleId());
        // EN_PANNE (ancien référentiel) -> HORS_SERVICE (nouveau statut GMAO)
        assertEquals(StatutEquipement.HORS_SERVICE, migrated.get().getStatut());

        long countBefore = equipementRepository.findByCentreIdAndType(centreId, "GENERATEUR_DIALYSE").size();
        runner.run();
        long countAfter = equipementRepository.findByCentreIdAndType(centreId, "GENERATEUR_DIALYSE").size();

        assertEquals(countBefore, countAfter, "une deuxième exécution ne doit rien dupliquer");
        assertEquals(1, countAfter);
    }
}
