package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Liste les patients suivis du centre (non sortis) avec leur préférence de planification, par nom.
 */
public class ListerLesPreferencesDesPatients {

    private final LectureDesPreferences lecture;

    public ListerLesPreferencesDesPatients(LectureDesPreferences lecture) {
        this.lecture = lecture;
    }

    public PageDeResultats<LectureDesPreferences.LigneDePatient> executer(CentreId centre, Pagination pagination) {
        return lecture.patientsSuivis(centre, pagination);
    }
}
