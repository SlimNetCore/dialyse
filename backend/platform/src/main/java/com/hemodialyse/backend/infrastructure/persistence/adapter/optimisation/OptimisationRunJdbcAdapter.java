package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.planning.optimisation.model.ParametresOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.ResultatOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.RunOptimisation.StatutRun;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationRunRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Historique des optimisations sur la table {@code planification_optimisation}. Paramètres, synthèse et résultat sont
 * stockés en JSON ; les dates sont en UTC. Toutes les requêtes sont bornées au centre (AGENTS.md §2).
 */
@Component
public class OptimisationRunJdbcAdapter implements OptimisationRunRepositoryPort {

    private static final String COLONNES = "id, center_id, statut, perimetre, parametres, cree_le, termine_le, lance_par, "
            + "phase, score, empreinte, resume, erreur, applique_le";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RunOptimisation> sansResultat = (rs, i) -> lire(rs, null);

    public OptimisationRunJdbcAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private static Timestamp horodatage(Instant instant) {
        return instant == null ? null : Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private RunOptimisation lire(java.sql.ResultSet rs, String resultatJson) throws java.sql.SQLException {
        String resume = rs.getString("resume");
        return new RunOptimisation(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                StatutRun.valueOf(rs.getString("statut")),
                mapper.readValue(rs.getString("parametres"), ParametresOptimisation.class),
                instant(rs.getTimestamp("cree_le")), instant(rs.getTimestamp("termine_le")), rs.getString("lance_par"),
                rs.getString("phase"), rs.getString("score"), rs.getString("empreinte"),
                resume == null ? null : mapper.readValue(resume, ResultatOptimisation.Resume.class),
                resultatJson == null ? null : mapper.readValue(resultatJson, ResultatOptimisation.class),
                rs.getString("erreur"), instant(rs.getTimestamp("applique_le")));
    }

    @Override
    public RunOptimisation save(RunOptimisation run) {
        String parametres = mapper.writeValueAsString(run.parametres());
        String resume = run.resume() == null ? null : mapper.writeValueAsString(run.resume());
        String resultat = run.resultat() == null ? null : mapper.writeValueAsString(run.resultat());
        int maj = jdbc.update("UPDATE planification_optimisation SET statut = ?, termine_le = ?, phase = ?, score = ?, "
                        + "resume = ?, resultat = ?, erreur = ?, applique_le = ? WHERE id = ? AND center_id = ?",
                run.statut().name(), horodatage(run.termineLe()), run.phase(), run.score(), resume, resultat,
                run.erreur(), horodatage(run.appliqueLe()), run.id(), run.centerId());
        if (maj == 0) {
            jdbc.update("INSERT INTO planification_optimisation (" + COLONNES + ", resultat) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    run.id(), run.centerId(), run.statut().name(), run.parametres().perimetre().name(), parametres,
                    horodatage(run.creeLe()), horodatage(run.termineLe()), run.lancePar(), run.phase(), run.score(),
                    run.empreinte(), resume, run.erreur(), horodatage(run.appliqueLe()), resultat);
        }
        return run;
    }

    @Override
    public Optional<RunOptimisation> findById(UUID centerId, UUID id) {
        return jdbc.query("SELECT " + COLONNES + ", resultat FROM planification_optimisation "
                        + "WHERE center_id = ? AND id = ?", (rs, i) -> lire(rs, rs.getString("resultat")), centerId, id)
                .stream().findFirst();
    }

    @Override
    public Optional<RunOptimisation> findEnCours(UUID centerId) {
        return jdbc.query("SELECT " + COLONNES + " FROM planification_optimisation "
                        + "WHERE center_id = ? AND statut = 'EN_COURS' ORDER BY cree_le DESC LIMIT 1", sansResultat, centerId)
                .stream().findFirst();
    }

    @Override
    public PagedResult<RunOptimisation> findPaged(UUID centerId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?",
                Long.class, centerId);
        List<RunOptimisation> items = jdbc.query("SELECT " + COLONNES + " FROM planification_optimisation "
                        + "WHERE center_id = ? ORDER BY cree_le DESC, id LIMIT ? OFFSET ?", sansResultat, centerId, size,
                (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public void purger(UUID centerId, int aGarder) {
        jdbc.update("DELETE FROM planification_optimisation WHERE center_id = ? AND id NOT IN ("
                + "SELECT id FROM (SELECT id FROM planification_optimisation WHERE center_id = ? "
                + "ORDER BY cree_le DESC, id LIMIT ?) recents)", centerId, centerId, aGarder);
    }

    @Override
    public boolean supprimer(UUID centerId, UUID id) {
        return jdbc.update("DELETE FROM planification_optimisation WHERE center_id = ? AND id = ?", centerId, id) > 0;
    }

    @Override
    public int interrompreEnCours(String motif) {
        return jdbc.update("UPDATE planification_optimisation SET statut = 'ECHEC', erreur = ?, termine_le = ? "
                + "WHERE statut = 'EN_COURS'", motif, horodatage(Instant.now()));
    }
}
