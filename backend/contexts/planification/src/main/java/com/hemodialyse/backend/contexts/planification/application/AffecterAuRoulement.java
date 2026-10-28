package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;
import com.hemodialyse.backend.contexts.planification.domain.Affectation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;

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
 * Affecte un infirmier actif à une salle et un créneau pour certains jours (RG-INF-020 à 022). Sous le verrou de sa fiche
 * (409 si l'écriture est concurrente, KB-PLN-25). Ordre des contrôles : infirmier, actif, salle, créneau, chevauchement. L'avertissement
 * de sur-effectif est calculé APRÈS l'enregistrement, sans jamais le faire échouer.
 */
public class AffecterAuRoulement {
    private final UniteDeTravail uniteDeTravail;
    private final InfirmiersRepository infirmiers;
    private final RoulementsRepository roulements;
    private final SallesDuCentre salles;
    private final CreneauxDuCentre creneaux;
    private final NouveauxIdentifiantsDuPlanning identifiants;
    private final SurEffectifDuRoulement surEffectif;
    private final HorlogeDesCentres horloge;

    @SuppressWarnings("java:S107")
    public AffecterAuRoulement(UniteDeTravail uniteDeTravail, InfirmiersRepository infirmiers, RoulementsRepository roulements,
                               SallesDuCentre salles, CreneauxDuCentre creneaux, NouveauxIdentifiantsDuPlanning identifiants,
                               SurEffectifDuRoulement surEffectif, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.infirmiers = infirmiers;
        this.roulements = roulements;
        this.salles = salles;
        this.creneaux = creneaux;
        this.identifiants = identifiants;
        this.surEffectif = surEffectif;
        this.horloge = horloge;
    }

    /**
     * @param joursEnSureffectif jours de la semaine courante où l'affectation porte la case au-dessus de l'effectif requis
     */
    public record Resultat(AffectationLue affectation, List<JourDeLaSemaine> joursEnSureffectif) {
    }

    public Resultat executer(CentreId centre, InfirmierId infirmier, SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
        Affectation creee = uniteDeTravail.executer(() -> {
            Infirmier fiche = infirmiers.pourEcriture(centre, infirmier).orElseThrow(
                    () -> new RegleDePlanificationViolee("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
            fiche.exigerActifPourLePlanning();
            CaseDuCentre.exiger(salles, creneaux, centre, salle, creneau);
            AffectationId id = identifiants.affectation();
            RoulementDeLInfirmier suivant = roulements.de(centre, infirmier).affecter(id, salle, creneau, jours);
            Affectation nouvelle = suivant.affectation(id).orElseThrow();
            roulements.ajouter(centre, infirmier, nouvelle);
            return nouvelle;
        });
        return new Resultat(Lectures.affectation(creee), Lectures.sureffectif(surEffectif, horloge, centre, creee));
    }
}
