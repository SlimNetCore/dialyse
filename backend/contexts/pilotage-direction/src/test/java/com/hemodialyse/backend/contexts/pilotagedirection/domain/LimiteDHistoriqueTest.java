package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimiteDHistoriqueTest {

    @Test
    void une_limite_hors_bornes_est_ramenee_a_la_borne_sans_etre_refusee() {
        assertEquals(1, LimiteDHistorique.bornee(0).valeur());
        assertEquals(1, LimiteDHistorique.bornee(-5).valeur());
        assertEquals(200, LimiteDHistorique.bornee(500).valeur());
        assertEquals(200, LimiteDHistorique.bornee(Integer.MAX_VALUE).valeur());
    }

    @Test
    void une_limite_dans_les_bornes_est_conservee() {
        assertEquals(1, LimiteDHistorique.bornee(1).valeur());
        assertEquals(50, LimiteDHistorique.bornee(LimiteDHistorique.PAR_DEFAUT).valeur());
        assertEquals(200, LimiteDHistorique.bornee(200).valeur());
    }

    @Test
    void on_ne_peut_pas_construire_une_limite_invalide() {
        assertThrows(IllegalArgumentException.class, () -> new LimiteDHistorique(0));
        assertThrows(IllegalArgumentException.class, () -> new LimiteDHistorique(201));
    }
}
