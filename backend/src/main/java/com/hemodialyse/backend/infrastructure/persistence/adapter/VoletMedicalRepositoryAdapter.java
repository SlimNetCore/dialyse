package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.VoletMedical;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.VoletMedicalJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.VoletMedicalJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class VoletMedicalRepositoryAdapter implements VoletMedicalRepositoryPort {

    private final VoletMedicalJpaRepository jpa;

    public VoletMedicalRepositoryAdapter(VoletMedicalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<VoletMedical> findBySeanceId(UUID seanceId, CenterId centerId) {
        return jpa.findBySeanceIdAndCenterId(seanceId, centerId.value()).map(this::toDomain);
    }

    @Override
    public VoletMedical save(VoletMedical volet) {
        return toDomain(jpa.save(toJpa(volet)));
    }

    private VoletMedical toDomain(VoletMedicalJpaEntity e) {
        VoletMedical v = new VoletMedical();
        v.setId(e.getId());
        v.setSeanceId(e.getSeanceId());
        v.setCenterId(e.getCenterId());
        v.setPrescription(e.getPrescription());
        v.setToleranceSeance(e.getToleranceSeance());
        v.setExamenClinique(e.getExamenClinique());
        v.setResultatsBiologiques(e.getResultatsBiologiques());
        v.setAjustementsTherapeutiques(e.getAjustementsTherapeutiques());
        v.setConclusionMedicale(e.getConclusionMedicale());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        return v;
    }

    private VoletMedicalJpaEntity toJpa(VoletMedical v) {
        VoletMedicalJpaEntity e = new VoletMedicalJpaEntity();
        e.setId(v.getId());
        e.setSeanceId(v.getSeanceId());
        e.setCenterId(v.getCenterId());
        e.setPrescription(v.getPrescription());
        e.setToleranceSeance(v.getToleranceSeance());
        e.setExamenClinique(v.getExamenClinique());
        e.setResultatsBiologiques(v.getResultatsBiologiques());
        e.setAjustementsTherapeutiques(v.getAjustementsTherapeutiques());
        e.setConclusionMedicale(v.getConclusionMedicale());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        return e;
    }
}

