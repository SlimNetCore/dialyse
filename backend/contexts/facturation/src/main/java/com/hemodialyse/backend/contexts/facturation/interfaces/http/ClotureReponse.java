package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.cloture.CloturerLeMois.ResultatDeCloture;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Réponse de la clôture d'un mois : l'état du mois et l'avertissement chiffré {@code seancesEligiblesRestantes} (séances validées
 * ou signées, non facturées, non exclues du mois, qui ne pourront plus être facturées tant que le mois reste clos) et, champ additif
 * de la slice A6 (ADR 0042), {@code absencesDuMoisNonCloses} : vrai si le mois d'absences du centre n'est pas clos (avertissement
 * seulement, la clôture de facturation n'est jamais refusée pour cela).
 */
public record ClotureReponse(UUID centerId, String mois, String statut, UUID cloturePar, Instant clotureLe,
                             int seancesEligiblesRestantes, boolean absencesDuMoisNonCloses) {

    static ClotureReponse depuis(CentreId centre, UUID auteur, ResultatDeCloture resultat) {
        return new ClotureReponse(centre.valeur(), resultat.mois().toString(), "CLOTURE", auteur, resultat.le(),
                resultat.seancesEligiblesRestantes(), resultat.absencesDuMoisNonCloses());
    }
}
