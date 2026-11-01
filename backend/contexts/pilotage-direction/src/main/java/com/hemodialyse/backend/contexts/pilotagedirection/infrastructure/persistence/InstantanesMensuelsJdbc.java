package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.Mois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link InstantaneMensuelRepository}. Toutes les requêtes sont
 * filtrées par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class InstantanesMensuelsJdbc implements InstantaneMensuelRepository {

    private final JdbcTemplate jdbc;

    public InstantanesMensuelsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private InstantaneMensuel mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        Mois mois = Mois.de(rs.getInt("annee"), rs.getInt("mois"));
        String donnees = rs.getString("donnees");
        boolean fige = Boolean.TRUE.equals(rs.getObject("fige", Boolean.class));
        OffsetDateTime createdAt = rs.getTimestamp("created_at") == null ? null
                : rs.getTimestamp("created_at").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return InstantaneMensuel.reconstituer(new InstantaneMensuelId(id),
                new CentreId(centreId), mois, donnees, fige, createdAt);
    }

    @Override
    public void save(InstantaneMensuel instantane) {
        int n = jdbc.update("UPDATE instantanes_mensuels SET donnees=?, fige=? "
                        + "WHERE id=? AND center_id=?",
                instantane.donnees(), instantane.fige(),
                instantane.id().value(), instantane.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO instantanes_mensuels (id, center_id, mois, annee, donnees, fige, created_at) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    instantane.id().value(), instantane.centreId().valeur(),
                    instantane.mois().valeur().getMonthValue(), instantane.mois().valeur().getYear(),
                    instantane.donnees(), instantane.fige(),
                    java.sql.Timestamp.from(instantane.createdAt().toInstant()));
        }
    }

    @Override
    public Optional<InstantaneMensuel> trouver(InstantaneMensuelId id, CentreId centreId) {
        List<InstantaneMensuel> r = jdbc.query("SELECT id, center_id, mois, annee, donnees, fige, created_at "
                        + "FROM instantanes_mensuels WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public boolean existeParCentreEtMois(CentreId centreId, Mois mois) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM instantanes_mensuels "
                        + "WHERE center_id = ? AND mois = ? AND annee = ?",
                Integer.class, centreId.valeur(), mois.valeur().getMonthValue(), mois.valeur().getYear());
        return n != null && n > 0;
    }

    @Override
    public PagedResult<InstantaneMensuel> listerParCentre(CentreId centreId, int page, int size) {
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM instantanes_mensuels WHERE center_id = ?",
                Long.class, centreId.valeur());
        List<InstantaneMensuel> items = jdbc.query("SELECT id, center_id, mois, annee, donnees, fige, created_at "
                        + "FROM instantanes_mensuels WHERE center_id = ? "
                        + "ORDER BY annee DESC, mois DESC LIMIT ? OFFSET ?",
                (rs, i) -> mapRow(rs), centreId.valeur(), size, page * size);
        return PagedResult.of(items, total, page, size);
    }
}
