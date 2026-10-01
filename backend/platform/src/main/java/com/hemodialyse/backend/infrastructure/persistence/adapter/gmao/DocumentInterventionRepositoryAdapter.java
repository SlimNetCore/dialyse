package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.DocumentIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention;
import com.hemodialyse.backend.domain.gmao.port.DocumentInterventionRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.DocumentInterventionEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.DocumentInterventionJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DocumentInterventionRepositoryAdapter implements DocumentInterventionRepositoryPort {

    private final DocumentInterventionJpaRepository jpaRepository;

    public DocumentInterventionRepositoryAdapter(DocumentInterventionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(DocumentIntervention d) {
        jpaRepository.save(new DocumentInterventionEntity(
                d.id(), d.interventionId(), d.centreId(), d.type().name(), d.nom(), d.contentType(), d.taille(),
                d.contenu(), d.ajoutePar(), d.ajouteLe()));
    }

    @Override
    public PagedResult<DocumentIntervention> findPagedByInterventionId(UUID interventionId, int page, int size) {
        List<DocumentIntervention> items = jpaRepository
                .findMetaByInterventionId(interventionId, PageRequest.of(page, size)).stream()
                .map(r -> DocumentIntervention.reconstruct(
                        (UUID) r[0], (UUID) r[1], (UUID) r[2], TypeDocumentIntervention.valueOf((String) r[3]),
                        (String) r[4], (String) r[5], (Long) r[6], null, (UUID) r[7], (OffsetDateTime) r[8]))
                .toList();
        return PagedResult.of(items, jpaRepository.countByInterventionId(interventionId), page, size);
    }

    @Override
    public Optional<DocumentIntervention> findWithContenuById(UUID id) {
        return jpaRepository.findById(id).map(e -> DocumentIntervention.reconstruct(
                e.getId(), e.getInterventionId(), e.getCentreId(), TypeDocumentIntervention.valueOf(e.getType()),
                e.getNom(), e.getContentType(), e.getTaille(), e.getContenu(), e.getAjoutePar(), e.getAjouteLe()));
    }

    @Override
    public long countByInterventionId(UUID interventionId) {
        return jpaRepository.countByInterventionId(interventionId);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}
