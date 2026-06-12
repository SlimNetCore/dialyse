package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.DossierMedicalPatient;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.DossierMedicalPatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.DossierMedicalPatientJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class DossierMedicalPatientRepositoryAdapter implements DossierMedicalPatientRepositoryPort {

    private final DossierMedicalPatientJpaRepository jpa;

    public DossierMedicalPatientRepositoryAdapter(DossierMedicalPatientJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<DossierMedicalPatient> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterId(patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public DossierMedicalPatient save(DossierMedicalPatient dossier) {
        return toDomain(jpa.save(toJpa(dossier)));
    }

    private DossierMedicalPatient toDomain(DossierMedicalPatientJpaEntity e) {
        DossierMedicalPatient d = new DossierMedicalPatient();
        d.setId(e.getId());
        d.setPatientId(e.getPatientId());
        d.setCenterId(e.getCenterId());
        d.setNephropathieInitiale(e.getNephropathieInitiale());
        d.setDateMiseEnDialyse(e.getDateMiseEnDialyse());
        d.setHepatiteBStatut(e.getHepatiteBStatut());
        d.setHepatiteCStatut(e.getHepatiteCStatut());
        d.setObservationGlobale(e.getObservationGlobale());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }

    private DossierMedicalPatientJpaEntity toJpa(DossierMedicalPatient d) {
        DossierMedicalPatientJpaEntity e = new DossierMedicalPatientJpaEntity();
        e.setId(d.getId());
        e.setPatientId(d.getPatientId());
        e.setCenterId(d.getCenterId());
        e.setNephropathieInitiale(d.getNephropathieInitiale());
        e.setDateMiseEnDialyse(d.getDateMiseEnDialyse());
        e.setHepatiteBStatut(d.getHepatiteBStatut());
        e.setHepatiteCStatut(d.getHepatiteCStatut());
        e.setObservationGlobale(d.getObservationGlobale());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}

