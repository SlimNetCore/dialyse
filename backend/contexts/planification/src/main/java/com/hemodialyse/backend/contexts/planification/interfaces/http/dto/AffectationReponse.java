package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.application.AffecterAuRoulement.Resultat;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.List;
import java.util.UUID;

/**
 * @param joursEnSureffectif jours de la semaine courante où l'affectation porte la case au-dessus de l'effectif requis (avertissement)
 */
public record AffectationReponse(UUID id, UUID salleId, UUID creneauId, List<JourDeLaSemaine> jours,
                                 List<JourDeLaSemaine> joursEnSureffectif) {
    public static AffectationReponse depuis(AffectationLue a) {
        return new AffectationReponse(a.id(), a.salle().valeur(), a.creneau().valeur(), a.jours(), List.of());
    }

    public static AffectationReponse depuis(Resultat r) {
        return new AffectationReponse(r.affectation().id(), r.affectation().salle().valeur(), r.affectation().creneau().valeur(),
                r.affectation().jours(), r.joursEnSureffectif());
    }
}
