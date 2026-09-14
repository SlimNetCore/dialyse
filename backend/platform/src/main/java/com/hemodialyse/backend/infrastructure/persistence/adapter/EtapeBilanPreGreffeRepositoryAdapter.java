package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.greffe.aggregate.EtapeBilanPreGreffe;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.CategorieEtapeGreffe;
import com.hemodialyse.backend.domain.medical.greffe.valueobject.StatutEtapeGreffe;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.EtapeBilanPreGreffeJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.EtapeBilanPreGreffeJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class EtapeBilanPreGreffeRepositoryAdapter implements EtapeBilanPreGreffeRepositoryPort {

    private final EtapeBilanPreGreffeJpaRepository jpa;

    public EtapeBilanPreGreffeRepositoryAdapter(EtapeBilanPreGreffeJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<EtapeBilanPreGreffe> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByCategorieAscLibelleAsc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<EtapeBilanPreGreffe> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public EtapeBilanPreGreffe save(EtapeBilanPreGreffe etape) {
        return toDomain(jpa.save(toJpa(etape)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private EtapeBilanPreGreffe toDomain(EtapeBilanPreGreffeJpaEntity e) {
        return EtapeBilanPreGreffe.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(),
                CategorieEtapeGreffe.valueOf(e.getCategorie()), e.getLibelle(), StatutEtapeGreffe.valueOf(e.getStatut()),
                e.getDateRealisation(), e.getResultat(), e.getDateExpiration(), e.getDemandeExamenId(),
                e.getSerologieId(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private EtapeBilanPreGreffeJpaEntity toJpa(EtapeBilanPreGreffe d) {
        EtapeBilanPreGreffeJpaEntity e = new EtapeBilanPreGreffeJpaEntity();
        e.setId(d.getId());
        e.setPatientId(d.getPatientId());
        e.setCenterId(d.getCenterId());
        e.setCategorie(d.getCategorie().name());
        e.setLibelle(d.getLibelle());
        e.setStatut(d.getStatut().name());
        e.setDateRealisation(d.getDateRealisation());
        e.setResultat(d.getResultat());
        e.setDateExpiration(d.getDateExpiration());
        e.setDemandeExamenId(d.getDemandeExamenId());
        e.setSerologieId(d.getSerologieId());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        return e;
    }
}
