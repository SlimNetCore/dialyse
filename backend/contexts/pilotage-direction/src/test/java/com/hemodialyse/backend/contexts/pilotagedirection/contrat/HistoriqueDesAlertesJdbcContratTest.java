package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.HistoriqueDesAlertesJdbc;
import com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence.SocieteDUnCentreJdbc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Historique des alertes sur H2 (mode PostgreSQL), table calquée sur la V1 (aucune contrainte d'unicité sur le centre et le code). */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:pilotage-direction-alertes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HistoriqueDesAlertesJdbcContratTest extends HistoriqueDesAlertesContrat {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void avant() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS direction_alert_history (id UUID PRIMARY KEY, societe_id UUID NOT NULL, "
                + "center_id UUID NOT NULL, centre_nom VARCHAR(200) NOT NULL, code VARCHAR(60) NOT NULL, severity VARCHAR(20) NOT NULL, "
                + "valeur NUMERIC(14, 2), first_seen_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL, "
                + "resolved_at TIMESTAMP WITH TIME ZONE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS centers (id UUID PRIMARY KEY, societe_id UUID)");
        jdbc.execute("DELETE FROM direction_alert_history");
        jdbc.execute("DELETE FROM centers");
    }

    @AfterEach
    void apres() {
        jdbc.execute("DELETE FROM direction_alert_history");
        jdbc.execute("DELETE FROM centers");
    }

    @Override
    protected HistoriqueDesAlertes historique() {
        return new HistoriqueDesAlertesJdbc(jdbc);
    }

    @Test
    void deux_ouvertures_concurrentes_du_meme_centre_et_code_creent_deux_lignes_course_reproduite_PD3_R2() {
        var h = historique();
        var alerte = new com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee(CENTRE, "Centre", "LOTS_PERIMES",
                com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte.CRITICAL, java.math.BigDecimal.ONE);
        h.ouvrir(SOCIETE, alerte, T0);
        h.ouvrir(SOCIETE, alerte, T0);
        assertEquals(2, h.ouvertes(SOCIETE).size(), "aucune contrainte d'unicité en base : le rapprochement est la seule garde");
    }

    @Test
    void la_societe_d_un_centre_est_lue_sur_centers_et_un_centre_inconnu_est_vide() {
        jdbc.update("INSERT INTO centers (id, societe_id) VALUES (?, ?)", CENTRE, SOCIETE);
        var societes = new SocieteDUnCentreJdbc(jdbc);
        assertEquals(SOCIETE, societes.de(CENTRE).orElseThrow());
        assertTrue(societes.de(UUID.fromString("99997000-0000-0000-0000-0000000000ff")).isEmpty());
    }
}
