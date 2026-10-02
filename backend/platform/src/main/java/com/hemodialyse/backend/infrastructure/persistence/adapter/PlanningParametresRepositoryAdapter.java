package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.PlanningParametresJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PlanningParametresJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlanningParametresRepositoryAdapter implements PlanningParametresPort {

    private final PlanningParametresJpaRepository jpa;

    public PlanningParametresRepositoryAdapter(PlanningParametresJpaRepository jpa) {
        this.jpa = jpa;
    }

    private static PlanningParametres toDomain(PlanningParametresJpaEntity e) {
        Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
        Arrays.stream(e.getJoursOuverts().split(",")).filter(s -> !s.isBlank())
                .forEach(s -> jours.add(JourSemaine.valueOf(s.trim())));
        Set<UUID> salles = e.getSallesIsolement() == null ? Set.of()
                : Arrays.stream(e.getSallesIsolement().split(",")).filter(s -> !s.isBlank())
                .map(s -> UUID.fromString(s.trim())).collect(Collectors.toSet());
        return new PlanningParametres(jours.isEmpty() ? EnumSet.allOf(JourSemaine.class) : jours, salles);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningParametres lire(UUID centerId) {
        return jpa.findById(centerId).map(PlanningParametresRepositoryAdapter::toDomain)
                .orElseGet(PlanningParametres::parDefaut);
    }

    @Override
    @Transactional
    public void enregistrer(UUID centerId, PlanningParametres p) {
        String jours = p.joursOuverts().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        String salles = p.sallesIsolement().stream().map(UUID::toString).sorted().collect(Collectors.joining(","));
        jpa.save(new PlanningParametresJpaEntity(centerId, jours, salles, OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
