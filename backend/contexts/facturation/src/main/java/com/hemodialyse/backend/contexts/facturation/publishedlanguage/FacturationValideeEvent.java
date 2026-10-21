package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Événement d'intégration publié après la validation d'une facturation (RG-FAC-032, F14), version 3 (ADR 0035) : de taille bornée,
 * il annonce qu'une émission a eu lieu sur une période d'un centre, sans lister les factures (les consommateurs les relisent en
 * base : la Comptabilité écrit les ventes des factures de la période encore sans écriture, de façon idempotente). Il n'est jamais publié
 * quand aucune facture n'est émise. Publié dans la transaction de la validation, enregistré dans le registre d'événements et
 * traité après son commit ; un arrêt avant l'écouteur laisse les factures sans écriture, rattrapées par
 * {@code POST /api/v1/comptabilite/rebuild}.
 *
 * @param centre            le centre dont la facturation est validée
 * @param debut             début de la période réellement facturée (celle du mois, ou des dates, qui a choisi les séances)
 * @param fin               fin de la période réellement facturée
 * @param nombreDeFactures  nombre de factures émises
 * @param seancesFacturees  nombre de séances passées à l'état facturée
 * @param totalTtc          somme exacte du TTC des factures émises
 * @param survenueLe        l'instant de la validation
 */
public record FacturationValideeEvent(UUID centre, LocalDate debut, LocalDate fin, int nombreDeFactures,
                                      int seancesFacturees, BigDecimal totalTtc, Instant survenueLe) {
}
