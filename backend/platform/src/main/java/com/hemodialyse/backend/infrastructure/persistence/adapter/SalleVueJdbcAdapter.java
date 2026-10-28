package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.planning.model.SalleVue;
import com.hemodialyse.backend.domain.planning.model.SalleVue.GenerateurVue;
import com.hemodialyse.backend.domain.planning.port.SalleVuePort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Salles d'un centre (table {@code salle}) et générateurs de dialyse qui leur sont affectés
 * ({@code gmao_equipements}, hors équipements réformés, désactivés ou supprimés). Toutes les requêtes sont bornées
 * au centre.
 */
@Component
public class SalleVueJdbcAdapter implements SalleVuePort {

    private static final String GENERATEURS_AFFECTES = "type = 'GENERATEUR_DIALYSE' AND deleted_at IS NULL "
            + "AND statut NOT IN ('REFORME', 'DESACTIF')";

    private final JdbcTemplate jdbc;

    public SalleVueJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PagedResult<SalleVue> findPaged(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM salle WHERE center_id = ?", Long.class, centerId);
        List<Ligne> lignes = jdbc.query("SELECT id, code, nom, isolement, capacite FROM salle WHERE center_id = ? "
                        + "ORDER BY code LIMIT ? OFFSET ?",
                (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        "OUI".equals(rs.getString("isolement")),
                        rs.getObject("capacite") == null ? null : rs.getInt("capacite")),
                centerId, size, (long) page * size);
        Map<UUID, List<GenerateurVue>> parSalle = generateurs(centerId, lignes.stream().map(Ligne::id).toList());
        List<SalleVue> items = lignes.stream().map(l -> new SalleVue(l.id(), l.code(), l.nom(), l.isolement(),
                l.capacite(), parSalle.getOrDefault(l.id(), List.of()))).toList();
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public Optional<SalleVue> findById(UUID centerId, UUID salleId) {
        return jdbc.query("SELECT id, code, nom, isolement, capacite FROM salle WHERE center_id = ? AND id = ?",
                        (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                                "OUI".equals(rs.getString("isolement")),
                                rs.getObject("capacite") == null ? null : rs.getInt("capacite")),
                        centerId, salleId).stream().findFirst()
                .map(l -> new SalleVue(l.id(), l.code(), l.nom(), l.isolement(), l.capacite(),
                        generateurs(centerId, List.of(salleId)).getOrDefault(salleId, List.of())));
    }

    private Map<UUID, List<GenerateurVue>> generateurs(UUID centerId, List<UUID> salleIds) {
        if (salleIds.isEmpty()) return Map.of();
        String in = salleIds.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(centerId);
        args.addAll(salleIds);
        Map<UUID, List<GenerateurVue>> parSalle = new HashMap<>();
        jdbc.query("SELECT id, code, designation, statut, salle_id FROM gmao_equipements WHERE centre_id = ? AND "
                        + GENERATEURS_AFFECTES + " AND salle_id IN (" + in + ") ORDER BY code",
                rs -> {
                    parSalle.computeIfAbsent(rs.getObject("salle_id", UUID.class), k -> new ArrayList<>())
                            .add(new GenerateurVue(rs.getObject("id", UUID.class), rs.getString("code"),
                                    rs.getString("designation"), rs.getString("statut")));
                }, args.toArray());
        return parSalle;
    }

    private record Ligne(UUID id, String code, String nom, boolean isolement, Integer capacite) {
    }
}
