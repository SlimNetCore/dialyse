package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
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
