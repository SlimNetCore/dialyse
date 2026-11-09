package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, SE6, ADR 0093 ; réduit par SE7f, ADR 0100) : l'effacement des administrations de
 * traitement (EPO, fer) d'une séance supprimée, données du Dossier médical que la Séance ne peut pas atteindre directement (le Dossier
 * médical dépend de la Séance). Bornée au centre, sans effet si rien n'existe, participe à l'unité de travail appelante (aucune transaction
 * propre). Les volets paramédical et médical sont effacés par la Séance elle-même depuis SE7f.
 */
public interface SaisiesDeSeanceFournies {

    /** Efface les administrations de traitement (EPO, fer) de la séance. */
    void effacerLesAdministrations(UUID centre, UUID seance);
}
