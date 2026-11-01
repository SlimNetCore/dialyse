package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TypeAlerte;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de l'adaptateur JDBC {@link AlertesDirectionJdbc}.
 * Vérifie l'isolation multi-centre (centerId), R2 (unicité par centre et jour) et l'acquittement.
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-alertes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class AlertesDirectionJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));
    private static final LocalDate JOUR = LocalDate.of(2026, 3, 10);

    @Autowired
    private AlerteDirectionRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM alertes_direction");
    }

    private AlerteDirection creerAlerte(CentreId centre, LocalDate date, TypeAlerte type) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T08:00:00Z"), ZoneOffset.UTC);
        AlerteDirection a = AlerteDirection.generer(new AlerteDirectionId(UUID.randomUUID()),
                centre, type, "seuil", "message", date, maintenant);
        repository.save(a);
        return a;
    }

    @Test
    void save_et_trouver_centre_a() {
        AlerteDirection a = creerAlerte(CENTRE_A, JOUR, TypeAlerte.SOUS_EFFECTIF);
        AlerteDirection trouve = repository.trouver(a.id(), CENTRE_A).orElseThrow();
        assertEquals(a.id(), trouve.id());
        assertEquals(TypeAlerte.SOUS_EFFECTIF, trouve.type());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        AlerteDirection a = creerAlerte(CENTRE_A, JOUR, TypeAlerte.SOUS_EFFECTIF);
        assertFalse(repository.trouver(a.id(), CENTRE_B).isPresent());
    }

    @Test
    void existe_par_centre_et_jour() {
        creerAlerte(CENTRE_A, JOUR, TypeAlerte.SOUS_EFFECTIF);
        assertTrue(repository.existeParCentreEtJour(CENTRE_A, JOUR));
        assertFalse(repository.existeParCentreEtJour(CENTRE_A, JOUR.plusDays(1)));
        assertFalse(repository.existeParCentreEtJour(CENTRE_B, JOUR));
    }

    @Test
    void acquitter_persiste_lecture() {
        AlerteDirection a = creerAlerte(CENTRE_A, JOUR, TypeAlerte.SOUS_EFFECTIF);
        OffsetDateTime plusTard = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-10T09:00:00Z"), ZoneOffset.UTC);
        repository.save(a.acquitter(plusTard));
        assertTrue(repository.trouver(a.id(), CENTRE_A).orElseThrow().lue());
    }

    @Test
    void lister_par_centre_est_page_et_filtre_centre() {
        creerAlerte(CENTRE_A, JOUR, TypeAlerte.SOUS_EFFECTIF);
        creerAlerte(CENTRE_A, JOUR.plusDays(1), TypeAlerte.SUR_CAPACITE);
        creerAlerte(CENTRE_B, JOUR, TypeAlerte.SOUS_EFFECTIF);

        PagedResult<AlerteDirection> page = repository.listerParCentre(CENTRE_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
    }
}
