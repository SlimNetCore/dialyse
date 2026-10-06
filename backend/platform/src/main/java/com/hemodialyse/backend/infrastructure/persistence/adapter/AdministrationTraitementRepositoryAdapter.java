package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.anemie.aggregate.AdministrationTraitement;
import com.hemodialyse.backend.domain.medical.anemie.port.AdministrationTraitementRepositoryPort;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.DoseAdministree;
import com.hemodialyse.backend.domain.medical.anemie.valueobject.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.AdministrationTraitementJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.AdministrationTraitementJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class AdministrationTraitementRepositoryAdapter implements AdministrationTraitementRepositoryPort {

    private final AdministrationTraitementJpaRepository jpa;

    public AdministrationTraitementRepositoryAdapter(AdministrationTraitementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PagedResult<AdministrationTraitement> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<AdministrationTraitementJpaEntity> result = jpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateAdministration")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public List<AdministrationTraitement> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByDateAdministrationDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteBySeanceId(UUID seanceId, CenterId centerId) {
        jpa.deleteAll(jpa.findBySeanceIdAndCenterId(seanceId, centerId.value()));
    }

    @Override
    public AdministrationTraitement save(AdministrationTraitement administration) {
        return toDomain(jpa.save(toJpa(administration)));
    }

    private AdministrationTraitement toDomain(AdministrationTraitementJpaEntity e) {
        DoseAdministree dose = e.getDose() == null ? null : new DoseAdministree(e.getDose(), e.getUniteDose());
        return AdministrationTraitement.reconstituer(e.getId(), e.getPatientId(), e.getCenterId(),
                e.getPrescriptionMedicaleId(), TypeTraitementAnemie.valueOf(e.getTypeTraitement()), e.getMolecule(),
                dose, e.getVoie(), e.getDateAdministration(), e.getSeanceId(), e.getAdministrePar(),
                e.isAdministree(), e.getMotifNonAdministration(), e.getArticleId(), e.getQuantiteArticle(),
                e.getCreatedAt());
    }

    private AdministrationTraitementJpaEntity toJpa(AdministrationTraitement a) {
        AdministrationTraitementJpaEntity e = new AdministrationTraitementJpaEntity();
        e.setId(a.getId());
        e.setPatientId(a.getPatientId());
        e.setCenterId(a.getCenterId());
        e.setPrescriptionMedicaleId(a.getPrescriptionMedicaleId().orElse(null));
        e.setTypeTraitement(a.getTypeTraitement().name());
        e.setMolecule(a.getMolecule());
        a.getDose().ifPresentOrElse(d -> {
            e.setDose(d.valeur());
            e.setUniteDose(d.unite());
        }, () -> {
            e.setDose(null);
            e.setUniteDose(null);
        });
        e.setVoie(a.getVoie());
        e.setDateAdministration(a.getDateAdministration());
        e.setSeanceId(a.getSeanceId().orElse(null));
        e.setAdministrePar(a.getAdministrePar());
        e.setAdministree(a.isAdministree());
        e.setMotifNonAdministration(a.getMotifNonAdministration());
        e.setArticleId(a.getArticleId().orElse(null));
        e.setQuantiteArticle(a.getQuantiteArticle().orElse(null));
        e.setCreatedAt(a.getCreatedAt());
        return e;
    }
}
