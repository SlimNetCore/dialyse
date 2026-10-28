package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.Set;

/**
 * Port sortant vers Organisation (ADR 0053) : les salles d'un centre et leur marque d'isolement, qui est une propriété de la
 * salle. Le paramétrage du planning n'écrit jamais la table des salles lui-même.
 */
public interface SallesDuCentre {

    Set<SalleId> toutes(CentreId centre);

    Set<SalleId> sallesDIsolement(CentreId centre);

    /** Marque exactement ces salles comme salles d'isolement du centre ; toutes les autres salles du centre ne le sont plus. */
    void marquerIsolement(CentreId centre, Set<SalleId> sallesDIsolement);
}
