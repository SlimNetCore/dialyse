package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;

import java.util.UUID;

/**
 * Port de lecture des données du planning d'un centre (toujours borné au centre — AGENTS.md §2).
 */
public interface PlanningDonneesPort {

    /**
     * @param centerId        centre dont on lit les salles, créneaux, générateurs et placements
     * @param patientAIgnorer patient dont le placement actuel ne doit pas être compté (modification de sa fiche) ;
     *                        peut être null
     */
    DonneesPlanning charger(UUID centerId, UUID patientAIgnorer);

    /**
     * Le patient est-il à risque infectieux (dernière sérologie positive pour le VHB, le VHC ou le VIH) ?
     */
    boolean patientARisque(UUID centerId, UUID patientId);

    /**
     * Identifiants des salles du centre (validation du paramétrage des salles d'isolement).
     */
    java.util.Set<UUID> sallesDuCentre(UUID centerId);
}
