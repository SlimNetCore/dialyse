package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers.AffectationLue;
import com.hemodialyse.backend.contexts.planification.domain.Affectation;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Traductions agrégat → read model des cas d'usage du roulement.
 */
final class Lectures {
    private Lectures() {
    }

    static AffectationLue affectation(Affectation a) {
        return new AffectationLue(a.id().valeur(), a.salle(), a.creneau(), a.jours().stream().sorted().toList());
    }

    /**
     * Avertissement de sur-effectif de l'affectation : un échec du calcul ne fait jamais échouer l'écriture (liste vide).
     */
    static List<JourDeLaSemaine> sureffectif(SurEffectifDuRoulement port, HorlogeDesCentres horloge, CentreId centre, Affectation a) {
        try {
            Set<String> jours = a.jours().stream().map(Enum::name).collect(Collectors.toSet());
            return port.joursEnSureffectif(centre.valeur(), a.salle().valeur(), a.creneau().valeur(), jours, horloge.aujourdhui(centre)).stream()
                    .map(JourDeLaSemaine::valueOf).toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
