package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Les absences des infirmiers du centre, des plus récentes aux plus anciennes (lecture seule).
 */
public class ListerLesAbsences {
    private final AbsencesRepository absences;

    public ListerLesAbsences(AbsencesRepository absences) {
        this.absences = absences;
    }

    public PageDeResultats<AbsenceLue> executer(CentreId centre, Pagination pagination) {
        var lues = absences.page(centre, pagination);
        return new PageDeResultats<>(lues.elements().stream().map(AbsenceLue::depuis).toList(), lues.total(), lues.page(), lues.taille());
    }
}
