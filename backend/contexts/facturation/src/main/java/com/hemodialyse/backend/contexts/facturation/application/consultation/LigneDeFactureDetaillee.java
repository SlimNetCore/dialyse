package com.hemodialyse.backend.contexts.facturation.application.consultation;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une ligne figée d'une facture (RG-FAC-047) : HT stocké, TVA de ligne = HT × taux figé de la facture arrondi au centime demi-supérieur,
 * TTC = HT + TVA.
 */
public record LigneDeFactureDetaillee(UUID forfaitId, String forfaitLibelle, BigDecimal prixUnitaireHt, int nombreDeSeances,
                                      BigDecimal montantHt, BigDecimal montantTva, BigDecimal montantTtc) {
}
