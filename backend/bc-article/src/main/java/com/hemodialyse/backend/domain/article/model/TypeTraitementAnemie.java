package com.hemodialyse.backend.domain.article.model;

/**
 * Marque un article de stock comme relevant du traitement de l'anémie du patient hémodialysé —
 * utilisé pour peupler les listes déroulantes de la prescription médicale (EPO / fer injectable)
 * et pour relier l'administration en séance à une sortie de stock réelle.
 * <p>
 * {@code null} sur un article signifie « article ordinaire, sans lien avec ce traitement ».
 */
public enum TypeTraitementAnemie {
    EPO,
    FER_INJECTABLE
}
