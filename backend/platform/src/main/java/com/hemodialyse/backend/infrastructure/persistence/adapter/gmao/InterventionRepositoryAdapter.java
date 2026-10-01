package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.*;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.InterventionEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.LigneCoutInterventionEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.RectificationInterventionEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.RectificationInterventionJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.InterventionJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.LigneCoutInterventionJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistance pour Intervention.
 * Implémente le port InterventionRepositoryPort — gère la conversion entre domaine et entités JPA,
 * ainsi que la persistance explicite des lignes de coût (enfants, table séparée : contrairement à
 * {@code TacheIntervention}, elles ne doivent jamais être perdues au rechargement de l'agrégat).
 */
@Component
public class InterventionRepositoryAdapter implements InterventionRepositoryPort {

    private final InterventionJpaRepository jpaRepository;
    private final LigneCoutInterventionJpaRepository lignesCoutRepository;
    private final RectificationInterventionJpaRepository rectificationRepository;

    public InterventionRepositoryAdapter(
            InterventionJpaRepository jpaRepository, LigneCoutInterventionJpaRepository lignesCoutRepository,
            RectificationInterventionJpaRepository rectificationRepository) {
        this.rectificationRepository = rectificationRepository;
        this.jpaRepository = jpaRepository;
        this.lignesCoutRepository = lignesCoutRepository;
    }

    @Override
    @Transactional
    public void save(Intervention intervention) {
        InterventionEntity entity = toEntity(intervention);
        jpaRepository.save(entity);

        // Remplace l'ensemble des lignes de coût (simple et sûr pour un agrégat sans concurrence
        // d'écriture attendue sur une même intervention — patron volontairement simple).
        lignesCoutRepository.deleteByInterventionId(intervention.getId());
        List<LigneCoutInterventionEntity> lignes = intervention.getLignesCout().stream()
                .map(l -> new LigneCoutInterventionEntity(
                        l.getId(), intervention.getId(), l.getType().name(), l.getLibelle(),
                        l.getQuantite(), l.getPrixUnitaire(), l.getArticleStockId(), l.isAutomatique()))
                .toList();
        lignesCoutRepository.saveAll(lignes);

        rectificationRepository.deleteByInterventionId(intervention.getId());
        rectificationRepository.saveAll(intervention.getRectifications().stream()
                .map(r -> new RectificationInterventionEntity(r.id(), intervention.getId(), r.motif(), r.par(), r.le(),
                        r.clotureAnterieureLe(), r.clotureAnterieurePar()))
                .toList());
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
    public List<Intervention> findByCentreIdAndDateRange(UUID centreId, OffsetDateTime debut, OffsetDateTime fin) {
        return jpaRepository.findByCentreIdAndDateRange(centreId, debut, fin).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Intervention> findByIntervenantId(UUID intervenantId) {
        return jpaRepository.findByIntervenantId(intervenantId).stream()
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
    @Transactional
    public void delete(UUID id) {
        lignesCoutRepository.deleteByInterventionId(id);
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

    @Override
    public long countByEquipementId(UUID equipementId) {
        return jpaRepository.countByEquipementId(equipementId);
    }

    @Override
    public Optional<Intervention> findLatestByEquipementId(UUID equipementId) {
        return jpaRepository.findByEquipementIdOrderByDateDebutDesc(equipementId, PageRequest.of(0, 1))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public BigDecimal sumCoutByEquipementIdAndDateRange(UUID equipementId, OffsetDateTime from, OffsetDateTime to) {
        BigDecimal sum = lignesCoutRepository.sumByEquipementIdAndDateRange(equipementId, from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public BigDecimal sumCoutByCentreIdAndDateRange(UUID centreId, OffsetDateTime from, OffsetDateTime to) {
        BigDecimal sum = lignesCoutRepository.sumByCentreIdAndDateRange(centreId, from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public long countEnRetardByCentreId(UUID centreId, OffsetDateTime maintenant) {
        return jpaRepository.countEnRetard(centreId, maintenant);
    }

    @Override
    public Map<UUID, BigDecimal> sumCoutParEquipement(UUID centreId, OffsetDateTime from, OffsetDateTime to) {
        Map<UUID, BigDecimal> result = new HashMap<>();
        for (Object[] row : lignesCoutRepository.sumGroupedByEquipement(centreId, from, to)) {
            result.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    // Mappers
    private InterventionEntity toEntity(Intervention domain) {
        InterventionEntity entity = new InterventionEntity(
                domain.getId(),
                domain.getEquipementId(),
                domain.getCentreId(),
                domain.getType().name(),
                domain.getStatut().name(),
                domain.getDateDebut(),
                domain.getDateFin(),
                domain.getIntervenantId(),
                domain.getDescription(),
                domain.getActions(),
                domain.getPieceRemplacee(),
                domain.getObservations(),
                domain.getDateCreation(),
                domain.getDateModification(),
                domain.getCreePar(),
                domain.getModifiePar(),
                domain.getEtatEquipementAvant() != null ? domain.getEtatEquipementAvant().name() : null,
                domain.getEtatEquipementApres() != null ? domain.getEtatEquipementApres().name() : null
        );
        entity.setPriorite(domain.getPriorite().name());
        entity.setEcheance(domain.getEcheance());
        entity.setSymptome(domain.getSymptome());
        entity.setCause(domain.getCause());
        entity.setDemarrePar(domain.getDemarrePar());
        entity.setDemarreLe(domain.getDemarreLe());
        entity.setCloturePar(domain.getCloturePar());
        entity.setClotureLe(domain.getClotureLe());
        entity.setAnnulePar(domain.getAnnulePar());
        entity.setAnnuleLe(domain.getAnnuleLe());
        return entity;
    }

    private Intervention toDomain(InterventionEntity entity) {
        List<LigneCoutIntervention> lignesCout = lignesCoutRepository.findByInterventionId(entity.getId()).stream()
                .map(l -> LigneCoutIntervention.reconstruct(
                        l.getId(), TypeLigneCout.valueOf(l.getType()), l.getLibelle(),
                        l.getQuantite(), l.getPrixUnitaire(), l.getArticleStockId(), l.isAutomatique()))
                .toList();

        Intervention intervention = Intervention.reconstruct(
                entity.getId(),
                entity.getEquipementId(),
                entity.getCentreId(),
                TypeIntervention.valueOf(entity.getType()),
                StatutIntervention.valueOf(entity.getStatut()),
                entity.getDateDebut(),
                entity.getDateFin(),
                entity.getIntervenantId(),
                entity.getDescription(),
                entity.getActions(),
                entity.getPieceRemplacee(),
                entity.getObservations(),
                entity.getDateCreation(),
                entity.getDateModification(),
                entity.getCreePar(),
                entity.getModifiePar(),
                lignesCout,
                entity.getEtatEquipementAvant() != null ? StatutEquipement.valueOf(entity.getEtatEquipementAvant()) : null,
                entity.getEtatEquipementApres() != null ? StatutEquipement.valueOf(entity.getEtatEquipementApres()) : null
        );
        List<RectificationIntervention> rectifications = rectificationRepository.findByInterventionIdOrderByLeAsc(entity.getId()).stream()
                .map(r -> new RectificationIntervention(r.getId(), r.getMotif(), r.getPar(), r.getLe(),
                        r.getClotureAnterieureLe(), r.getClotureAnterieurePar()))
                .toList();
        intervention.restaurerRectifications(rectifications);
        return intervention.restaurerSuivi(
                entity.getPriorite() != null ? PrioriteIntervention.valueOf(entity.getPriorite()) : null,
                entity.getEcheance(), entity.getSymptome(), entity.getCause(),
                entity.getDemarrePar(), entity.getDemarreLe(), entity.getCloturePar(), entity.getClotureLe(),
                entity.getAnnulePar(), entity.getAnnuleLe());
    }
}
