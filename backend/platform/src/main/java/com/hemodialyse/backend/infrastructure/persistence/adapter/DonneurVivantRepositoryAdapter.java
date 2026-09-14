package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.DonneurVivant;
import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.LienParenteDonneur;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.ResultatCrossmatch;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutBilanDonneur;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.DonneurVivantJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.DonneurVivantJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DonneurVivantRepositoryAdapter implements DonneurVivantRepositoryPort {

    private final DonneurVivantJpaRepository jpa;

    public DonneurVivantRepositoryAdapter(DonneurVivantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<DonneurVivant> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByCreatedAtDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<DonneurVivant> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public DonneurVivant save(DonneurVivant donneur) {
        return toDomain(jpa.save(toJpa(donneur)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private DonneurVivant toDomain(DonneurVivantJpaEntity e) {
        return DonneurVivant.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(), e.getNom(), e.getPrenom(),
                e.getDateNaissance(), LienParenteDonneur.valueOf(e.getLienParente()), e.getTelephone(),
                e.getGroupeSanguin(), e.getTypageHla(), StatutBilanDonneur.valueOf(e.getStatutBilan()),
                ResultatCrossmatch.valueOf(e.getCrossmatchResultat()), e.getDateCrossmatch(), e.getBilanRealise(),
                e.getContreIndications(), e.getDecisionFinale(), e.getDateDecision(), e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private DonneurVivantJpaEntity toJpa(DonneurVivant d) {
        DonneurVivantJpaEntity e = new DonneurVivantJpaEntity();
        e.setId(d.getId());
        e.setPatientId(d.getPatientId());
        e.setCenterId(d.getCenterId());
        e.setNom(d.getNom());
        e.setPrenom(d.getPrenom());
        e.setDateNaissance(d.getDateNaissance());
        e.setLienParente(d.getLienParente().name());
        e.setTelephone(d.getTelephone());
        e.setGroupeSanguin(d.getGroupeSanguin());
        e.setTypageHla(d.getTypageHla());
        e.setStatutBilan(d.getStatutBilan().name());
        e.setCrossmatchResultat(d.getCrossmatchResultat().name());
        e.setDateCrossmatch(d.getDateCrossmatch());
        e.setBilanRealise(d.getBilanRealise());
        e.setContreIndications(d.getContreIndications());
        e.setDecisionFinale(d.getDecisionFinale());
        e.setDateDecision(d.getDateDecision());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}
