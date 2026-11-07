package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.PlanningDesPatients;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlanningFourni;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;

/** ACL du planning : traduit le langage publié {@link PlanningFourni} en {@link SituationDuPlanning} ; patient inconnu : 404. */
public class PlanningDesPatientsParLeLegacy implements PlanningDesPatients {

    private final PlanningFourni planning;

    public PlanningDesPatientsParLeLegacy(PlanningFourni planning) {
        this.planning = planning;
    }

    @Override
    public SituationDuPlanning situation(CentreId centre, PatientId patient, LocalDate jour) {
        return planning.situation(centre.valeur(), patient.valeur(), jour).map(PlanningDesPatientsParLeLegacy::traduire)
                .orElseThrow(PatientIntrouvable::new);
    }

    static SituationDuPlanning traduire(PlanningFourni.Situation s) {
        return new SituationDuPlanning(s.centreFerme(), s.jourDeDialyse(), s.enSommeil(), s.etatDuPatient(), s.dateDeLEtat(),
                s.dateDAdmission());
    }
}
