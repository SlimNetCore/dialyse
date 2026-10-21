package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.ModeDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ce que l'appelant demande d'enregistrer : le montant signé (négatif = remboursement), la date (le jour d'exécution si elle est
 * absente, KB-FAC-38), et les ajouts de F4, tous facultatifs : mode, référence de paiement, clé d'idempotence.
 */
public record DemandeDeReglement(BigDecimal montant, LocalDate date, ModeDeReglement mode, String reference, String cleDIdempotence) {

    public DemandeDeReglement {
        Reglement.exigerUnMontantNonNul(montant);
    }
}
