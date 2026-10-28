package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;

/**
 * Les absences de l'infirmier connecté, et d'aucun autre (« Mon planning »).
 */
public class ListerMesAbsences {
    private final InfirmiersRepository infirmiers;
    private final AbsencesRepository absences;

    public ListerMesAbsences(InfirmiersRepository infirmiers, AbsencesRepository absences) {
        this.infirmiers = infirmiers;
        this.absences = absences;
    }

    public PageDeResultats<AbsenceLue> executer(CentreId centre, CompteId compte, Pagination pagination) {
        var lues = absences.pageDeLInfirmier(centre, FicheLiee.de(infirmiers, centre, compte).id(), pagination);
        return new PageDeResultats<>(lues.elements().stream().map(AbsenceLue::depuis).toList(), lues.total(), lues.page(), lues.taille());
    }
}
