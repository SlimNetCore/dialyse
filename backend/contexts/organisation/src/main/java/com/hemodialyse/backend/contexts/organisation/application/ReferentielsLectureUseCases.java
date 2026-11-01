package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.util.List;
import java.util.UUID;

/**
 * Port d'entrée (use cases) des lectures référentielles croisées (slice O5).
 * Agrège les langages publiés des contextes propriétaires (Stock, GMAO) pour les
 * listes déroulantes : aucune écriture, lecture seule (R4).
 */
public interface ReferentielsLectureUseCases {

    PagedResult<ArticleLu> listerArticles(CentreId centreId, int page, int size);

    List<GenerateurLu> listerGenerateurs(CentreId centreId);

    record ArticleLu(UUID id, String code, String libelle, String unite) {
    }

    record GenerateurLu(UUID id, String code, String nom, UUID salleId, String statut) {
    }
}
