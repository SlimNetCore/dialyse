package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.PilotageDirectionTestApplication;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de l'adaptateur JDBC {@link InstantanesMensuelsJdbc}.
 * Vérifie l'isolation multi-centre (centerId) et R1 (un instantané par centre et mois).
 */
@SpringBootTest(classes = PilotageDirectionTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:pilotage-instantanes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema.sql"
        })
class InstantanesMensuelsJdbcIntegrationTest {

    private static final CentreId CENTRE_A = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000a"));
    private static final CentreId CENTRE_B = new CentreId(UUID.fromString("99997300-0000-0000-0000-00000000000b"));

    @Autowired
    private InstantaneMensuelRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM instantanes_mensuels");
    }

    private InstantaneMensuel creerInstantane(CentreId centre, int annee, int mois) {
        OffsetDateTime maintenant = OffsetDateTime.ofInstant(
                java.time.Instant.parse("2026-03-31T23:00:00Z"), ZoneOffset.UTC);
        InstantaneMensuel i = InstantaneMensuel.capturer(new InstantaneMensuelId(UUID.randomUUID()),
                centre, Mois.de(annee, mois), "{\"seances\":120}", maintenant);
        repository.save(i);
        return i;
    }

    @Test
    void save_et_trouver_centre_a() {
        InstantaneMensuel i = creerInstantane(CENTRE_A, 2026, 3);
        InstantaneMensuel trouve = repository.trouver(i.id(), CENTRE_A).orElseThrow();
        assertEquals(i.id(), trouve.id());
        assertTrue(trouve.fige());
    }

    @Test
    void trouver_centre_b_ne_retourne_pas_donnees_centre_a() {
        InstantaneMensuel i = creerInstantane(CENTRE_A, 2026, 3);
        assertFalse(repository.trouver(i.id(), CENTRE_B).isPresent());
    }

    @Test
    void existe_par_centre_et_mois() {
        creerInstantane(CENTRE_A, 2026, 3);
        assertTrue(repository.existeParCentreEtMois(CENTRE_A, Mois.de(2026, 3)));
        assertFalse(repository.existeParCentreEtMois(CENTRE_A, Mois.de(2026, 4)));
        assertFalse(repository.existeParCentreEtMois(CENTRE_B, Mois.de(2026, 3)));
    }

    @Test
    void lister_par_centre_est_page_et_filtre_centre() {
        creerInstantane(CENTRE_A, 2026, 3);
        creerInstantane(CENTRE_A, 2026, 2);
        creerInstantane(CENTRE_B, 2026, 3);

        PagedResult<InstantaneMensuel> page = repository.listerParCentre(CENTRE_A, 0, 10);
        assertEquals(2, page.items().size());
        assertEquals(2, page.total());
    }
}
