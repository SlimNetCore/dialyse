package com.hemodialyse.backend.contexts.stock.domain.commandes;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Port sortant : les bons de commande d'un centre (tables {@code bons_commande} et {@code bons_commande_lignes}, partagées
 * avec la réception legacy jusqu'à la slice S5).
 */
public interface BonsDeCommande {

    /**
     * Enregistre un bon neuf avec la référence {@code BC} suivante du centre, <b>dans une seule transaction</b> : un échec
     * ne consomme aucune référence (KB-STK-20 corrigé). Retourne le bon relu.
     */
    BonDeCommande creer(CentreId centre, Function<String, BonDeCommande> fabrique);

    Optional<BonDeCommande> parId(CentreId centre, UUID id);

    /**
     * Les bons du centre, du plus récent au plus ancien.
     */
    List<BonDeCommande> tous(CentreId centre);

    /**
     * Applique la modification au bon verrouillé (une réception ou une autre modification simultanée attend) et
     * l'enregistre ; vide si le bon est inconnu ou d'un autre centre. Retourne le bon relu.
     */
    Optional<BonDeCommande> modifier(CentreId centre, UUID id, UnaryOperator<BonDeCommande> modification);
}


