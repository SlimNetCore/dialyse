package com.hemodialyse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le profil {@code demo-renadial} charge le jeu de test RENADIAL au démarrage, en plus du seed habituel (jamais à
 * sa place) — voir {@code application-demo-renadial.yml} et {@code db/demo/README.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "demo-renadial"})
class DemoRenadialProfileValidationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void the_profile_loads_renadial_at_startup() {
        Integer societes = jdbc.queryForObject("SELECT COUNT(*) FROM societes WHERE code = 'RENADIAL'", Integer.class);
        assertEquals(1, societes);
        Integer centres = jdbc.queryForObject(
                "SELECT COUNT(*) FROM centers WHERE societe_id = (SELECT id FROM societes WHERE code='RENADIAL')", Integer.class);
        assertEquals(17, centres);
        Integer patients = jdbc.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE center_id IN (SELECT id FROM centers WHERE societe_id = "
                        + "(SELECT id FROM societes WHERE code='RENADIAL'))", Integer.class);
        assertTrue(patients > 400, "patients=" + patients);
        // le seed habituel doit toujours être présent aussi (le profil AJOUTE, ne remplace pas)
        Integer autres = jdbc.queryForObject("SELECT COUNT(*) FROM societes WHERE code <> 'RENADIAL'", Integer.class);
        assertTrue(autres >= 2, "le seed habituel (GHNE, CLR...) doit toujours être chargé, autres=" + autres);
    }
}
