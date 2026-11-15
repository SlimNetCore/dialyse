package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port sortant (DOC5a, ADR 0193) : les consommables SORTIS DU STOCK pour la séance, tels que les mouvements du centre les ont enregistrés,
 * valorisés à leur prix unitaire, dans l'ordre du code d'article. Distinct de {@link ConsommationsDeLaSeance} (qui lit aussi les
 * consommations imputées sur la sortie pour séances) : le cahier n'a jamais imprimé que les mouvements.
 */
public interface SortiesDeLaSeance {

    List<Sortie> deLaSeance(CentreId centre, SeanceId seance);

    /** Article absent du centre : code, libellé et unité nuls. */
    record Sortie(String code, String libelle, String unite, BigDecimal quantite, BigDecimal prixUnitaire, BigDecimal totalValorise) {
    }
}
