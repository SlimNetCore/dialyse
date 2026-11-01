package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Test d'intégration de l'adaptateur JDBC {@link TableauxDeBordJdbc}.
 * Vérifie l'isolation multi-centre (centerId).
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-tableaux;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class TableauxDeBordJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));

    @Autowired
    private TableauDeBordRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tableaux_de_bord");
    }

    private OffsetDateTime maintenant() {
        return OffsetDateTime.ofInstant(java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    void save_et_trouver_centre_a() {
        repository.save(TableauDeBord.actualiser(CENTRE_A, "{\"seances\":10}", maintenant()));
        TableauDeBord trouve = repository.trouver(CENTRE_A).orElseThrow();
        assertEquals("{\"seances\":10}", trouve.indicateurs());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        repository.save(TableauDeBord.actualiser(CENTRE_A, "{\"seances\":10}", maintenant()));
        assertFalse(repository.trouver(CENTRE_B).isPresent());
    }

    @Test
    void actualiser_ecrase_sans_dupliquer() {
        repository.save(TableauDeBord.actualiser(CENTRE_A, "{\"seances\":10}", maintenant()));
        repository.save(TableauDeBord.actualiser(CENTRE_A, "{\"seances\":12}", maintenant()));
        assertEquals("{\"seances\":12}", repository.trouver(CENTRE_A).orElseThrow().indicateurs());
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM tableaux_de_bord WHERE center_id = ?",
                Integer.class, CENTRE_A.valeur());
        assertEquals(1, n);
    }
}
