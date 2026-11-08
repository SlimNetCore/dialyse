package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.absencespatients.application.ExclusionsDeSeances;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.LiensDExclusionFournis;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL des Absences de patients vers la Séance (ADR 0098, SE7d) : une absence vient d'une exclusion tant qu'une séance du centre garde le
 * lien vers elle, quel que soit le statut de la séance (KB-SEA-81, reproduit). Lien lu par {@link LiensDExclusionFournis}. Remplace
 * {@code legacyseance/ExclusionsDeSeancesJdbc}.
 */
public class ExclusionsDeSeancesPubliees implements ExclusionsDeSeances {

    private final LiensDExclusionFournis liens;

    public ExclusionsDeSeancesPubliees(LiensDExclusionFournis liens) {
        this.liens = liens;
    }

    @Override
    public boolean absenceIssueDUneExclusion(CentreId centre, AbsenceId absence) {
        return liens.absenceLiee(centre.valeur(), absence.valeur());
    }
}
