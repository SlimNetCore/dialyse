package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.model.MotifHorsPlanning;
import com.hemodialyse.backend.domain.seance.model.Seance;
import com.hemodialyse.backend.domain.seance.model.SeanceListItem;
import com.hemodialyse.backend.domain.seance.model.SeanceSearch;
import com.hemodialyse.backend.domain.seance.model.SeanceStatus;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.persistence.entity.PatientJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.SeanceJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.SeanceJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class SeanceRepositoryAdapter implements SeanceRepositoryPort {

    @PersistenceContext
    private EntityManager em;

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
    public List<Seance> findRecentByPatient(CenterId centerId, UUID patientId, LocalDate before, int limit) {
        return jpa.findByCenterIdAndPatientIdAndDateSeanceLessThanOrderByDateSeanceDesc(
                        centerId.value(), patientId, before, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * Recherche filtrée, triée et paginée en base (JPA Criteria : aucun paramètre null, donc aucun souci de typage
     * H2 / PostgreSQL). Le patient est joint dans la même requête : pas de requête par ligne.
     */
    @Override
    public PagedResult<SeanceListItem> search(CenterId centerId, SeanceSearch criteria, int page, int size) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<SeanceJpaEntity> countSeance = countQuery.from(SeanceJpaEntity.class);
        Root<PatientJpaEntity> countPatient = countQuery.from(PatientJpaEntity.class);
        countQuery.select(cb.count(countSeance))
                .where(predicates(cb, countSeance, countPatient, centerId, criteria).toArray(Predicate[]::new));
        long total = em.createQuery(countQuery).getSingleResult();

        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<SeanceJpaEntity> seance = query.from(SeanceJpaEntity.class);
        Root<PatientJpaEntity> patient = query.from(PatientJpaEntity.class);
        query.multiselect(seance.alias("seance"), patient.get("codePatient").alias("code"),
                        patient.get("nom").alias("nom"), patient.get("prenom").alias("prenom"))
                .where(predicates(cb, seance, patient, centerId, criteria).toArray(Predicate[]::new))
                .orderBy(order(cb, seance, patient, criteria));
        List<SeanceListItem> items = em.createQuery(query)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream()
                .map(row -> {
                    SeanceJpaEntity e = row.get("seance", SeanceJpaEntity.class);
                    return new SeanceListItem(e.getId(), e.getCenterId(), e.getPatientId(),
                            row.get("code", String.class), row.get("nom", String.class),
                            row.get("prenom", String.class), e.getDateSeance(), SeanceStatus.valueOf(e.getStatut()),
                            e.getCreatedAt(), e.getValidatedAt(), e.getSignedInfirmierAt(), e.getSignedMedecinAt(),
                            e.getRegularisationDeverrouilleeAt(), e.isHorsPlanning(),
                            e.getMotifHorsPlanning() == null
                                    ? null : MotifHorsPlanning.valueOf(e.getMotifHorsPlanning()));
                })
                .toList();
        return PagedResult.of(items, total, page, size);
    }

    private List<Predicate> predicates(CriteriaBuilder cb, Root<SeanceJpaEntity> seance, Root<PatientJpaEntity> patient,
                                       CenterId centerId, SeanceSearch criteria) {
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(patient.get("id"), seance.get("patientId")));
        where.add(cb.equal(seance.get("centerId"), centerId.value()));
        where.add(cb.equal(patient.get("centerId"), centerId.value()));
        if (criteria.from() != null) where.add(cb.greaterThanOrEqualTo(seance.get("dateSeance"), criteria.from()));
        if (criteria.to() != null) where.add(cb.lessThanOrEqualTo(seance.get("dateSeance"), criteria.to()));
        if (criteria.deverrouillee() != null) {
            where.add(criteria.deverrouillee()
                    ? cb.isNotNull(seance.get("regularisationDeverrouilleeAt"))
                    : cb.isNull(seance.get("regularisationDeverrouilleeAt")));
        }
        if (!criteria.statuses().isEmpty()) {
            where.add(seance.get("statut").in(criteria.statuses().stream().map(SeanceStatus::name).toList()));
        }
        if (criteria.text() != null && !criteria.text().isBlank()) {
            for (String word : criteria.text().toLowerCase(Locale.ROOT).split("\\s+")) {
                String like = "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(patient.get("nom")), like, '\\'),
                        cb.like(cb.lower(patient.get("prenom")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(patient.get("codePatient"), "")), like, '\\')));
            }
        }
        return where;
    }

    private List<Order> order(CriteriaBuilder cb, Root<SeanceJpaEntity> seance, Root<PatientJpaEntity> patient,
                              SeanceSearch criteria) {
        List<Expression<?>> keys = switch (criteria.sort()) {
            case PATIENT -> List.of(patient.get("nom"), patient.get("prenom"));
            case CODE -> List.of(patient.get("codePatient"));
            case STATUS -> List.of(seance.get("statut"));
            case CREATED -> List.of(seance.get("createdAt"));
            case DATE -> List.of(seance.get("dateSeance"));
        };
        List<Order> orders = new ArrayList<>();
        for (Expression<?> key : keys) {
            orders.add(criteria.desc() ? cb.desc(key) : cb.asc(key));
        }
        // Départage stable : sans lui, une même page pourrait réapparaître sur la suivante.
        orders.add(cb.desc(seance.get("createdAt")));
        orders.add(cb.asc(seance.get("id")));
        return orders;
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
        s.setRegularisationDeverrouilleeAt(e.getRegularisationDeverrouilleeAt());
        s.setRegularisationDeverrouilleeBy(e.getRegularisationDeverrouilleeBy());
        s.setHorsPlanning(e.isHorsPlanning());
        s.setMotifHorsPlanning(e.getMotifHorsPlanning() == null
                ? null : MotifHorsPlanning.valueOf(e.getMotifHorsPlanning()));
        s.setPrecisionHorsPlanning(e.getPrecisionHorsPlanning());
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
        e.setRegularisationDeverrouilleeAt(s.getRegularisationDeverrouilleeAt());
        e.setRegularisationDeverrouilleeBy(s.getRegularisationDeverrouilleeBy());
        e.setHorsPlanning(s.isHorsPlanning());
        e.setMotifHorsPlanning(s.getMotifHorsPlanning() == null ? null : s.getMotifHorsPlanning().name());
        e.setPrecisionHorsPlanning(s.getPrecisionHorsPlanning());
        return e;
    }
}




