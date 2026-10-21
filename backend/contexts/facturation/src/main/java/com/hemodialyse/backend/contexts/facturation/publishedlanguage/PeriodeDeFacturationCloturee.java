package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Événement d'intégration publié quand un administrateur clôture un mois de facturation (RG-FAC-040, ADR 0037). Publié dans la
 * transaction de la clôture, donc jamais si elle échoue. Aucun consommateur de production pour l'instant (utile aux Absences et au
 * pilotage de la Direction) ; au moins une livraison, consommation idempotente par (centre, mois, instant).
 *
 * @param centre                    le centre dont le mois est clos
 * @param mois                      le mois clos
 * @param cloturePar                l'utilisateur de la session qui a clôturé
 * @param cloturee                  l'instant de la clôture
 * @param seancesEligiblesRestantes séances validées ou signées, non facturées et non exclues du mois au moment de la clôture
 */
public record PeriodeDeFacturationCloturee(UUID centre, YearMonth mois, UUID cloturePar, Instant cloturee,
                                           int seancesEligiblesRestantes) {
}
