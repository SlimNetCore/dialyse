package com.hemodialyse.backend.domain.planning.port;

import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;

import java.time.LocalDate;
import java.util.Set;
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
     * Données du planning avec les fermetures datées comprises entre deux dates (bornes incluses).
     */
    DonneesPlanning chargerPeriode(UUID centerId, LocalDate du, LocalDate au);

    /**
     * Le patient est-il à risque infectieux (dernière sérologie positive pour le VHB, le VHC ou le VIH) ?
     */
    boolean patientARisque(UUID centerId, UUID patientId);

    /**
     * Patients du centre à risque infectieux (dernière sérologie positive pour le VHB, le VHC ou le VIH).
     */
    Set<UUID> patientsARisque(UUID centerId);

    /**
     * Identifiants des salles du centre (validation du paramétrage des salles d'isolement et des affectations).
     */
    Set<UUID> sallesDuCentre(UUID centerId);

    /**
     * Identifiants des créneaux (positions horaires) du centre.
     */
    Set<UUID> creneauxDuCentre(UUID centerId);
}
