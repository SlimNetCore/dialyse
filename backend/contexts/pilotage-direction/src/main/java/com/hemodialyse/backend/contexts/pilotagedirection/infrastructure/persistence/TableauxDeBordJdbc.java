package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBord;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link TableauDeBordRepository}. Toutes les requêtes sont filtrées
 * par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class TableauxDeBordJdbc implements TableauDeBordRepository {

    private final JdbcTemplate jdbc;

    public TableauxDeBordJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private TableauDeBord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID centreId = rs.getObject("center_id", UUID.class);
        String indicateurs = rs.getString("indicateurs");
        OffsetDateTime dateGeneration = rs.getTimestamp("date_generation") == null ? null
                : rs.getTimestamp("date_generation").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return TableauDeBord.reconstituer(new CentreId(centreId), indicateurs, dateGeneration);
    }

    @Override
    public void save(TableauDeBord tableau) {
        int n = jdbc.update("UPDATE tableaux_de_bord SET indicateurs=?, date_generation=? "
                        + "WHERE center_id=?",
                tableau.indicateurs(),
                java.sql.Timestamp.from(tableau.dateGeneration().toInstant()),
                tableau.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO tableaux_de_bord (center_id, indicateurs, date_generation) "
                            + "VALUES (?,?,?)",
                    tableau.centreId().valeur(), tableau.indicateurs(),
                    java.sql.Timestamp.from(tableau.dateGeneration().toInstant()));
        }
    }

    @Override
    public Optional<TableauDeBord> trouver(CentreId centreId) {
        List<TableauDeBord> r = jdbc.query("SELECT center_id, indicateurs, date_generation "
                        + "FROM tableaux_de_bord WHERE center_id = ?",
                (rs, i) -> mapRow(rs), centreId.valeur());
        return r.stream().findFirst();
    }
}
