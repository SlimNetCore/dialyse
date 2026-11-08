package com.hemodialyse.backend.contexts.seance.infrastructure.acl.absencespatients;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.contexts.seance.application.AbsencesDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * ACL vers le langage publié des Absences ({@link AbsencesDuPilotage}) : traduit les absences du pilotage (hors annulées) en absences
 * du centre au sens du tableau de bord. Aucune lecture de table des Absences.
 */
public class AbsencesDuCentreParLePilotage implements AbsencesDuCentre {

    private final AbsencesDuPilotage pilotage;

    public AbsencesDuCentreParLePilotage(AbsencesDuPilotage pilotage) {
        this.pilotage = pilotage;
    }

    @Override
    public long nombre(CentreId centre, LocalDate du, LocalDate au) {
        return pilotage.nombreHorsAnnulees(centre.valeur(), du, au);
    }

    @Override
    public List<AbsenceDuCentre> liste(CentreId centre, LocalDate du, LocalDate au) {
        return pilotage.horsAnnulees(centre.valeur(), du, au).stream().map(AbsencesDuCentreParLePilotage::traduire).toList();
    }

    static AbsenceDuCentre traduire(AbsencesDuPilotage.AbsenceDuCentre absence) {
        return new AbsenceDuCentre(new PatientId(absence.patient()), absence.dateDeSeance(), absence.statut());
    }
}
