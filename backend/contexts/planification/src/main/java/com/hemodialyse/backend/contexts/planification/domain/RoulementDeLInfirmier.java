package com.hemodialyse.backend.contexts.planification.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Le roulement d'un infirmier (racine d'agrégat, RG-INF-020 à 022) : ses affectations hebdomadaires. Invariant d'ensemble (P14) :
 * jamais deux affectations sur le même créneau un même jour, même dans deux salles. Immuable : chaque intention rend le roulement
 * suivant. Un roulement relu garde ses données héritées (deux affectations déjà chevauchantes, KB-PLN-25) : on peut les retirer, on ne
 * peut pas modifier l'une d'elles tant qu'elle chevauche l'autre.
 */
public final class RoulementDeLInfirmier {
    private final CentreId centre;
    private final InfirmierId infirmier;
    private final List<Affectation> affectations;

    private RoulementDeLInfirmier(CentreId centre, InfirmierId infirmier, List<Affectation> affectations) {
        if (centre == null || infirmier == null) {
            throw new SaisieInvalide("Roulement incomplet");
        }
        this.centre = centre;
        this.infirmier = infirmier;
        this.affectations = List.copyOf(affectations);
    }

    /**
     * Le roulement relu de la persistance (aucune règle rejouée).
     */
    public static RoulementDeLInfirmier reconstituer(CentreId centre, InfirmierId infirmier, List<Affectation> affectations) {
        return new RoulementDeLInfirmier(centre, infirmier, affectations);
    }

    public RoulementDeLInfirmier affecter(AffectationId id, SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
        Affectation nouvelle = new Affectation(id, salle, creneau, jours);
        exigerSansChevauchement(nouvelle);
        List<Affectation> suivantes = new ArrayList<>(affectations);
        suivantes.add(nouvelle);
        return new RoulementDeLInfirmier(centre, infirmier, suivantes);
    }

    public RoulementDeLInfirmier modifier(AffectationId id, SalleId salle, CreneauId creneau, Set<JourDeLaSemaine> jours) {
        exigerConnue(id);
        Affectation modifiee = new Affectation(id, salle, creneau, jours);
        exigerSansChevauchement(modifiee);
        return new RoulementDeLInfirmier(centre, infirmier,
                affectations.stream().map(a -> a.id().equals(id) ? modifiee : a).toList());
    }

    /** Une case habituelle voulue par une proposition d'optimisation : salle et créneau. */
    public record CaseVoulue(SalleId salle, CreneauId creneau) {
    }

    /**
     * Ce qu'il faut écrire pour atteindre le roulement voulu : seules les lignes touchées (P22).
     *
     * @param suivant  le roulement une fois appliqué
     * @param ajoutees nouvelles affectations
     * @param modifiees affectations dont les jours changent
     * @param retirees affectations qui disparaissent
     */
    public record Changements(RoulementDeLInfirmier suivant, List<Affectation> ajoutees, List<Affectation> modifiees,
                              List<AffectationId> retirees) {
        public boolean estVide() {
            return ajoutees.isEmpty() && modifiees.isEmpty() && retirees.isEmpty();
        }
    }

    /**
     * Remplace le roulement par celui d'une proposition d'optimisation : les affectations identiques (même salle, même créneau)
     * sont conservées, celles dont les jours changent modifiées, les autres supprimées, les cases nouvelles créées. Le roulement
     * obtenu est JUGÉ comme toute autre écriture : jamais deux affectations sur le même créneau un même jour.
     *
     * @param voulu les jours voulus par case ; une case sans jour n'est pas voulue
     * @param ids   fournit l'identité des affectations créées
     * @throws RegleDePlanificationViolee {@code AFFECTATION_CHEVAUCHEMENT}
     */
    public Changements remplacerPar(java.util.Map<CaseVoulue, Set<JourDeLaSemaine>> voulu,
                                    java.util.function.Supplier<AffectationId> ids) {
        List<Affectation> ajoutees = new ArrayList<>();
        List<Affectation> modifiees = new ArrayList<>();
        List<AffectationId> retirees = new ArrayList<>();
        List<Affectation> suivantes = new ArrayList<>();
        java.util.Set<CaseVoulue> traitees = new java.util.HashSet<>();
        for (Affectation a : affectations) {
            CaseVoulue cle = new CaseVoulue(a.salle(), a.creneau());
            Set<JourDeLaSemaine> jours = voulu.get(cle);
            if (jours == null || jours.isEmpty() || !traitees.add(cle)) {
                retirees.add(a.id());
            } else if (!jours.equals(a.jours())) {
                Affectation modifiee = new Affectation(a.id(), a.salle(), a.creneau(), jours);
                modifiees.add(modifiee);
                suivantes.add(modifiee);
            } else {
                suivantes.add(a);
            }
        }
        for (java.util.Map.Entry<CaseVoulue, Set<JourDeLaSemaine>> souhait : voulu.entrySet()) {
            if (traitees.contains(souhait.getKey()) || souhait.getValue().isEmpty()) continue;
            Affectation nouvelle = new Affectation(ids.get(), souhait.getKey().salle(), souhait.getKey().creneau(), souhait.getValue());
            ajoutees.add(nouvelle);
            suivantes.add(nouvelle);
        }
        for (int i = 0; i < suivantes.size(); i++) {
            for (int j = i + 1; j < suivantes.size(); j++) {
                if (suivantes.get(i).chevauche(suivantes.get(j))) {
                    throw new RegleDePlanificationViolee("AFFECTATION_CHEVAUCHEMENT",
                            "L'infirmier est déjà affecté à ce créneau l'un de ces jours");
                }
            }
        }
        return new Changements(new RoulementDeLInfirmier(centre, infirmier, suivantes), List.copyOf(ajoutees), List.copyOf(modifiees),
                List.copyOf(retirees));
    }

    public RoulementDeLInfirmier retirer(AffectationId id) {
        exigerConnue(id);
        return new RoulementDeLInfirmier(centre, infirmier, affectations.stream().filter(a -> !a.id().equals(id)).toList());
    }

    /**
     * L'affectation de cet infirmier, si elle existe.
     */
    public Optional<Affectation> affectation(AffectationId id) {
        return affectations.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * Vrai si l'infirmier est prévu ce jour-là sur ce créneau, dans n'importe quelle salle.
     */
    public boolean prevuSurLeCreneau(CreneauId creneau, JourDeLaSemaine jour) {
        return affectations.stream().anyMatch(a -> a.creneau().equals(creneau) && a.jours().contains(jour));
    }

    /**
     * Vrai si l'infirmier est prévu ce jour-là dans cette salle sur ce créneau.
     */
    public boolean prevuSurLaCase(SalleId salle, CreneauId creneau, JourDeLaSemaine jour) {
        return affectations.stream().anyMatch(a -> a.salle().equals(salle) && a.creneau().equals(creneau) && a.jours().contains(jour));
    }

    private void exigerConnue(AffectationId id) {
        if (affectation(id).isEmpty()) {
            throw new RegleDePlanificationViolee("AFFECTATION_INTROUVABLE", "Affectation introuvable");
        }
    }

    private void exigerSansChevauchement(Affectation candidate) {
        if (affectations.stream().anyMatch(candidate::chevauche)) {
            throw new RegleDePlanificationViolee("AFFECTATION_CHEVAUCHEMENT",
                    "L'infirmier est déjà affecté à ce créneau l'un de ces jours");
        }
    }

    public CentreId centre() {
        return centre;
    }

    public InfirmierId infirmier() {
        return infirmier;
    }

    public List<Affectation> affectations() {
        return affectations;
    }
}
