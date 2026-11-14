package com.hemodialyse.backend.contexts.comptabilite.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de la numérotation des documents d'un centre (S-DDL2, ADR 0185, décision D7) : la Comptabilité possède les compteurs
 * par centre et par type de document ({@code app_settings}) ; le Dossier médical (ordonnances) et, par un port fourni, le Stock
 * (bons, inventaires) y réservent leurs numéros.
 */
public interface NumerotationDesDocuments {

    /**
     * Réserve et retourne le numéro suivant du type de document dans le centre : préfixe enregistré (celui par défaut à la première
     * utilisation) suivi du compteur sur cinq chiffres. L'incrément a lieu dans la transaction de l'appelant et la ligne du compteur
     * y reste verrouillée : un refus du document qui annule la transaction ne consomme aucun numéro.
     */
    String suivant(UUID centre, String type, String prefixeParDefaut);
}
