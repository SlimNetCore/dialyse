package com.hemodialyse.backend.contexts.stock.domain.sortie;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonDeSortieTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final OffsetDateTime CREE_LE = OffsetDateTime.parse("2026-10-23T08:00:00Z");

    private static BonDeSortie bon(UUID seance) {
        return BonDeSortie.nouveau(UUID.randomUUID(), CENTRE, "BS-00001", seance, null, "SALLE_A", LocalDate.of(2026, 10, 22), "u",
                CREE_LE, List.of(new LigneDeSortie(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                        BigDecimal.TEN)));
    }

    @Test
    void un_bon_manuel_n_est_lie_a_aucune_seance() {
        assertFalse(bon(BonDeSortie.AUCUNE_SEANCE).estLieeAUneSeance());
        assertTrue(bon(UUID.randomUUID()).estLieeAUneSeance());
        assertFalse(BonDeSortie.estLieeAUneSeance(null));
    }

    @Test
    void une_correction_garde_l_identite_la_reference_et_l_heure_de_saisie() {
        BonDeSortie bon = bon(BonDeSortie.AUCUNE_SEANCE);
        UUID patient = UUID.randomUUID();
        BonDeSortie corrige = bon.corriger(bon.seanceId(), patient, "P2", LocalDate.of(2026, 10, 21), "autre", List.of());
        assertEquals(bon.id(), corrige.id());
        assertEquals(bon.reference(), corrige.reference());
        assertEquals(CREE_LE, corrige.creeLe());
        assertEquals(patient, corrige.patientId());
        assertEquals("P2", corrige.poste());
        assertEquals("autre", corrige.creePar());
        assertTrue(corrige.lignes().isEmpty());
        assertEquals(1, bon.lignes().size());
    }

    @Test
    void la_date_de_sortie_d_un_bon_neuf_est_obligatoire() {
        assertThrows(NullPointerException.class, () -> BonDeSortie.nouveau(UUID.randomUUID(), CENTRE, "BS-1",
                BonDeSortie.AUCUNE_SEANCE, null, null, null, "u", CREE_LE, List.of()));
    }
}

