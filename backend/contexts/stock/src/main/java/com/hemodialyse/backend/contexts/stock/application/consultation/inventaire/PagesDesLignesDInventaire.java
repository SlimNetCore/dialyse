package com.hemodialyse.backend.contexts.stock.application.consultation.inventaire;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;

import java.util.Optional;
import java.util.UUID;

/**
 * Port de lecture paginée de la feuille de comptage d'un inventaire (RG-STK-092) : pagination, tri et filtres exécutés par
 * l'adapter (SQL). L'en-tête de l'inventaire (compteurs, progression, statut) reste lu par sa consultation.
 */
public interface PagesDesLignesDInventaire {

    /** L'inventaire existe-t-il dans ce centre ? (isolation : un inventaire d'un autre centre est inconnu) */
    boolean existe(CentreId centre, UUID inventaire);

    PageDeResultats<LigneDInventaire> lignes(CentreId centre, UUID inventaire, Filtres filtres, DemandeDePage demande);

    /** États de comptage d'une ligne : toutes, à compter (non comptées), avec écart (comptées, écart non nul), comptées. */
    enum EtatDesLignes {
        TOUTES, A_COMPTER, ECARTS, COMPTEES
    }

    /** @param recherche texte normalisé cherché dans le code OU le libellé de l'article OU le n° de lot */
    record Filtres(EtatDesLignes etat, Optional<String> recherche) {
    }
}
