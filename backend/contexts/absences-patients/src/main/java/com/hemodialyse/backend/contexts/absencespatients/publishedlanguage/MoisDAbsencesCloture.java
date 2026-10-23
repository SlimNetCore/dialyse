package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Événement d'intégration publié quand un administrateur clôture un mois d'absences (A6, ADR 0042). Publié dans la transaction de
 * la clôture (jamais si elle échoue ; Outbox Spring Modulith, ADR 0013). Aucun consommateur de production pour l'instant ; au moins
 * une livraison, consommation idempotente par (centre, mois, instant).
 *
 * @param centre                      le centre dont le mois est clos
 * @param mois                        le mois clos
 * @param cloturePar                  l'utilisateur de la session qui a clôturé
 * @param cloture                     l'instant de la clôture
 * @param absencesAQualifierRestantes absences encore « à qualifier » du mois au moment de la clôture
 * @param absencesEnRetard            parmi elles, celles dont le délai de qualification est dépassé
 */
public record MoisDAbsencesCloture(UUID centre, YearMonth mois, UUID cloturePar, Instant cloture,
                                   long absencesAQualifierRestantes, long absencesEnRetard) {
}
