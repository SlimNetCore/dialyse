package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.FormatExport;
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
 * Test d'intégration de l'adaptateur JDBC {@link ExportsSyntheseJdbc}.
 * Vérifie l'isolation multi-centre (centerId).
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-exports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class ExportsSyntheseJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));

    @Autowired
    private ExportSyntheseRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM exports_synthese");
    }

    private ExportSynthese creerExport(CentreId centre) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        ExportSynthese e = ExportSynthese.generer(new ExportSyntheseId(UUID.randomUUID()),
                centre, "2026-03", FormatExport.HTML, "{\"centre\":\"x\"}", maintenant);
        repository.save(e);
        return e;
    }

    @Test
    void save_et_trouver_centre_a() {
        ExportSynthese e = creerExport(CENTRE_A);
        ExportSynthese trouve = repository.trouver(e.id(), CENTRE_A).orElseThrow();
        assertEquals(e.id(), trouve.id());
        assertEquals("2026-03", trouve.periode());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        ExportSynthese e = creerExport(CENTRE_A);
        assertFalse(repository.trouver(e.id(), CENTRE_B).isPresent());
    }
}
