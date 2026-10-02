package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.infrastructure.persistence.entity.PlanningParametresJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.PlanningParametresJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Paramétrage du planning : jours d'ouverture et ratio par centre (table {@code planning_parametres}). Les salles
 * d'isolement sont une propriété de la salle ({@code salle.isolement = 'OUI'}, saisie à la création de la salle) : elles
 * sont lues et écrites sur la table {@code salle}, jamais dans une liste séparée.
 */
@Component
public class PlanningParametresRepositoryAdapter implements PlanningParametresPort {

    static final String OUI = "OUI";
    static final String NON = "NON";

    private final PlanningParametresJpaRepository jpa;
    private final JdbcTemplate jdbc;

    public PlanningParametresRepositoryAdapter(PlanningParametresJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = jdbc;
    }

    private static PlanningParametres toDomain(PlanningParametresJpaEntity e, Set<UUID> sallesIsolement) {
        Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
        Arrays.stream(e.getJoursOuverts().split(",")).filter(s -> !s.isBlank())
                .forEach(s -> jours.add(JourSemaine.valueOf(s.trim())));
        int ratio = e.getPatientsParInfirmier() == null ? PlanningParametres.RATIO_PAR_DEFAUT : e.getPatientsParInfirmier();
        return new PlanningParametres(jours.isEmpty() ? EnumSet.allOf(JourSemaine.class) : jours, sallesIsolement, ratio);
    }

    private Set<UUID> sallesIsolement(UUID centerId) {
        return new HashSet<>(jdbc.query("SELECT id FROM salle WHERE center_id = ? AND isolement = ?",
                (rs, i) -> rs.getObject("id", UUID.class), centerId, OUI));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanningParametres lire(UUID centerId) {
        Set<UUID> isolement = sallesIsolement(centerId);
        PlanningParametres lus = jpa.findById(centerId).map(e -> toDomain(e, isolement))
                .orElseGet(PlanningParametres::parDefaut);
        return new PlanningParametres(lus.joursOuverts(), isolement, lus.patientsParInfirmier());
    }

    @Override
    @Transactional
    public void enregistrer(UUID centerId, PlanningParametres p) {
        String jours = p.joursOuverts().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        jpa.save(new PlanningParametresJpaEntity(centerId, jours, null, p.patientsParInfirmier(),
                OffsetDateTime.now(ZoneOffset.UTC)));
        jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ?", NON, centerId);
        for (UUID salleId : p.sallesIsolement()) {
            jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ? AND id = ?", OUI, centerId, salleId);
        }
    }
}
