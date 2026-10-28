package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.DelaiDeQualification;
import com.hemodialyse.backend.contexts.absencespatients.domain.SyntheseDesAbsences;

import java.time.LocalDate;

/**
 * Synthèse du suivi du centre : absences à qualifier et absences en retard de qualification.
 */
public class ObtenirLaSynthese {

    private final LectureDesAbsences lecture;
    private final HorlogeDesCentres horloge;

    public ObtenirLaSynthese(LectureDesAbsences lecture, HorlogeDesCentres horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    public SyntheseDesAbsences executer(CentreId centre) {
        LocalDate limite = DelaiDeQualification.limite(horloge.aujourdhui(centre));
        return new SyntheseDesAbsences(lecture.nombreAQualifier(centre), lecture.nombreAQualifierAvant(centre, limite));
    }
}
