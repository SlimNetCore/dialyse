package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant (lecture) : les absences du suivi des absences (déclarées ou détectées), hors annulées, sur une période de dates de
 * séance manquée, bornes incluses. Les absences appartiennent au contexte Absences : lecture par son langage publié.
 */
public interface AbsencesDuCentre {

    long nombre(CentreId centre, LocalDate du, LocalDate au);

    List<AbsenceDuCentre> liste(CentreId centre, LocalDate du, LocalDate au);

    record AbsenceDuCentre(PatientId patient, LocalDate jour, String statut) {
    }
}
