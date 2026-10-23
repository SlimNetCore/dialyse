package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.FiltreDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.absencespatients.domain.Pagination;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Modèle de lecture du suivi des absences (CQRS léger) : lectures seules, toujours bornées au centre. Adaptateur sur la
 * table {@code absence_patient} ; le nom des patients vient du port {@link NomsDesPatients}.
 */
public interface LectureDesAbsences {

    /**
     * Liste filtrée, de la date de séance la plus récente à la plus ancienne puis par identifiant.
     */
    PageDeResultats<AbsenceSuivie> lister(CentreId centre, FiltreDesAbsences filtre, Pagination pagination);

    /**
     * Absences non annulées dont la séance a lieu entre ces deux dates incluses, par date puis identifiant.
     */
    List<AbsenceSuivie> absencesNonAnnuleesEntre(CentreId centre, LocalDate du, LocalDate au);

    long nombreAQualifier(CentreId centre);

    /**
     * Absences « à qualifier » dont la séance précède strictement cette date.
     */
    long nombreAQualifierAvant(CentreId centre, LocalDate limite);

    /**
     * Absences « à qualifier » dont la séance tombe entre ces deux dates incluses (compte de la notification de nuit).
     */
    long nombreAQualifierEntre(CentreId centre, LocalDate du, LocalDate au);

    /**
     * L'absence de ce centre ; vide si elle n'existe pas ou appartient à un autre centre.
     */
    Optional<AbsenceSuivie> absence(CentreId centre, AbsenceId id);

    /**
     * Dates de séance déjà utilisées pour rattraper une absence (rattrapée) de ce patient : une séance ne rattrape qu'une
     * absence. Une absence annulée ne réserve rien.
     */
    List<LocalDate> datesDeRattrapageUtilisees(CentreId centre, PatientId patient);
}
