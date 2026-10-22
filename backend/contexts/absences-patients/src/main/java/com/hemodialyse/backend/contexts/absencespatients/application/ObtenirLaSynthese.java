package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.DelaiDeQualification;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.SyntheseDesAbsences;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Synthèse du suivi du centre : absences à qualifier et absences en retard de qualification.
 */
public class ObtenirLaSynthese {

    private final LectureDesAbsences lecture;
    private final Clock horloge;

    public ObtenirLaSynthese(LectureDesAbsences lecture, Clock horloge) {
        this.lecture = lecture;
        this.horloge = horloge;
    }

    public SyntheseDesAbsences executer(CentreId centre) {
        LocalDate limite = DelaiDeQualification.limite(LocalDate.now(horloge));
        return new SyntheseDesAbsences(lecture.nombreAQualifier(centre), lecture.nombreAQualifierAvant(centre, limite));
    }
}
