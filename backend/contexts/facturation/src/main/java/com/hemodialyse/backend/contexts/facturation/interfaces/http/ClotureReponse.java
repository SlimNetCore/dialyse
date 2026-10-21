package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois.ResultatDeCloture;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de la clôture d'un mois : l'état du mois et l'avertissement chiffré {@code seancesEligiblesRestantes} (séances validées
 * ou signées, non facturées, non exclues du mois, qui ne pourront plus être facturées tant que le mois reste clos).
 */
public record ClotureReponse(UUID centerId, String mois, String statut, UUID cloturePar, Instant clotureLe,
                             int seancesEligiblesRestantes) {

    static ClotureReponse depuis(CentreId centre, UUID auteur, ResultatDeCloture resultat) {
        return new ClotureReponse(centre.valeur(), resultat.mois().toString(), "CLOTURE", auteur, resultat.le(),
                resultat.seancesEligiblesRestantes());
    }
}
