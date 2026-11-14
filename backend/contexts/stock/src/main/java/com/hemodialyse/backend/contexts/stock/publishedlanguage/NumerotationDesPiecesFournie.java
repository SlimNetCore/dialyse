package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.UUID;

/**
 * Port FOURNI AU Stock (S-DDL2, ADR 0185, décision D7) : la numérotation des pièces d'un centre (bons de commande, de réception, de
 * sortie, inventaires, sorties pour séances), dont les compteurs appartiennent à la Comptabilité. Le numéro est réservé dans la
 * transaction en cours de l'appelant : un refus de la pièce n'en consomme aucun. Implémenté par la Comptabilité (qui dépend du Stock,
 * jamais l'inverse) à partir de son langage publié {@code NumerotationDesDocuments}.
 */
public interface NumerotationDesPiecesFournie {

    /** Numéro suivant du type de pièce ({@code cle}) : préfixe enregistré (ou par défaut) puis compteur sur cinq chiffres. */
    String suivante(UUID centre, String cle, String prefixeParDefaut);
}
