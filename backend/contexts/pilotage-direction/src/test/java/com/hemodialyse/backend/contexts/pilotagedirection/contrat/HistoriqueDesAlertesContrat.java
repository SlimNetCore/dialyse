package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes.Entree;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteOuverte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CleDAlerte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.GraviteDAlerte;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contrat de l'historique des alertes : le double en mémoire et l'adaptateur JDBC passent les MÊMES cas. */
abstract class HistoriqueDesAlertesContrat {

    static final UUID SOCIETE = UUID.fromString("99997000-0000-0000-0000-000000000001");
    static final UUID AUTRE = UUID.fromString("99997000-0000-0000-0000-000000000002");
    static final UUID CENTRE = UUID.fromString("99997000-0000-0000-0000-0000000000a1");
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 9, 8, 0, 0, 0, ZoneOffset.UTC);

    protected abstract HistoriqueDesAlertes historique();

    private static AlerteLevee alerte(String code, GraviteDAlerte g, String valeur) {
        return new AlerteLevee(CENTRE, "Centre", code, g, valeur == null ? null : new BigDecimal(valeur));
    }

    @Test
    void une_alerte_ouverte_est_listee_avec_sa_cle_puis_n_est_plus_ouverte_une_fois_resolue() {
        HistoriqueDesAlertes h = historique();
        h.ouvrir(SOCIETE, alerte("STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, "3"), T0);

        List<AlerteOuverte> ouvertes = h.ouvertes(SOCIETE);
        assertEquals(1, ouvertes.size());
        assertEquals(new CleDAlerte(CENTRE, "STOCK_SOUS_SEUIL"), ouvertes.get(0).cle());

        h.resoudre(ouvertes.get(0).id(), T0.plusHours(1));
        assertTrue(h.ouvertes(SOCIETE).isEmpty());
    }

    @Test
    void les_ouvertes_d_une_societe_ne_contiennent_pas_celles_d_une_autre() {
        HistoriqueDesAlertesContrat.this.historique().ouvrir(AUTRE, alerte("LOTS_PERIMES", GraviteDAlerte.CRITICAL, "9"), T0);
        assertTrue(historique().ouvertes(SOCIETE).isEmpty());
        assertEquals(1, historique().ouvertes(AUTRE).size());
    }

    @Test
    void l_entree_relue_porte_le_nom_la_gravite_la_valeur_et_les_dates_de_l_ouverture() {
        HistoriqueDesAlertes h = historique();
        h.ouvrir(SOCIETE, alerte("KTV_CONFORMITE_BASSE", GraviteDAlerte.WARNING, "60"), T0);
        h.ouvrir(SOCIETE, alerte("OBSERVANCE_EN_RETARD", GraviteDAlerte.WARNING, null), T0.plusHours(1));

        List<Entree> entrees = h.recentes(SOCIETE, 10);
        Entree observance = entrees.get(0);
        assertEquals("OBSERVANCE_EN_RETARD", observance.code());
        assertNull(observance.valeur());
        assertNull(observance.resolueLe());
        Entree ktv = entrees.get(1);
        assertEquals(CENTRE, ktv.centreId());
        assertEquals("Centre", ktv.centre());
        assertEquals("WARNING", ktv.gravite());
        assertEquals(0, new BigDecimal("60").compareTo(ktv.valeur()));
        assertEquals(T0.toInstant(), ktv.vueLe().toInstant());
    }

    @Test
    void les_entrees_sont_du_plus_recemment_actif_au_plus_ancien_et_la_limite_est_respectee() {
        HistoriqueDesAlertes h = historique();
        h.ouvrir(SOCIETE, alerte("STOCK_SOUS_SEUIL", GraviteDAlerte.CRITICAL, "3"), T0.plusHours(1));        // ouverte vue à +1h
        h.ouvrir(SOCIETE, alerte("LOTS_PERIMES", GraviteDAlerte.CRITICAL, "2"), T0.plusHours(2));            // résolue à +3h
        h.ouvrir(SOCIETE, alerte("KTV_CONFORMITE_BASSE", GraviteDAlerte.WARNING, "60"), T0);                  // résolue à +10h
        UUID perimes = h.ouvertes(SOCIETE).stream().filter(o -> o.cle().code().equals("LOTS_PERIMES")).findFirst().orElseThrow().id();
        UUID ktv = h.ouvertes(SOCIETE).stream().filter(o -> o.cle().code().equals("KTV_CONFORMITE_BASSE")).findFirst().orElseThrow().id();
        h.resoudre(perimes, T0.plusHours(3));
        h.resoudre(ktv, T0.plusHours(10));

        assertEquals(List.of("KTV_CONFORMITE_BASSE", "LOTS_PERIMES", "STOCK_SOUS_SEUIL"),
                h.recentes(SOCIETE, 10).stream().map(Entree::code).toList());
        assertEquals(List.of("KTV_CONFORMITE_BASSE", "LOTS_PERIMES"), h.recentes(SOCIETE, 2).stream().map(Entree::code).toList());
    }

    @Test
    void les_entrees_d_une_societe_ne_contiennent_pas_celles_d_une_autre() {
        historique().ouvrir(AUTRE, alerte("LOTS_PERIMES", GraviteDAlerte.CRITICAL, "9"), T0);
        assertTrue(historique().recentes(SOCIETE, 10).isEmpty());
    }
}
