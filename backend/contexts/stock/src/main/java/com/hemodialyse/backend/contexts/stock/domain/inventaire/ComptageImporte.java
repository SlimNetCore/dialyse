package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Une ligne lue dans une feuille de comptage remplie. La ligne visée est donnée par son identifiant (colonne cachée de la feuille),
 * à défaut par le code de l'article et le numéro de lot.
 *
 * @param ligneFichier numéro de la ligne dans le fichier (1 = première ligne)
 * @param quantite     {@code null} si la cellule est vide (ligne ignorée)
 * @param motif        motif d'écart reconnu ({@code null} s'il est vide)
 */
public record ComptageImporte(int ligneFichier, UUID ligneId, String articleCode, String numeroLot, BigDecimal quantite,
                              String motif) {
}

