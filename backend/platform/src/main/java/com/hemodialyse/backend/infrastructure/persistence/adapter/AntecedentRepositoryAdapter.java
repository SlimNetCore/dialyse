package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.antecedent.aggregate.Antecedent;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.StatutClinique;
import com.hemodialyse.backend.domain.medical.antecedent.valueobject.TypeAntecedent;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AntecedentJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AntecedentJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AntecedentRepositoryAdapter implements AntecedentRepositoryPort {

    private final AntecedentJpaRepository jpa;

    public AntecedentRepositoryAdapter(AntecedentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Antecedent> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByDateDebutDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PagedResult<Antecedent> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<AntecedentJpaEntity> result = jpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateDebut")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Optional<Antecedent> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public Antecedent save(Antecedent antecedent) {
        return toDomain(jpa.save(toJpa(antecedent)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private Antecedent toDomain(AntecedentJpaEntity e) {
        ConceptCode diagnostic = e.getDiagnosticCode() == null ? null
                : ConceptCode.of(CodingSystem.valueOf(e.getDiagnosticCodeSystem()), e.getDiagnosticCode(),
                e.getDiagnosticCodeDisplay());
        return Antecedent.reconstituer(
                e.getId(), e.getPatientId(), e.getCenterId(), TypeAntecedent.valueOf(e.getTypeAntecedent()),
                diagnostic, e.getLibelleLibre(), e.getDateDebut(), e.getDateFin(),
                StatutClinique.valueOf(e.getStatutClinique()), e.getSeverite(), e.getNote(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private AntecedentJpaEntity toJpa(Antecedent a) {
        AntecedentJpaEntity e = new AntecedentJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setTypeAntecedent(a.getType().name());
        ConceptCode diagnostic = a.getDiagnostic();
        e.setDiagnosticCodeSystem(diagnostic == null ? null : diagnostic.system().name());
        e.setDiagnosticCode(diagnostic == null ? null : diagnostic.code());
        e.setDiagnosticCodeDisplay(diagnostic == null ? null : diagnostic.display());
        e.setLibelleLibre(a.getLibelleLibre());
        e.setDateDebut(a.getPeriode().debut());
        e.setDateFin(a.getPeriode().fin());
        e.setStatutClinique(a.getStatutClinique().name());
        e.setSeverite(a.getSeverite());
        e.setNote(a.getNote());
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        return e;
    }
}
