package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement.Resultat;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;
import com.hemodialyse.backend.contexts.planification.domain.Affectation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;

import java.util.List;
import java.util.Set;

/**
 * Remplace la salle, le créneau et les jours d'une affectation. Sous le verrou de la fiche. Ordre des contrôles : affectation de cet
 * infirmier ({@code AFFECTATION_INTROUVABLE}), infirmier actif, salle, créneau, chevauchement.
 */
public class ModifierLAffectation {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final RoulementsRepository roulements;
    private final SallesDuCentre salles;
    private final CreneauxDuCentre creneaux;
    private final SurEffectifDuRoulement surEffectif;
    private final HorlogeDesCentres horloge;

    @SuppressWarnings("java:S107")
    public ModifierLAffectation(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, RoulementsRepository roulements,
                                SallesDuCentre salles, CreneauxDuCentre creneaux, SurEffectifDuRoulement surEffectif,
                                HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.roulements = roulements;
        this.salles = salles;
        this.creneaux = creneaux;
        this.surEffectif = surEffectif;
        this.horloge = horloge;
    }

    public Resultat executer(CentreId centre, InfirmierId infirmier, AffectationId affectation, SalleId salle, CreneauId creneau,
                             Set<JourDeLaSemaine> jours) {
        Affectation modifiee = uniteDeTravail.executer(() -> {
            Infirmier fiche = infirmiers.pourEcriture(centre, infirmier).orElseThrow(ModifierLAffectation::introuvable);
            RoulementDeLInfirmier roulement = roulements.de(centre, infirmier);
            roulement.affectation(affectation).orElseThrow(ModifierLAffectation::introuvable);
            fiche.exigerActifPourLePlanning();
            CaseDuCentre.exiger(salles, creneaux, centre, salle, creneau);
            RoulementDeLInfirmier suivant = roulement.modifier(affectation, salle, creneau, jours);
            Affectation touchee = suivant.affectation(affectation).orElseThrow();
            roulements.remplacer(centre, infirmier, touchee);
            return touchee;
        });
        return new Resultat(Lectures.affectation(modifiee), Lectures.sureffectif(surEffectif, horloge, centre, modifiee));
    }

    static RegleDePlanificationViolee introuvable() {
        return new RegleDePlanificationViolee("AFFECTATION_INTROUVABLE", "Affectation introuvable");
    }
}
