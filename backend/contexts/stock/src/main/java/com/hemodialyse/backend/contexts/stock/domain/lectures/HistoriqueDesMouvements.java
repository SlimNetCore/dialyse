package com.hemodialyse.backend.contexts.stock.domain.lectures;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant : l'historique complet des mouvements d'articles d'un centre, mouvements d'un inventaire clôturé compris (la
 * valorisation historique repart des stocks de départ qu'ils posent). Lecture seule.
 */
public interface HistoriqueDesMouvements {

    /**
     * @return pour chaque article qui a des mouvements, ses mouvements dans l'ordre {@link MouvementDeStock#CHRONOLOGIE}
     */
    Map<UUID, List<MouvementDeStock>> parArticle(CentreId centre, Set<UUID> articles);
}
