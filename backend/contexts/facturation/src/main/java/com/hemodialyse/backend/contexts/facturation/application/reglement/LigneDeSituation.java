package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.reglement.EtatDeReglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.TypeDeSolde;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une facture avec sa situation de règlement (lecture calculée, F13) : montants au centime, nombre de règlements et code du
 * dernier. Les noms des composants sont ceux du JSON historique.
 */
public record LigneDeSituation(UUID factureId, String numeroFacture, String numeroAssurance, String patientNom,
                               String patientPrenom, UUID caisseId, String caisse, UUID agenceId, String agence,
                               UUID centrePayeurId, String centrePayeur, LocalDate dateFacturation, BigDecimal montantFacture,
                               BigDecimal montantRegle, BigDecimal reste, BigDecimal tropPercu, EtatDeReglement etat,
                               TypeDeSolde soldeType, String latestCodeReglement, long paymentCount) {
}
