package com.hemodialyse.backend.domain.stock.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ligne lue dans une feuille de comptage remplie.
 *
 * @param ligneFichier numéro de ligne dans le fichier (pour les messages)
 * @param ligneId      identifiant de la ligne d'inventaire (colonne cachée), {@code null} si absent ou illisible
 * @param articleCode  code article (rapprochement de secours avec le n° de lot)
 * @param numeroLot    n° de lot, {@code null} si vide
 * @param quantite     quantité comptée, {@code null} si la cellule est vide (ligne ignorée)
 * @param motif        code du motif d'écart, {@code null} si vide
 */
public record ComptageImporte(int ligneFichier, UUID ligneId, String articleCode, String numeroLot, BigDecimal quantite,
                              String motif) {
}

