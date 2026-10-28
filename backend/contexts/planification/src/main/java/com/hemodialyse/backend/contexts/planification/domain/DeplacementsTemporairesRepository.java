package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;

import java.time.LocalDate;
import java.util.List;

/**
 * Port sortant : les déplacements temporaires (un dépôt par racine d'agrégat), seul propriétaire de la table
 * {@code deplacement_temporaire} (décision du 28/10). Toujours borné au centre.
 */
public interface DeplacementsTemporairesRepository {

    /**
     * Enregistre les déplacements dans l'ordre : un seul par patient et par date, un nouveau remplace l'existant du même jour (le
     * dernier d'un même appel l'emporte, KB-PLN-105). Dans la transaction de l'appelant.
     */
    void enregistrer(List<DeplacementTemporaire> deplacements);

    /** Les déplacements du centre sur la période (bornes incluses), par date puis patient. */
    List<DeplacementTemporaire> entre(CentreId centre, LocalDate du, LocalDate au);
}
