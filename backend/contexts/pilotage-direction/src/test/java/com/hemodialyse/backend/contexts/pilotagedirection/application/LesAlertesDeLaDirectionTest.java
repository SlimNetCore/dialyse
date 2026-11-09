package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes.Entree;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cycle de vie de l'historique des alertes (ouverture, figeage, résolution, réapparition) avec le double en mémoire. */
class LesAlertesDeLaDirectionTest {

    private static final UUID SOCIETE = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID AUTRE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CENTRE_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID CENTRE_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 9, 8, 0, 0, 0, ZoneOffset.UTC);

    private final HistoriqueDesAlertesEnMemoire historique = new HistoriqueDesAlertesEnMemoire();
    private final EnregistrerLesAlertes enregistrer = new EnregistrerLesAlertes(historique, new HorlogeDuPilotage(
            java.time.Clock.fixed(Instant.parse("2026-11-09T08:00:00Z"), ZoneOffset.UTC)));
    private final ConsulterLHistoriqueDesAlertes consulter = new ConsulterLHistoriqueDesAlertes(historique);

    private static AlerteLevee alerte(UUID centre, String nom, String code, GraviteDAlerte g, String valeur) {
        return new AlerteLevee(centre, nom, code, g, valeur == null ? null : new BigDecimal(valeur));
    }

    @Test
    void une_alerte_est_ouverte_une_fois_et_reste_ouverte_tant_qu_elle_persiste() {
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, "3")), T0);
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, "3")), T0.plusMinutes(1));

        List<Entree> entrees = consulter.execute(SOCIETE, 10);
        assertEquals(1, entrees.size(), "aucun doublon tant que l'alerte persiste");
        assertNull(entrees.get(0).resolueLe());
    }

    @Test
    void la_valeur_le_nom_du_centre_et_la_gravite_sont_figes_a_la_premiere_apparition() {
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, "3")), T0);
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A renomme", "STOCK_SOUS_SEUIL", GraviteDAlerte.WARNING, "7")), T0.plusMinutes(1));

        Entree e = consulter.execute(SOCIETE, 10).get(0);
        assertEquals("A", e.centre());
        assertEquals(new BigDecimal("3"), e.valeur());
        assertEquals("CRITICAL", e.gravite());
    }

    @Test
    void une_alerte_qui_disparait_est_resolue_puis_sa_reapparition_ouvre_une_nouvelle_ligne() {
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_B, "B", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "1")), T0);
        enregistrer.execute(SOCIETE, List.of(), T0.plusMinutes(5));
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_B, "B", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "4")), T0.plusMinutes(10));

        List<Entree> entrees = consulter.execute(SOCIETE, 10);
        assertEquals(2, entrees.size(), "une résolue, une nouvelle ouverte");
        assertNull(entrees.get(0).resolueLe(), "la nouvelle ouverte est la plus récemment active");
        assertNotNull(entrees.get(1).resolueLe());
    }

    @Test
    void l_historique_d_une_societe_n_est_jamais_touche_par_une_autre() {
        enregistrer.execute(AUTRE, List.of(alerte(CENTRE_A, "Confidentiel", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "9")), T0);
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "1")), T0);
        enregistrer.execute(SOCIETE, List.of(), T0.plusMinutes(1));

        assertEquals(1, consulter.execute(AUTRE, 10).size());
        assertNull(consulter.execute(AUTRE, 10).get(0).resolueLe());
    }

    @Test
    void la_limite_demandee_est_bornee_entre_un_et_deux_cents() {
        for (int i = 0; i < 3; i++) {
            enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "1")), T0.plusMinutes(2L * i));
            enregistrer.execute(SOCIETE, List.of(), T0.plusMinutes(2L * i + 1));
        }
        assertEquals(1, consulter.execute(SOCIETE, 0).size());
        assertEquals(1, consulter.execute(SOCIETE, -5).size());
        assertEquals(2, consulter.execute(SOCIETE, 2).size());
        assertEquals(3, consulter.execute(SOCIETE, 500).size());
    }

    @Test
    void l_horloge_du_pilotage_date_l_enregistrement_quand_aucune_date_n_est_donnee() {
        enregistrer.execute(SOCIETE, List.of(alerte(CENTRE_A, "A", "LOTS_PERIMES", GraviteDAlerte.CRITICAL, "1")));
        assertEquals(T0.toInstant(), consulter.execute(SOCIETE, 1).get(0).vueLe().toInstant());
        assertTrue(historique.nombreDeLignes() == 1);
    }
}
