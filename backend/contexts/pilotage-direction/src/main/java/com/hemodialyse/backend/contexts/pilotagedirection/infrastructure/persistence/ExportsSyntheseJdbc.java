package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.FormatExport;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.StatutExport;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptateur JDBC du port {@link ExportSyntheseRepository}. Toutes les requêtes sont
 * filtrées par {@code center_id} (règle multi-centre, AGENTS.md §2).
 */
public class ExportsSyntheseJdbc implements ExportSyntheseRepository {

    private final JdbcTemplate jdbc;

    public ExportsSyntheseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private ExportSynthese mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        String periode = rs.getString("periode");
        FormatExport format = FormatExport.valueOf(rs.getString("format"));
        StatutExport statut = StatutExport.valueOf(rs.getString("statut"));
        String contenu = rs.getString("contenu");
        OffsetDateTime dateGeneration = rs.getTimestamp("date_generation") == null ? null
                : rs.getTimestamp("date_generation").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return ExportSynthese.reconstituer(new ExportSyntheseId(id), new CentreId(centreId),
                periode, format, statut, contenu, dateGeneration);
    }

    @Override
    public void save(ExportSynthese export) {
        int n = jdbc.update("UPDATE exports_synthese SET periode=?, format=?, statut=?, contenu=?, "
                        + "date_generation=? "
                        + "WHERE id=? AND center_id=?",
                export.periode(), export.format().name(), export.statut().name(), export.contenu(),
                java.sql.Timestamp.from(export.dateGeneration().toInstant()),
                export.id().value(), export.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO exports_synthese (id, center_id, periode, format, statut, "
                            + "contenu, date_generation) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    export.id().value(), export.centreId().valeur(), export.periode(),
                    export.format().name(), export.statut().name(), export.contenu(),
                    java.sql.Timestamp.from(export.dateGeneration().toInstant()));
        }
    }

    @Override
    public Optional<ExportSynthese> trouver(ExportSyntheseId id, CentreId centreId) {
        List<ExportSynthese> r = jdbc.query("SELECT id, center_id, periode, format, statut, contenu, "
                        + "date_generation "
                        + "FROM exports_synthese WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.value(), centreId.valeur());
        return r.stream().findFirst();
    }
}
