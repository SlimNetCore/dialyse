package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.observation.aggregate.ObservationBiologique;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueRepositoryPort;
import com.hemodialyse.backend.domain.medical.observation.valueobject.SourceObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.StatutObservation;
import com.hemodialyse.backend.domain.medical.observation.valueobject.ValeurMesuree;
import com.hemodialyse.backend.domain.medical.shared.valueobject.CodingSystem;
import com.hemodialyse.backend.domain.medical.shared.valueobject.ConceptCode;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.ObservationBiologiqueJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.ObservationBiologiqueJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.ObservationBiologiqueSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ObservationBiologiqueRepositoryAdapter implements ObservationBiologiqueRepositoryPort {

    private final ObservationBiologiqueJpaRepository jpa;

    public ObservationBiologiqueRepositoryAdapter(ObservationBiologiqueJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PagedResult<ObservationBiologique> findPagedByPatientId(UUID patientId, CenterId centerId,
                                                                   String loincCode, LocalDate from, LocalDate to,
                                                                   int page, int size) {
        Page<ObservationBiologiqueJpaEntity> result = jpa.findAll(
                ObservationBiologiqueSpecifications.from(patientId, centerId.value(), loincCode, from, to),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "datePrelevement")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public List<ObservationBiologique> findByDemandeExamenId(UUID demandeExamenId, CenterId centerId) {
        return jpa.findByDemandeExamenIdAndCenterId(demandeExamenId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ObservationBiologique> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public ObservationBiologique save(ObservationBiologique observation) {
        return toDomain(jpa.save(toJpa(observation)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private ObservationBiologique toDomain(ObservationBiologiqueJpaEntity e) {
        ConceptCode analyte = ConceptCode.of(
                CodingSystem.valueOf(e.getAnalyteCodeSystem()), e.getAnalyteCode(), e.getAnalyteCodeDisplay());
        ValeurMesuree valeurNum = e.getValeurNum() == null ? null : new ValeurMesuree(e.getValeurNum(), e.getUnite());
        return ObservationBiologique.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(),
                e.getDemandeExamenId(), analyte, valeurNum, e.getValeurTexte(), e.getDatePrelevement(),
                StatutObservation.valueOf(e.getStatut()), SourceObservation.valueOf(e.getSource()),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private ObservationBiologiqueJpaEntity toJpa(ObservationBiologique o) {
        ObservationBiologiqueJpaEntity e = new ObservationBiologiqueJpaEntity();
        e.setId(o.getId());
        e.setPatientId(o.getPatientId());
        e.setCenterId(o.getCenterId());
        e.setDemandeExamenId(o.getDemandeExamenId().orElse(null));
        e.setAnalyteCodeSystem(o.getAnalyte().system().name());
        e.setAnalyteCode(o.getAnalyte().code());
        e.setAnalyteCodeDisplay(o.getAnalyte().display());
        o.getValeurNum().ifPresentOrElse(v -> {
            e.setValeurNum(v.valeur());
            e.setUnite(v.unite());
        }, () -> {
            e.setValeurNum(null);
            e.setUnite(null);
        });
        e.setValeurTexte(o.getValeurTexte());
        e.setDatePrelevement(o.getDatePrelevement());
        e.setStatut(o.getStatut().name());
        e.setSource(o.getSource().name());
        e.setCreatedAt(o.getCreatedAt());
        e.setUpdatedAt(o.getUpdatedAt());
        return e;
    }
}
