package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les six codes réels de la politique d'alertes de la direction, leurs seuils inclusifs, leur gravité et leur valeur. */
class PolitiqueDAlertesTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private static ConstatDuCentre constat(String kt, String hb, boolean observance, long sousSeuil, long perimes, long proches) {
        return new ConstatDuCentre(CENTRE, "Centre", kt == null ? null : new BigDecimal(kt), hb == null ? null : new BigDecimal(hb),
                observance, sousSeuil, perimes, proches);
    }

    private static List<String> codes(ConstatDuCentre c) {
        return PolitiqueDAlertes.lever(c).stream().map(AlerteLevee::code).toList();
    }

    @Test
    void un_centre_sain_ne_leve_aucune_alerte() {
        assertTrue(codes(constat("95", "70", false, 0, 0, 0)).isEmpty());
    }

    @Test
    void les_six_alertes_sont_levees_dans_l_ordre_des_codes_reels() {
        assertEquals(List.of("KTV_CONFORMITE_BASSE", "HB_HORS_CIBLE", "OBSERVANCE_EN_RETARD", "STOCK_SOUS_SEUIL",
                "LOTS_PERIMES", "LOTS_PEREMPTION_PROCHE"), codes(constat("79.9", "49.9", true, 2, 1, 3)));
    }

    @Test
    void un_taux_masque_ne_leve_jamais_d_alerte_de_taux() {
        assertTrue(codes(constat(null, null, false, 0, 0, 0)).isEmpty());
    }

    @Test
    void les_seuils_de_taux_sont_inclusifs_du_minimum() {
        assertTrue(codes(constat("80", "50", false, 0, 0, 0)).isEmpty());
        assertEquals(List.of("KTV_CONFORMITE_BASSE"), codes(constat("79.99", "50", false, 0, 0, 0)));
        assertEquals(List.of("HB_HORS_CIBLE"), codes(constat("80", "49.99", false, 0, 0, 0)));
    }

    @Test
    void la_gravite_et_la_valeur_de_chaque_code_sont_celles_du_legacy() {
        List<AlerteLevee> alertes = PolitiqueDAlertes.lever(constat("60.5", "40", true, 2, 1, 3));
        assertEquals(GraviteDAlerte.WARNING, alertes.get(0).gravite());
        assertEquals(new BigDecimal("60.5"), alertes.get(0).valeur());
        assertEquals(GraviteDAlerte.WARNING, alertes.get(1).gravite());
        assertEquals(new BigDecimal("40"), alertes.get(1).valeur());
        assertEquals(GraviteDAlerte.WARNING, alertes.get(2).gravite());
        assertNull(alertes.get(2).valeur(), "l'observance en retard ne porte pas de valeur");
        assertEquals(GraviteDAlerte.CRITICAL, alertes.get(3).gravite());
        assertEquals(BigDecimal.valueOf(2), alertes.get(3).valeur());
        assertEquals(GraviteDAlerte.CRITICAL, alertes.get(4).gravite());
        assertEquals(BigDecimal.valueOf(1), alertes.get(4).valeur());
        assertEquals(GraviteDAlerte.WARNING, alertes.get(5).gravite());
        assertEquals(BigDecimal.valueOf(3), alertes.get(5).valeur());
        assertTrue(alertes.stream().allMatch(a -> CENTRE.equals(a.centreId()) && "Centre".equals(a.centre())));
    }

    @Test
    void l_horizon_de_peremption_proche_est_de_quatre_vingt_dix_jours() {
        assertEquals(90, PolitiqueDAlertes.PEREMPTION_JOURS);
    }
}
