package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SeanceJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SeanceRepositoryAdapter implements SeanceRepositoryPort {

    private final SeanceJpaRepository jpa;

    public SeanceRepositoryAdapter(SeanceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Seance save(Seance seance) {
        return toDomain(jpa.save(toJpa(seance)));
    }

    @Override
    public Optional<Seance> findById(UUID seanceId, CenterId centerId) {
        return jpa.findByIdAndCenterId(seanceId, centerId.value()).map(this::toDomain);
    }

    @Override
    public Optional<Seance> findByPatientIdAndDate(CenterId centerId, UUID patientId, LocalDate dateSeance) {
        return jpa.findByCenterIdAndPatientIdAndDateSeance(centerId.value(), patientId, dateSeance).map(this::toDomain);
    }

    @Override
    public List<SeanceListItem> findAllByCenter(CenterId centerId) {
        return jpa.findByCenterIdOrderByDateSeanceDescCreatedAtDesc(centerId.value()).stream()
                .map(e -> new SeanceListItem(
                        e.getId(),
                        e.getCenterId(),
                        e.getPatientId(),
                        null,
                        null,
                        null,
                        e.getDateSeance(),
                        SeanceStatus.valueOf(e.getStatut()),
                        e.getCreatedAt(),
                        e.getValidatedAt(),
                        e.getSignedInfirmierAt(),
                        e.getSignedMedecinAt()
                ))
                .toList();
    }

    @Override
    public PagedResult<SeanceListItem> findPagedByCenter(CenterId centerId, int page, int size) {
        Page<SeanceJpaEntity> jpaPage = jpa.findByCenterIdOrderByDateSeanceDescCreatedAtDesc(
                centerId.value(), PageRequest.of(page, size));
        List<SeanceListItem> items = jpaPage.getContent().stream()
                .map(e -> new SeanceListItem(
                        e.getId(),
                        e.getCenterId(),
                        e.getPatientId(),
                        null,
                        null,
                        null,
                        e.getDateSeance(),
                        SeanceStatus.valueOf(e.getStatut()),
                        e.getCreatedAt(),
                        e.getValidatedAt(),
                        e.getSignedInfirmierAt(),
                        e.getSignedMedecinAt()
                ))
                .toList();
        return PagedResult.of(items, jpaPage.getTotalElements(), page, size);
    }

    @Override
    public PagedResult<SeanceListItem> findPagedByCenterAndMonth(CenterId centerId, YearMonth month, int page, int size) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Page<SeanceJpaEntity> jpaPage = jpa.findByCenterIdAndDateSeanceBetweenOrderByDateSeanceDescCreatedAtDesc(
                centerId.value(), from, to, PageRequest.of(page, size));
        List<SeanceListItem> items = jpaPage.getContent().stream()
                .map(e -> new SeanceListItem(
                        e.getId(),
                        e.getCenterId(),
                        e.getPatientId(),
                        null,
                        null,
                        null,
                        e.getDateSeance(),
                        SeanceStatus.valueOf(e.getStatut()),
                        e.getCreatedAt(),
                        e.getValidatedAt(),
                        e.getSignedInfirmierAt(),
                        e.getSignedMedecinAt()
                ))
                .toList();
        return PagedResult.of(items, jpaPage.getTotalElements(), page, size);
    }

    private Seance toDomain(SeanceJpaEntity e) {
        Seance s = new Seance();
        s.setId(e.getId());
        s.setPatientId(e.getPatientId());
        s.setCenterId(e.getCenterId());
        s.setDateSeance(e.getDateSeance());
        s.setStatus(SeanceStatus.valueOf(e.getStatut()));
        s.setCreatedAt(e.getCreatedAt());
        s.setValidatedAt(e.getValidatedAt());
        s.setSignedByInfirmierAt(e.getSignedInfirmierAt());
        s.setSignedByInfirmierUserId(e.getSignedInfirmierBy());
        s.setSignedByMedecinAt(e.getSignedMedecinAt());
        s.setSignedByMedecinUserId(e.getSignedMedecinBy());
        s.setForfaitOverrideId(e.getForfaitOverrideId());
        s.setForfaitOverrideCode(e.getForfaitOverrideCode());
        s.setForfaitOverrideNom(e.getForfaitOverrideNom());
        s.setForfaitOverridePrix(e.getForfaitOverridePrix());
        s.setForfaitOverrideUpdatedAt(e.getForfaitOverrideUpdatedAt());
        s.setForfaitOverrideUpdatedBy(e.getForfaitOverrideUpdatedBy());
        return s;
    }

    private SeanceJpaEntity toJpa(Seance s) {
        SeanceJpaEntity e = new SeanceJpaEntity();
        e.setId(s.getId());
        e.setPatientId(s.getPatientId());
        e.setCenterId(s.getCenterId());
        e.setDateSeance(s.getDateSeance());
        e.setStatut(s.getStatus().name());
        e.setCreatedAt(s.getCreatedAt());
        e.setValidatedAt(s.getValidatedAt());
        e.setSignedInfirmierAt(s.getSignedByInfirmierAt());
        e.setSignedInfirmierBy(s.getSignedByInfirmierUserId());
        e.setSignedMedecinAt(s.getSignedByMedecinAt());
        e.setSignedMedecinBy(s.getSignedByMedecinUserId());
        e.setForfaitOverrideId(s.getForfaitOverrideId());
        e.setForfaitOverrideCode(s.getForfaitOverrideCode());
        e.setForfaitOverrideNom(s.getForfaitOverrideNom());
        e.setForfaitOverridePrix(s.getForfaitOverridePrix());
        e.setForfaitOverrideUpdatedAt(s.getForfaitOverrideUpdatedAt());
        e.setForfaitOverrideUpdatedBy(s.getForfaitOverrideUpdatedBy());
        return e;
    }
}




