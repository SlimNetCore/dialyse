package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.aggregate.ModelePiece;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — modèles de pièces d'un centre.
 */
public interface ModelePieceRepositoryPort {

    /**
     * Page de modèles triés par code.
     */
    PagedResult<ModelePiece> findPaged(UUID centerId, boolean actifsSeulement, int page, int size);

    Optional<ModelePiece> findById(UUID centerId, UUID id);

    Optional<ModelePiece> findByCode(UUID centerId, String code);

    void save(ModelePiece modele);

    void delete(UUID centerId, UUID id);

    /**
     * Vrai si une ligne d'un modèle du centre utilise ce compte.
     */
    boolean existsByCompte(UUID centerId, String compte);

    /**
     * Vrai si un modèle du centre s'écrit dans ce journal.
     */
    boolean existsByJournal(UUID centerId, JournalCode journal);
}
