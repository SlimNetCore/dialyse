package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.InterventionEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.InterventionJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistance pour Intervention
 * Implémente le port InterventionRepositoryPort
 * Gère la conversion entre domaine et entités JPA
 */
@Component
public class InterventionRepositoryAdapter implements InterventionRepositoryPort {

    private final InterventionJpaRepository jpaRepository;

    public InterventionRepositoryAdapter(InterventionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Intervention intervention) {
        InterventionEntity entity = toEntity(intervention);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Intervention> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Intervention> findByEquipementId(UUID equipementId) {
        return jpaRepository.findByEquipementId(equipementId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findByCentreId(UUID centreId) {
        return jpaRepository.findByCentreId(centreId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findByCentreIdAndStatut(UUID centreId, String statut) {
        return jpaRepository.findByCentreIdAndStatut(centreId, statut).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findByCentreIdAndDateRange(UUID centreId, LocalDateTime debut, LocalDateTime fin) {
        return jpaRepository.findByCentreIdAndDateRange(centreId, debut, fin).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findByTechnicien(UUID technicienId) {
        return jpaRepository.findByTechnicien(technicienId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findPendingByEquipementId(UUID equipementId) {
        return jpaRepository.findPendingByEquipementId(equipementId).stream()
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
    public long countByCentreIdAndStatutEnCours(UUID centreId) {
        return jpaRepository.countByCentreIdAndStatutEnCours(centreId);
    }

    @Override
    public long countByCentreIdAndStatut(UUID centreId, String statut) {
        return jpaRepository.countByCentreIdAndStatut(centreId, statut);
    }

    @Override
    public PagedResult<Intervention> findPaged(UUID centreId, String statut, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        Page<InterventionEntity> result = (statut == null || statut.isBlank())
                ? jpaRepository.findPageByCentreId(centreId, pageable)
                : jpaRepository.findPageByCentreIdAndStatut(centreId, statut, pageable);
        return PagedResult.of(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    @Override
    public PagedResult<Intervention> findPagedByEquipementId(UUID equipementId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        Page<InterventionEntity> result = jpaRepository.findPageByEquipementId(equipementId, pageable);
        return PagedResult.of(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(),
                page,
                size
        );
    }

    // Mappers
    private InterventionEntity toEntity(Intervention domain) {
        return new InterventionEntity(
                domain.getId(),
                domain.getEquipementId(),
                domain.getCentreId(),
                domain.getType().name(),
                domain.getStatut().name(),
                domain.getDateDebut(),
                domain.getDateFin(),
                domain.getTechnicien(),
                domain.getDescription(),
                domain.getActions(),
                domain.getPieceRemplacee(),
                domain.getCout(),
                domain.getObservations(),
                domain.getDateCreation(),
                domain.getDateModification(),
                domain.getCreePar(),
                domain.getModifiePar()
        );
    }

    private Intervention toDomain(InterventionEntity entity) {
        return Intervention.reconstruct(
                entity.getId(),
                entity.getEquipementId(),
                entity.getCentreId(),
                TypeIntervention.valueOf(entity.getType()),
                StatutIntervention.valueOf(entity.getStatut()),
                entity.getDateDebut(),
                entity.getDateFin(),
                entity.getTechnicienId(),
                entity.getDescription(),
                entity.getActions(),
                entity.getPieceRemplacee(),
                entity.getCout(),
                entity.getObservations(),
                entity.getDateCreation(),
                entity.getDateModification(),
                entity.getCreePar(),
                entity.getModifiePar()
        );
    }
}

