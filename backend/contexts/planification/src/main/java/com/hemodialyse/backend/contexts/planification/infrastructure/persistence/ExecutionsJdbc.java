package com.hemodialyse.backend.contexts.planification.infrastructure.persistence;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.OptimisationLue;
import com.hemodialyse.backend.contexts.planification.application.OptimisationLue.ExecutionLue;
import com.hemodialyse.backend.contexts.planification.application.OptimisationLue.ResultatLu;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation.Statut;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Historique des optimisations sur la table {@code planification_optimisation}. Paramètres, synthèse et résultat sont stockés en JSON
 * aux noms de champs historiques ({@link OptimisationLue}) ; les dates sont en UTC. Toutes les requêtes sont bornées au centre ;
 * compatible H2 et PostgreSQL.
 */
public class ExecutionsJdbc implements ExecutionsRepository {

    private static final String COLONNES = "id, center_id, statut, perimetre, parametres, cree_le, termine_le, lance_par, "
            + "phase, score, empreinte, resume, erreur, applique_le";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<ExecutionDOptimisation> sansResultat = (rs, i) -> lire(rs, null);

    private final String instance;

    /**
     * @param instance jeton de cette instance du serveur (un par démarrage) : seule elle fait avancer les calculs qu'elle a lancés
     */
    public ExecutionsJdbc(JdbcTemplate jdbc, ObjectMapper mapper, String instance) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.instance = instance;
    }

    private static Timestamp horodatage(Instant instant) {
        return instant == null ? null : Timestamp.valueOf(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private ExecutionDOptimisation lire(ResultSet rs, String resultatJson) throws SQLException {
        String resume = rs.getString("resume");
        ExecutionLue lue = new ExecutionLue(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                Statut.valueOf(rs.getString("statut")), mapper.readValue(rs.getString("parametres"), ParametresDOptimisation.class),
                instant(rs.getTimestamp("cree_le")), instant(rs.getTimestamp("termine_le")), rs.getString("lance_par"),
                rs.getString("phase"), rs.getString("score"), rs.getString("empreinte"),
                resume == null ? null : mapper.readValue(resume, ResultatDOptimisation.Resume.class),
                resultatJson == null ? null : mapper.readValue(resultatJson, ResultatLu.class), rs.getString("erreur"),
                instant(rs.getTimestamp("applique_le")));
        return OptimisationLue.versLAgregat(lue);
    }

    @Override
    public void creer(ExecutionDOptimisation e) {
        String parametres = mapper.writeValueAsString(e.parametres());
        String resume = e.resume() == null ? null : mapper.writeValueAsString(e.resume());
        String resultat = e.resultat() == null ? null : mapper.writeValueAsString(OptimisationLue.depuis(e.resultat()));
        jdbc.update("INSERT INTO planification_optimisation (" + COLONNES + ", resultat, instance_id, battement_le) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                e.id().valeur(), e.centre().valeur(), e.statut().name(), e.parametres().perimetre().name(), parametres,
                horodatage(e.creeLe()), horodatage(e.termineLe()), e.lancePar(), e.phase(), e.score(),
                e.empreinte() == null ? null : e.empreinte().valeur(), resume, e.erreur(), horodatage(e.appliqueeLe()),
                resultat, instance, horodatage(e.creeLe()));
    }

    @Override
    public boolean transiter(ExecutionDOptimisation e, Statut avant) {
        String resume = e.resume() == null ? null : mapper.writeValueAsString(e.resume());
        String resultat = e.resultat() == null ? null : mapper.writeValueAsString(OptimisationLue.depuis(e.resultat()));
        String condition = "WHERE id = ? AND center_id = ? AND statut = ?" + (avant == Statut.EN_COURS ? " AND instance_id = ?" : "");
        Object[] valeurs = avant == Statut.EN_COURS
                ? new Object[]{e.statut().name(), horodatage(e.termineLe()), e.phase(), e.score(), resume, resultat, e.erreur(),
                horodatage(e.appliqueeLe()), e.id().valeur(), e.centre().valeur(), avant.name(), instance}
                : new Object[]{e.statut().name(), horodatage(e.termineLe()), e.phase(), e.score(), resume, resultat, e.erreur(),
                horodatage(e.appliqueeLe()), e.id().valeur(), e.centre().valeur(), avant.name()};
        return jdbc.update("UPDATE planification_optimisation SET statut = ?, termine_le = ?, phase = ?, score = ?, "
                + "resume = ?, resultat = ?, erreur = ?, applique_le = ? " + condition, valeurs) > 0;
    }

    @Override
    public void battre(Instant maintenant) {
        jdbc.update("UPDATE planification_optimisation SET battement_le = ? WHERE statut = 'EN_COURS' AND instance_id = ?",
                horodatage(maintenant), instance);
    }

    @Override
    public Optional<ExecutionDOptimisation> parId(CentreId centre, ExecutionId id) {
        return jdbc.query("SELECT " + COLONNES + ", resultat FROM planification_optimisation WHERE center_id = ? AND id = ?",
                (rs, i) -> lire(rs, rs.getString("resultat")), centre.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public Optional<ExecutionDOptimisation> enCours(CentreId centre) {
        return jdbc.query("SELECT " + COLONNES + " FROM planification_optimisation "
                        + "WHERE center_id = ? AND statut = 'EN_COURS' ORDER BY cree_le DESC LIMIT 1", sansResultat, centre.valeur())
                .stream().findFirst();
    }

    @Override
    public PageDeResultats<ExecutionDOptimisation> page(CentreId centre, Pagination pagination) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM planification_optimisation WHERE center_id = ?", Long.class,
                centre.valeur());
        List<ExecutionDOptimisation> elements = jdbc.query("SELECT " + COLONNES + " FROM planification_optimisation "
                        + "WHERE center_id = ? ORDER BY cree_le DESC, id LIMIT ? OFFSET ?", sansResultat, centre.valeur(),
                pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(elements, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public void purger(CentreId centre, int aGarder) {
        jdbc.update("DELETE FROM planification_optimisation WHERE center_id = ? AND id NOT IN ("
                + "SELECT id FROM (SELECT id FROM planification_optimisation WHERE center_id = ? "
                + "ORDER BY cree_le DESC, id LIMIT ?) recents)", centre.valeur(), centre.valeur(), aGarder);
    }

    @Override
    public boolean supprimer(CentreId centre, ExecutionId id) {
        return jdbc.update("DELETE FROM planification_optimisation WHERE center_id = ? AND id = ?", centre.valeur(), id.valeur()) > 0;
    }

    @Override
    public int interrompreLesOrphelines(String motif, Instant maintenant, Duration delai) {
        return jdbc.update("UPDATE planification_optimisation SET statut = 'ECHEC', erreur = ?, termine_le = ? "
                        + "WHERE statut = 'EN_COURS' AND (battement_le IS NULL OR battement_le < ?)",
                motif, horodatage(maintenant), horodatage(maintenant.minus(delai)));
    }
}
