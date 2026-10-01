package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.PlanMaintenanceRepositoryPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.PlanMaintenanceEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.PlanMaintenanceJpaRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistance pour PlanMaintenance
 * Implémente le port PlanMaintenanceRepositoryPort
 * Gère la conversion entre domaine et entités JPA
 */
@Component
public class PlanMaintenanceRepositoryAdapter implements PlanMaintenanceRepositoryPort {

    private final PlanMaintenanceJpaRepository jpaRepository;

    public PlanMaintenanceRepositoryAdapter(PlanMaintenanceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(PlanMaintenance plan) {
        PlanMaintenanceEntity entity = toEntity(plan);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<PlanMaintenance> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<PlanMaintenance> findByEquipementId(UUID equipementId) {
        return jpaRepository.findByEquipementId(equipementId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlanMaintenance> findByCentreId(UUID centreId) {
        return jpaRepository.findByCentreId(centreId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlanMaintenance> findActiveByCentreId(UUID centreId) {
        return jpaRepository.findActiveByCentreId(centreId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlanMaintenance> findOverdueByCentreId(UUID centreId, OffsetDateTime dateLimit) {
        return jpaRepository.findOverdueByCentreId(centreId, dateLimit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PlanMaintenance> findActiveByEquipementId(UUID equipementId) {
        return jpaRepository.findActiveByEquipementId(equipementId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long countByCentreId(UUID centreId) {
        return jpaRepository.countByCentreId(centreId);
    }

    @Override
    public long countActiveByCentreId(UUID centreId) {
        return jpaRepository.countActiveByCentreId(centreId);
    }

    // Mappers
    private PlanMaintenanceEntity toEntity(PlanMaintenance domain) {
        return new PlanMaintenanceEntity(
                domain.getId(),
                domain.getEquipementId(),
                domain.getCentreId(),
                domain.getDesignation(),
                domain.getDescription(),
                domain.getFrequence().name(),
                domain.getStatut().name(),
                domain.getProchaineDatePrevue(),
                domain.getDerniereDateExecution(),
                domain.getNombreExecutions(),
                domain.getTachemesAEffectuer(),
                domain.getDateCreation(),
                domain.getDateModification(),
                domain.getCreePar(),
                domain.getModifiePar()
        );
    }

    private PlanMaintenance toDomain(PlanMaintenanceEntity entity) {
        return PlanMaintenance.reconstruct(
                entity.getId(),
                entity.getEquipementId(),
                entity.getCentreId(),
                entity.getDesignation(),
                entity.getDescription(),
                FrequenceMaintenance.valueOf(entity.getFrequence()),
                StatutPlan.valueOf(entity.getStatut()),
                entity.getProchaineDatePrevue(),
                entity.getDerniereDateExecution(),
                entity.getNombreExecutions(),
                entity.getTachesAEffectuer(),
                entity.getDateCreation(),
                entity.getDateModification(),
                entity.getCreePar(),
                entity.getModifiePar()
        );
    }
}

