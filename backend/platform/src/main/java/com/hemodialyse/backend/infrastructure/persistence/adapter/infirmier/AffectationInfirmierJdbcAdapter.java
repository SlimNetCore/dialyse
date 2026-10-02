package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.model.AffectationInfirmier;
import com.hemodialyse.backend.domain.infirmier.port.AffectationInfirmierRepositoryPort;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Affectations (roulement) des infirmiers sur la table {@code infirmier_affectation} ; les jours sont stockés sous
 * forme de liste séparée par des virgules.
 */
@Component
public class AffectationInfirmierJdbcAdapter implements AffectationInfirmierRepositoryPort {

    private static final String COLONNES = "id, center_id, infirmier_id, salle_id, creneau_id, jours";

    private static final RowMapper<AffectationInfirmier> MAPPER = (rs, i) -> new AffectationInfirmier(
            rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
            rs.getObject("infirmier_id", UUID.class), rs.getObject("salle_id", UUID.class),
            rs.getObject("creneau_id", UUID.class), decoder(rs.getString("jours")));

    private final JdbcTemplate jdbc;

    public AffectationInfirmierJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Set<JourSemaine> decoder(String jours) {
        Set<JourSemaine> resultat = EnumSet.noneOf(JourSemaine.class);
        Arrays.stream(jours.split(",")).filter(s -> !s.isBlank()).forEach(s -> resultat.add(JourSemaine.valueOf(s.trim())));
        return resultat;
    }

    private static String encoder(Set<JourSemaine> jours) {
        return jours.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public AffectationInfirmier save(AffectationInfirmier a) {
        int maj = jdbc.update("UPDATE infirmier_affectation SET salle_id = ?, creneau_id = ?, jours = ? "
                        + "WHERE id = ? AND center_id = ?",
                a.salleId(), a.creneauId(), encoder(a.jours()), a.id(), a.centerId());
        if (maj == 0) {
            jdbc.update("INSERT INTO infirmier_affectation (" + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?)",
                    a.id(), a.centerId(), a.infirmierId(), a.salleId(), a.creneauId(), encoder(a.jours()));
        }
        return a;
    }

    @Override
    public Optional<AffectationInfirmier> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_affectation WHERE center_id = ? AND id = ?",
                MAPPER, centerId, id).stream().findFirst();
    }

    @Override
    public List<AffectationInfirmier> findByInfirmierIds(UUID centerId, Collection<UUID> infirmierIds) {
        if (infirmierIds.isEmpty()) return List.of();
        String marques = infirmierIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Object[] args = new Object[infirmierIds.size() + 1];
        args[0] = centerId;
        int i = 1;
        for (UUID id : infirmierIds) args[i++] = id;
        return jdbc.query("SELECT " + COLONNES + " FROM infirmier_affectation WHERE center_id = ? "
                + "AND infirmier_id IN (" + marques + ") ORDER BY creneau_id, id", MAPPER, args);
    }

    @Override
    public void delete(UUID centerId, UUID id) {
        jdbc.update("DELETE FROM infirmier_affectation WHERE center_id = ? AND id = ?", centerId, id);
    }
}
