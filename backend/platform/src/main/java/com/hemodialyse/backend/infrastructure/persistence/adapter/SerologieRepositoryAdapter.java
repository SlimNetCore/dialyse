package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.serologie.aggregate.Serologie;
import com.hemodialyse.backend.domain.medical.serologie.port.SerologieRepositoryPort;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.MarqueurSerologique;
import com.hemodialyse.backend.domain.medical.serologie.valueobject.ResultatSerologique;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.SerologieJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SerologieJpaRepository;
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
public class SerologieRepositoryAdapter implements SerologieRepositoryPort {

    private final SerologieJpaRepository jpa;

    public SerologieRepositoryAdapter(SerologieJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Serologie> findByPatientId(UUID patientId, CenterId centerId) {
        return jpa.findByPatientIdAndCenterIdOrderByDatePrelevementDesc(patientId, centerId.value())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public PagedResult<Serologie> findPagedByPatientId(UUID patientId, CenterId centerId, int page, int size) {
        Page<SerologieJpaEntity> result = jpa.findByPatientIdAndCenterId(
                patientId, centerId.value(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "datePrelevement")));
        return PagedResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), page, size);
    }

    @Override
    public Optional<Serologie> findById(UUID id, UUID patientId, CenterId centerId) {
        return jpa.findByIdAndPatientIdAndCenterId(id, patientId, centerId.value()).map(this::toDomain);
    }

    @Override
    public boolean existsByPatientIdAndMarqueurAndDatePrelevement(UUID patientId, CenterId centerId,
                                                                  MarqueurSerologique marqueur, LocalDate datePrelevement) {
        return jpa.existsByPatientIdAndCenterIdAndMarqueurAndDatePrelevement(
                patientId, centerId.value(), marqueur.name(), datePrelevement);
    }

    @Override
    public Serologie save(Serologie serologie) {
        return toDomain(jpa.save(toJpa(serologie)));
    }

    @Override
    @Transactional
    public void deleteById(UUID id, UUID patientId, CenterId centerId) {
        jpa.deleteByIdAndPatientIdAndCenterId(id, patientId, centerId.value());
    }

    private Serologie toDomain(SerologieJpaEntity e) {
        return Serologie.reconstituer(
                e.getId(), e.getPatientId(), e.getCenterId(), MarqueurSerologique.valueOf(e.getMarqueur()),
                ResultatSerologique.valueOf(e.getResultat()), e.getTitre(), e.getUnite(), e.getDatePrelevement(),
                e.getLaboratoire(), e.getDateProchainControle(), e.getConduiteATenir(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private SerologieJpaEntity toJpa(Serologie s) {
        SerologieJpaEntity e = new SerologieJpaEntity();
        e.setId(s.getId());
        e.setPatientId(s.getPatientId());
        e.setCenterId(s.getCenterId());
        e.setMarqueur(s.getMarqueur().name());
        e.setResultat(s.getResultat().name());
        e.setTitre(s.getTitre());
        e.setUnite(s.getUnite());
        e.setDatePrelevement(s.getDatePrelevement());
        e.setLaboratoire(s.getLaboratoire());
        e.setDateProchainControle(s.getDateProchainControle());
        e.setConduiteATenir(s.getConduiteATenir());
        e.setCreatedAt(s.getCreatedAt());
        e.setUpdatedAt(s.getUpdatedAt());
        return e;
    }
}
