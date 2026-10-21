package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant de lecture : les factures émises avec leur situation de règlement (read model, aucune écriture). Tri :
 * date de facturation décroissante puis numéro décroissant, sans départage supplémentaire (KB-FAC-49 reproduit).
 */
public interface SituationsDesFactures {

    PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille);

    /**
     * Toutes les factures du filtre (tableau de bord et export), dans l'ordre de la recherche.
     */
    List<LigneDeSituation> toutes(CriteresDeSituation criteres);

    Optional<LigneDeSituation> deLaFacture(CentreId centre, UUID facture);
}
