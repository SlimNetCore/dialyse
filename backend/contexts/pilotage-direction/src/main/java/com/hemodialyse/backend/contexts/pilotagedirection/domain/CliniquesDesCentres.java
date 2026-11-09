package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Les indicateurs cliniques de chaque centre demandé et leur consolidé. Le consolidé est calculé par le Dossier médical sur les valeurs
 * BRUTES de tous les centres puis masqué (KB-MED-104, KB-DIR-01 : reproduit, à confirmer par le DPO avant le 2026-12-15) : le Pilotage ne le
 * recalcule pas à partir des centres masqués.
 */
public record CliniquesDesCentres(Map<UUID, IndicateursCliniquesDuCentre> parCentre, IndicateursCliniquesDuCentre consolides) {

    public CliniquesDesCentres {
        parCentre = Map.copyOf(parCentre);
    }
}
