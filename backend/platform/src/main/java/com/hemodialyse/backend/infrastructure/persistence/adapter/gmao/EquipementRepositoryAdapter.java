package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.EquipementEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.EquipementJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistance pour Equipement
 * Implémente le port EquipementRepositoryPort
 * Gère la conversion entre domaine et entités JPA
 */
@Component
public class EquipementRepositoryAdapter implements EquipementRepositoryPort {

    private final EquipementJpaRepository jpaRepository;

    public EquipementRepositoryAdapter(EquipementJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Equipement equipement) {
        EquipementEntity entity = toEntity(equipement);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Equipement> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Equipement> findByCentreId(UUID centreId) {
        return jpaRepository.findByCentreId(centreId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Equipement> findByCentreIdAndStatut(UUID centreId, String statut) {
        return jpaRepository.findByCentreIdAndStatut(centreId, statut).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Equipement> findByCentreIdAndCode(UUID centreId, String code) {
        return jpaRepository.findByCentreIdAndCode(centreId, code).map(this::toDomain);
    }

    @Override
    public List<Equipement> findByCentreIdAndType(UUID centreId, String type) {
        return jpaRepository.findByCentreIdAndType(centreId, type).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Equipement> findByCentreIdAndTypeAndSalleId(UUID centreId, String type, UUID salleId) {
        return jpaRepository.findByCentreIdAndTypeAndSalleId(centreId, type, salleId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
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
    public long countByCentreIdAndStatut(UUID centreId, String statut) {
        return jpaRepository.countByCentreIdAndStatut(centreId, statut);
    }

    @Override
    public PagedResult<Equipement> findPaged(UUID centreId, String statut, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        Page<EquipementEntity> result = (statut == null || statut.isBlank())
                ? jpaRepository.findPageByCentreId(centreId, pageable)
                : jpaRepository.findPageByCentreIdAndStatut(centreId, statut, pageable);
        return PagedResult.of(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    // Mappers
    private EquipementEntity toEntity(Equipement domain) {
        return new EquipementEntity(
                domain.getId(),
                domain.getCode(),
                domain.getDesignation(),
                domain.getType().name(),
                domain.getFabricant(),
                domain.getModele(),
                domain.getNumeroSerie(),
                domain.getDateInstallation(),
                domain.getCentreId(),
                domain.getStatut().name(),
                domain.getLocalisation(),
                domain.getObservations(),
                domain.getDateCreation(),
                domain.getDateModification(),
                domain.getCreePar(),
                domain.getModifiePar(),
                domain.getSalleId(),
                domain.getPrixAcquisition()
        );
    }

    private Equipement toDomain(EquipementEntity entity) {
        return Equipement.reconstruct(
                entity.getId(),
                entity.getCode(),
                entity.getDesignation(),
                TypeEquipement.valueOf(entity.getType()),
                entity.getFabricant(),
                entity.getModele(),
                entity.getNumeroSerie(),
                entity.getDateInstallation(),
                entity.getCentreId(),
                StatutEquipement.valueOf(entity.getStatut()),
                entity.getLocalisation(),
                entity.getObservations(),
                entity.getDateCreation(),
                entity.getDateModification(),
                entity.getCreePar(),
                entity.getModifiePar(),
                entity.getSalleId(),
                entity.getPrixAcquisition()
        );
    }
}


