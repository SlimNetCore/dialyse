package com.hemodialyse.backend.contexts.absencespatients.contrat;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.application.LectureDesAbsences;
import com.hemodialyse.backend.contexts.absencespatients.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.absencespatients.doubles.SuiviEnMemoire;
import com.hemodialyse.backend.contexts.absencespatients.domain.AbsenceSuivie;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;

import java.time.LocalDate;

/**
 * Le double en mémoire passe le contrat partagé des lectures du suivi.
 */
class LecturesDuSuiviEnMemoireContratTest extends LecturesDuSuiviContrat {

    private final SuiviEnMemoire memoire = new SuiviEnMemoire();

    @Override
    protected LectureDesAbsences lecture() {
        return memoire;
    }

    @Override
    protected SeancesRealisees seances() {
        return memoire;
    }

    @Override
    protected com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients noms() {
        return memoire;
    }

    @Override
    protected void poserLePatient(CentreId centre, PatientId id, String prenom, String nom) {
        memoire.poserLePatient(centre, id, prenom, nom);
    }

    @Override
    protected void poserLAbsence(CentreId centre, AbsenceSuivie absence) {
        memoire.poserLAbsence(centre, absence);
    }

    @Override
    protected void poserLaSeance(CentreId centre, PatientId patient, LocalDate date, String statut) {
        memoire.poserLaSeance(centre, patient, date, statut);
    }
}
