package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.comptabilite;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.PeriodesComptables;
import com.hemodialyse.backend.contexts.facturation.application.cloture.ClotureComptable;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.YearMonth;

/**
 * L'état de la période comptable vient du langage publié de la Comptabilité, propriétaire de ses périodes (RG-CPT-005) : la
 * Facturation la connaît, pas l'inverse (ADR 0035).
 */
public class ClotureComptableComptabilite implements ClotureComptable {

    private final PeriodesComptables periodes;

    public ClotureComptableComptabilite(PeriodesComptables periodes) {
        this.periodes = periodes;
    }

    @Override
    public boolean estCloturee(CentreId centre, YearMonth mois) {
        return periodes.estCloturee(centre.valeur(), mois);
    }
}
