package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.shared.PagedResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — persistance des écritures comptables.
 */
public interface EcritureComptableRepositoryPort {

    void save(EcritureComptable ecriture);

    void saveAll(List<EcritureComptable> ecritures);

    Optional<EcritureComptable> findById(UUID id, UUID centerId);

    /**
     * Recherche par référence source — garantit l'idempotence.
     */
    Optional<EcritureComptable> findBySourceId(UUID sourceId, UUID centerId, JournalCode journalCode);

    PagedResult<EcritureComptable> findByCenterAndPeriod(UUID centerId, LocalDate from, LocalDate to,
                                                         JournalCode journalCode, int page, int size);

    List<EcritureComptable> findForExport(UUID centerId, LocalDate from, LocalDate to, JournalCode journalCode);

    /**
     * Prochain numéro séquentiel de pièce pour un journal/centre donné (sans trou).
     */
    String nextNumeroPiece(UUID centerId, JournalCode journalCode, int year);
}

