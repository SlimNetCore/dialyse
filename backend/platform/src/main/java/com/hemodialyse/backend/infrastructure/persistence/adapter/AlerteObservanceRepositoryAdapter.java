package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AlerteObservance;
import com.hemodialyse.backend.domain.medical.anemie.port.AlerteObservanceRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DetailObservance;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeAlerteObservance;
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
                                                                 TypeTraitementAnemie typeTraitement,
                                                                 TypeAlerteObservance type) {
        return jpa.findByPatientIdAndCenterIdAndTypeTraitementAndTypeAlerteAndResolvedAtIsNull(
                patientId, centerId.value(), typeTraitement.name(), type.name()).map(this::toDomain);
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
                TypeTraitementAnemie.valueOf(e.getTypeTraitement()), TypeAlerteObservance.valueOf(e.getTypeAlerte()),
                e.getPeriodeDebut(), e.getPeriodeFin(),
                e.getDosesAttendues(), e.getDosesAdministrees(), e.getMessage(),
                new DetailObservance(e.getUniteDose(), e.getDosePrescrite(), e.getFrequenceValeur(),
                        e.getFrequenceUnite()),
                e.getCreatedAt(), e.getResolvedAt());
    }

    private AlerteObservanceJpaEntity toJpa(AlerteObservance a) {
        AlerteObservanceJpaEntity e = new AlerteObservanceJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setTypeTraitement(a.getTypeTraitement().name());
        e.setTypeAlerte(a.getType().name());
        e.setPeriodeDebut(a.getPeriodeDebut());
        e.setPeriodeFin(a.getPeriodeFin());
        e.setDosesAttendues(a.getDosesAttendues());
        e.setDosesAdministrees(a.getDosesAdministrees());
        e.setMessage(a.getMessage());
        e.setUniteDose(a.getDetail().uniteDose());
        e.setDosePrescrite(a.getDetail().dosePrescrite());
        e.setFrequenceValeur(a.getDetail().frequenceValeur());
        e.setFrequenceUnite(a.getDetail().frequenceUnite());
        e.setCreatedAt(a.getCreatedAt());
        e.setResolvedAt(a.getResolvedAt().orElse(null));
        return e;
    }
}
