package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.pec.model.PecStatus;
import com.hemodialyse.backend.domain.pec.model.PriseEnCharge;
import com.hemodialyse.backend.domain.pec.port.PecRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.PecJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PecJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PecRepositoryAdapter implements PecRepositoryPort {

    private final PecJpaRepository jpa;

    public PecRepositoryAdapter(PecJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public PriseEnCharge save(PriseEnCharge pec) {
        var e = toJpa(pec);
        var saved = jpa.save(e);
        return toDomain(saved);
    }

    @Override
    public void deleteById(UUID id) { jpa.deleteById(id); }

    @Override
    public Optional<PriseEnCharge> findById(UUID id, CenterId centerId) {
        return jpa.findByIdAndCenterId(id, centerId.value()).map(this::toDomain);
    }

    @Override
    public List<PriseEnCharge> findByPatient(CenterId centerId, UUID patientId) {
        return jpa.findByCenterIdAndPatientId(centerId.value(), patientId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<PriseEnCharge> findByCenter(CenterId centerId) {
        return jpa.findByCenterId(centerId.value()).stream().map(this::toDomain).toList();
    }

    private PriseEnCharge toDomain(PecJpaEntity e) {
        var p = new PriseEnCharge();
        p.setId(e.getId()); p.setPatientId(e.getPatientId()); p.setCenterId(e.getCenterId());
        p.setDateDebutDemande(e.getDateDebutDemande()); p.setDateFinDemande(e.getDateFinDemande());
        p.setForfaitDemandeId(e.getForfaitDemandeId());
        p.setDateDebutEffectif(e.getDateDebutEffectif()); p.setDateFinEffectif(e.getDateFinEffectif());
        p.setForfaitEffectifId(e.getForfaitEffectifId());
        p.setStatus(PecStatus.valueOf(e.getStatut())); p.setCreatedAt(e.getCreatedAt());
        return p;
    }

    private PecJpaEntity toJpa(PriseEnCharge p) {
        var e = new PecJpaEntity();
        e.setId(p.getId()); e.setPatientId(p.getPatientId()); e.setCenterId(p.getCenterId());
        e.setDateDebutDemande(p.getDateDebutDemande()); e.setDateFinDemande(p.getDateFinDemande());
        e.setForfaitDemandeId(p.getForfaitDemandeId());
        e.setDateDebutEffectif(p.getDateDebutEffectif()); e.setDateFinEffectif(p.getDateFinEffectif());
        e.setForfaitEffectifId(p.getForfaitEffectifId());
        e.setStatut(p.getStatus().name()); e.setCreatedAt(p.getCreatedAt());
        return e;
    }
}

