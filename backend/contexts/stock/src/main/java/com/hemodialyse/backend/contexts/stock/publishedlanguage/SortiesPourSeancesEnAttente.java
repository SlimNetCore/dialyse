package com.hemodialyse.backend.contexts.stock.publishedlanguage;

import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié du Stock à l'inventaire (ADR 0045, T43) : les sorties pour séances non rapprochées. Un inventaire ne s'ouvre pas
 * tant qu'il en reste une, sinon les retours et sorties supplémentaires du rapprochement tomberaient pendant le gel du stock.
 */
public interface SortiesPourSeancesEnAttente {
    /**
     * Référence de la plus ancienne sortie pour séances non rapprochée du centre ; vide si toutes le sont.
     */
    Optional<String> premiereNonRapprochee(UUID centre);
}