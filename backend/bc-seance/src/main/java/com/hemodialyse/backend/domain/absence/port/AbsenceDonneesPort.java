package com.hemodialyse.backend.domain.absence.port;

import com.hemodialyse.backend.domain.absence.model.ValeurAbsence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de sortie : données de planning et de facturation dont dépend le suivi des absences.
 */
public interface AbsenceDonneesPort {

    /**
     * Patients actifs du centre dont le programme prévoit une séance ce jour (centre ouvert, admis, non en sommeil,
     * non transférés) mais sans séance réalisée.
     */
    List<UUID> patientsAttendusSansSeance(UUID centerId, LocalDate date);

    /**
     * Vrai si une séance réalisée (validée, signée ou facturée) existe pour ce patient ce jour.
     */
    boolean seanceRealisee(UUID centerId, UUID patientId, LocalDate date);

    /**
     * Séances réalisées (validées, signées ou facturées) du centre sur une courte période, une ligne par patient et
     * par jour : la grille du planning les marque comme faites.
     */
    List<SeanceRealisee> seancesRealisees(UUID centerId, LocalDate from, LocalDate to);

    record SeanceRealisee(UUID patientId, LocalDate dateSeance) {
    }

    /**
     * Vrai si la facturation du patient est validée pour une période couvrant ce jour : l'absence est alors clôturée
     * (plus de déclaration ni de modification).
     */
    boolean periodeFacturee(UUID centerId, UUID patientId, LocalDate date);

    Optional<String> nomPatient(UUID centerId, UUID patientId);

    /**
     * Centres sur lesquels exécuter le contrôle automatique.
     */
    List<UUID> centresActifs();
}
