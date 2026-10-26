package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;

/**
 * Retire une affectation du roulement d'un infirmier (même désactivé : le nettoyage reste possible). Sous le verrou de la fiche ;
 * {@code AFFECTATION_INTROUVABLE} si l'affectation n'est pas celle de cet infirmier.
 */
public class RetirerLAffectation {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final RoulementsRepository roulements;

    public RetirerLAffectation(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, RoulementsRepository roulements) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.roulements = roulements;
    }

    public void executer(CentreId centre, InfirmierId infirmier, AffectationId affectation) {
        uniteDeTravail.executer(() -> {
            infirmiers.pourEcriture(centre, infirmier).orElseThrow(ModifierLAffectation::introuvable);
            roulements.de(centre, infirmier).retirer(affectation);
            roulements.retirer(centre, infirmier, affectation);
            return null;
        });
    }
}
