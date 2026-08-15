package com.hemodialyse.backend.domain.facturation.port;

import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Port entrant — gestion des types TVA (CRUD + consultation).
 * Exposé par le use case, implémenté par le service de domaine ou applicatif.
 */
public interface TypeTvaUseCase {

    PagedResult<TypeTVA> listerTypesTva(UUID centerId, int page, int size);

    TypeTVA creerTypeTva(UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                         boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                         String texteReference, String createdBy);

    TypeTVA modifierTypeTva(UUID id, UUID centerId, String libelle, BigDecimal taux, String typePrestation,
                            boolean exonere, LocalDate dateDebutValidite, LocalDate dateFinValidite,
                            String texteReference, String updatedBy);

    void desactiverTypeTva(UUID id, UUID centerId);
}

