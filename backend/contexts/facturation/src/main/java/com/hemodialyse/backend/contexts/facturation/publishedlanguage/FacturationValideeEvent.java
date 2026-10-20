package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Événement d'intégration publié après la validation d'une facturation (RG-FAC-032, F14), version 2 (KB-FAC-27/28/29 corrigés) :
 * il décrit exactement ce qui a été émis. Il n'est jamais publié quand aucune facture n'est émise. Publié dans la transaction de
 * la validation et traité après son commit (livraison au moins une fois : les consommateurs sont idempotents).
 *
 * @param centre           le centre dont la facturation est validée
 * @param debut            début de la période réellement facturée (celle du mois, ou des dates, qui a choisi les séances)
 * @param fin              fin de la période réellement facturée
 * @param factures         les factures émises, avec leurs montants
 * @param seancesFacturees nombre de séances passées à l'état facturée
 * @param totalTtc         somme exacte du TTC des factures émises
 * @param survenueLe       l'instant de la validation
 */
public record FacturationValideeEvent(UUID centre, LocalDate debut, LocalDate fin, List<FactureEmise> factures,
                                      int seancesFacturees, BigDecimal totalTtc, Instant survenueLe) {

    public FacturationValideeEvent {
        factures = List.copyOf(factures);
    }

    /**
     * Une facture émise : son identité, son numéro et ses montants figés.
     */
    public record FactureEmise(UUID id, String numero, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc) {
    }
}
