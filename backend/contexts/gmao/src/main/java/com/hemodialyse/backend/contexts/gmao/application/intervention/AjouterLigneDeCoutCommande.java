package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Commande d'ajout d'une ligne de coût. {@code type} est le code reçu ; {@code articleStockId} est opaque : GMAO ne le
 * vérifie pas et ne sort rien du stock (KB-GMAO-07).
 */
public record AjouterLigneDeCoutCommande(InterventionId id, CentreId centre, String type, String libelle, BigDecimal quantite,
                                         BigDecimal prixUnitaire, UUID articleStockId, UtilisateurId par) {
}
