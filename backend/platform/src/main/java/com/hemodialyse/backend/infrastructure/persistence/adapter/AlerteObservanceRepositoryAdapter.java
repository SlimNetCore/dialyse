package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AlerteObservanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AlerteObservanceJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AlerteObservanceRepositoryAdapter implements AlerteObservanceRepositoryPort {

    private final AlerteObservanceJpaRepository jpa;

    public AlerteObservanceRepositoryAdapter(AlerteObservanceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<AlerteObservance> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByCreatedAtDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AlerteObservance> findActiveByPatientAndType(UUID patientId, CenterId centerId,
                                                                 TypeTraitementAnemie typeTraitement) {
        return jpa.findByPatientIdAndCenterIdAndTypeTraitementAndResolvedAtIsNull(
                patientId, centerId.value(), typeTraitement.name()).map(this::toDomain);
    }

    @Override
    public Optional<AlerteObservance> findById(UUID alerteId, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(alerteId, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public AlerteObservance save(AlerteObservance alerte) {
        return toDomain(jpa.save(toJpa(alerte)));
    }

    private AlerteObservance toDomain(AlerteObservanceJpaEntity e) {
        return AlerteObservance.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(),
                TypeTraitementAnemie.valueOf(e.getTypeTraitement()), e.getPeriodeDebut(), e.getPeriodeFin(),
                e.getDosesAttendues(), e.getDosesAdministrees(), e.getMessage(), e.getCreatedAt(), e.getResolvedAt());
    }

    private AlerteObservanceJpaEntity toJpa(AlerteObservance a) {
        AlerteObservanceJpaEntity e = new AlerteObservanceJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setTypeTraitement(a.getTypeTraitement().name());
        e.setPeriodeDebut(a.getPeriodeDebut());
        e.setPeriodeFin(a.getPeriodeFin());
        e.setDosesAttendues(a.getDosesAttendues());
        e.setDosesAdministrees(a.getDosesAdministrees());
        e.setMessage(a.getMessage());
        e.setCreatedAt(a.getCreatedAt());
        e.setResolvedAt(a.getResolvedAt().orElse(null));
        return e;
    }
}
