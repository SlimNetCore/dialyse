package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Liste les infirmiers actifs du centre avec leur profil de planification, par nom.
 */
public class ListerLesProfilsDesInfirmiers {

    private final LectureDesPreferences lecture;

    public ListerLesProfilsDesInfirmiers(LectureDesPreferences lecture) {
        this.lecture = lecture;
    }

    public PageDeResultats<LectureDesPreferences.LigneDInfirmier> executer(CentreId centre, Pagination pagination) {
        return lecture.infirmiersActifs(centre, pagination);
    }
}
