package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.EcritureComptableJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EcritureComptableJpaRepository extends JpaRepository<EcritureComptableJpaEntity, UUID> {

    /**
     * Une pièce source n'est comptabilisée qu'une fois, quel que soit le journal alors paramétré.
     */
    Optional<EcritureComptableJpaEntity> findFirstByCenterIdAndSourceIdOrderByCreatedAtAsc(UUID centerId, UUID sourceId);

    boolean existsByCenterIdAndJournalCode(UUID centerId, String journalCode);

    Page<EcritureComptableJpaEntity> findByCenterIdAndJournalCodeAndDateEcritureBetween(
            UUID centerId, String journalCode, LocalDate from, LocalDate to, Pageable pageable);

    Page<EcritureComptableJpaEntity> findByCenterIdAndDateEcritureBetween(
            UUID centerId, LocalDate from, LocalDate to, Pageable pageable);

    // ─── Avec filtre statut ──────────────────────────────────────────────────

    Page<EcritureComptableJpaEntity> findByCenterIdAndJournalCodeAndStatutAndDateEcritureBetween(
            UUID centerId, String journalCode, String statut, LocalDate from, LocalDate to, Pageable pageable);

    Page<EcritureComptableJpaEntity> findByCenterIdAndStatutAndDateEcritureBetween(
            UUID centerId, String statut, LocalDate from, LocalDate to, Pageable pageable);

    List<EcritureComptableJpaEntity> findByCenterIdAndJournalCodeAndDateEcritureBetween(
            UUID centerId, String journalCode, LocalDate from, LocalDate to);

    Optional<EcritureComptableJpaEntity> findTopByCenterIdAndJournalCodeAndNumeroPieceLikeOrderByNumeroPieceDesc(
            UUID centerId, String journalCode, String prefix);
}


