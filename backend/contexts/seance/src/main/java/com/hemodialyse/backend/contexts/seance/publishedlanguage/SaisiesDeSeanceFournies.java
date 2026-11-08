package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, SE6, ADR 0093) : l'effacement des saisies tenues par le legacy pour une séance
 * supprimée. Chaque opération est bornée au centre, sans effet si la saisie n'existe pas, et participe à l'unité de travail appelante
 * (aucune transaction propre : tout ou rien avec la suppression). Le fournisseur n'efface rien d'autre.
 */
public interface SaisiesDeSeanceFournies {

    /** Efface les administrations de traitement (EPO, fer) de la séance. */
    void effacerLesAdministrations(UUID centre, UUID seance);

    /** Efface le volet paramédical de la séance. */
    void effacerLeVoletParamedical(UUID centre, UUID seance);

    /** Efface le volet médical de la séance. */
    void effacerLeVoletMedical(UUID centre, UUID seance);
}
