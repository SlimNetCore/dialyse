package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les référentiels communs restants de l'Organisation : les états patient (liste statique). Les payeurs (caisses, agences, centres
 * payeurs) sont servis par Patient et couverture depuis RF3c (ADR 0128).
 */
class ReferentielsCommunsServiceTest {

    @Test
    void lister_etats_patients_statiques() {
        List<EntreeReferentiel> etats = new ReferentielsCommunsService().etatsPatients();
        assertEquals(8, etats.size());
        assertTrue(etats.stream().anyMatch(e -> e.code().equals("PERMANENT")));
    }
}
