package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.allergie.aggregate.Allergie;
import com.hemodialyse.backend.domain.medical.allergie.port.AllergieRepositoryPort;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CategorieAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.CriticiteAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.StatutVerificationAllergie;
import com.hemodialyse.backend.domain.medical.allergie.valueobject.TypeReaction;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AllergieJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AllergieJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AllergieRepositoryAdapter implements AllergieRepositoryPort {

    private final AllergieJpaRepository jpa;

    public AllergieRepositoryAdapter(AllergieJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Allergie> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByDateConstatationDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PagedResult<Allergie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<AllergieJpaEntity> result = jpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateConstatation")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Optional<Allergie> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public Allergie save(Allergie allergie) {
        return toDomain(jpa.save(toJpa(allergie)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private Allergie toDomain(AllergieJpaEntity e) {
        ConceptCode substance = ConceptCode.of(
                CodingSystem.valueOf(e.getSubstanceCodeSystem()), e.getSubstanceCode(), e.getSubstanceCodeDisplay());
        return Allergie.reconstituer(
                e.getId(), e.getPatientId(), e.getCenterId(), substance,
                CategorieAllergie.valueOf(e.getCategorie()), CriticiteAllergie.valueOf(e.getCriticite()),
                TypeReaction.valueOf(e.getTypeReaction()), e.getManifestations(), e.getDateConstatation(),
                StatutVerificationAllergie.valueOf(e.getStatutVerification()), e.getCreatedAt(), e.getUpdatedAt());
    }

    private AllergieJpaEntity toJpa(Allergie a) {
        AllergieJpaEntity e = new AllergieJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setSubstanceCodeSystem(a.getSubstance().system().name());
        e.setSubstanceCode(a.getSubstance().code());
        e.setSubstanceCodeDisplay(a.getSubstance().display());
        e.setCategorie(a.getCategorie().name());
        e.setCriticite(a.getCriticite().name());
        e.setTypeReaction(a.getTypeReaction().name());
        e.setManifestations(a.getManifestations());
        e.setDateConstatation(a.getDateConstatation());
        e.setStatutVerification(a.getStatutVerification().name());
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        return e;
    }
}
