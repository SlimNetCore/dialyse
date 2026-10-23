package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.absences;

import com.hemodialyse.backend.contexts.facturation.application.cloture.AbsencesDuMois;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ClotureDesAbsences;

import java.time.YearMonth;

/**
 * L'état du mois d'absences vient du langage publié que le module Absences fournit, propriétaire de ses mois (ADR 0042).
 */
public final class AbsencesDuMoisPubliees implements AbsencesDuMois {

    private final ClotureDesAbsences absences;

    public AbsencesDuMoisPubliees(ClotureDesAbsences absences) {
        this.absences = absences;
    }

    @Override
    public boolean sontCloses(CentreId centre, YearMonth mois) {
        return absences.moisClos(centre.valeur(), mois);
    }
}
