package com.hemodialyse.backend.infrastructure.persistence.adapter.gmao;

import com.hemodialyse.backend.domain.gmao.model.Intervenant;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervenant;
import com.hemodialyse.backend.domain.gmao.port.IntervenantRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.IntervenantEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.gmao.IntervenantJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class IntervenantRepositoryAdapter implements IntervenantRepositoryPort {

    private final IntervenantJpaRepository jpaRepository;

    public IntervenantRepositoryAdapter(IntervenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Intervenant save(Intervenant intervenant) {
        jpaRepository.save(toEntity(intervenant));
        return intervenant;
    }

    @Override
    public Optional<Intervenant> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public PagedResult<Intervenant> findPaged(UUID centreId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("nom").ascending());
        Page<IntervenantEntity> result = jpaRepository.findPageByCentreId(centreId, pageable);
        return PagedResult.of(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    private IntervenantEntity toEntity(Intervenant domain) {
        return new IntervenantEntity(
                domain.id(), domain.centreId(), domain.nom(), domain.type().name(),
                domain.telephone(), domain.email(), domain.tarifHoraireDefaut(), domain.actif());
    }

    private Intervenant toDomain(IntervenantEntity entity) {
        return new Intervenant(
                entity.getId(), entity.getCentreId(), entity.getNom(), TypeIntervenant.valueOf(entity.getType()),
                entity.getTelephone(), entity.getEmail(), entity.getTarifHoraireDefaut(), entity.isActif());
    }
}
