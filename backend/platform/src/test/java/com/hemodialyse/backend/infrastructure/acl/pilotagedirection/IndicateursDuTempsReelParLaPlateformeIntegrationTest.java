package com.hemodialyse.backend.infrastructure.acl.pilotagedirection;

import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDuTempsReelFournis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PD3 (ADR 0104, revue m3) : le traducteur des indicateurs du temps réel ne fait que traduire ; ce test compare, champ par champ, sa sortie à
 * celle de {@code /indicators} (période par défaut) : centres, marqueurs, stock et alertes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class IndicateursDuTempsReelParLaPlateformeIntegrationTest {

    private static final UUID SOCIETE = UUID.fromString("99997000-0000-0000-0000-000000000011");
    private static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-0000000000b1");

    @Autowired
    private IndicateursDuTempsReelFournis port;
    @Autowired
    private DirectionIndicatorsQueryService indicateurs;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparer() {
        nettoyer();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)",
                SOCIETE, "ZT-TR1", "Société ZT-TR1");
        jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", CENTRE, "ZT-TR1-C", "Centre TR", SOCIETE);
    }

    @AfterEach
    void nettoyer() {
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-TR1%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-TR1%'");
    }

    @Test
    void la_sortie_traduite_reprend_chaque_champ_des_indicateurs_de_la_route() {
        var attendus = indicateurs.indicators(SOCIETE, null, null);
        var traduits = port.pour(SOCIETE);

        assertThat(traduits.centres()).hasSameSizeAs(attendus.centres()).hasSize(1);
        assertThat(traduits.alertes()).isEqualTo(attendus.alertes());
        for (int i = 0; i < attendus.centres().size(); i++) {
            var a = attendus.centres().get(i);
            var t = traduits.centres().get(i);
            assertThat(t.centerId()).isEqualTo(a.centerId());
            assertThat(t.nom()).isEqualTo("Centre TR").isEqualTo(a.nom());

            var ac = a.clinique();
            var tc = t.clinique();
            marqueur(tc.ktV(), ac.ktV());
            marqueur(tc.hemoglobine(), ac.hemoglobine());
            marqueur(tc.phosphore(), ac.phosphore());
            marqueur(tc.pth(), ac.pth());
            marqueur(tc.albumine(), ac.albumine());
            assertThat(tc.vhbPositifs()).isEqualTo(ac.vhbPositifs());
            assertThat(tc.vhcPositifs()).isEqualTo(ac.vhcPositifs());
            assertThat(tc.vihPositifs()).isEqualTo(ac.vihPositifs());
            assertThat(tc.patientsObservanceEnRetard()).isEqualTo(ac.patientsObservanceEnRetard());
            assertThat(tc.greffeListeAttente()).isEqualTo(ac.greffeListeAttente());
            assertThat(tc.greffesPeriode()).isEqualTo(ac.greffesPeriode());

            assertThat(t.stock().articlesSousSeuil()).isEqualTo(a.stock().articlesSousSeuil());
            assertThat(t.stock().lotsPerimes()).isEqualTo(a.stock().lotsPerimes());
            assertThat(t.stock().lotsPeremptionProche()).isEqualTo(a.stock().lotsPeremptionProche());
            assertThat(t.stock().valeurStock()).isEqualTo(a.stock().valeurStock());
        }
    }

    private static void marqueur(IndicateursDuTempsReelFournis.Marqueur traduit, DirectionIndicatorsQueryService.Marker attendu) {
        assertThat(traduit.evalues()).isEqualTo(attendu.evalues());
        assertThat(traduit.pctDansCible()).isEqualTo(attendu.pctDansCible());
    }
}
