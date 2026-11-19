package com.hemodialyse.backend.contexts.stock.application.consultation.fournisseurs;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.application.pagination.DemandeDePage;
import com.hemodialyse.backend.contexts.stock.application.pagination.PageDeResultats;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;

/**
 * Port sortant : une page des fournisseurs ACTIFS d'un centre (comme la liste complète), filtrée, triée et paginée par
 * l'adapter (en SQL : {@code COUNT} + {@code ORDER BY … LIMIT/OFFSET}).
 */
public interface PagesDesFournisseurs {

    /**
     * Textes en minuscules (« contient ») ; {@code null} : pas de filtre. {@code recherche} porte sur le code OU la raison
     * sociale OU le contact.
     */
    record FiltreDesFournisseurs(String recherche, String code, String raisonSociale, String contact) {
    }

    PageDeResultats<Fournisseur> page(CentreId centre, FiltreDesFournisseurs filtre, DemandeDePage demande);
}
