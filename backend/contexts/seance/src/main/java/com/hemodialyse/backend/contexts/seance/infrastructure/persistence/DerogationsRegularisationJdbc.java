package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.DerogationId;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.DerogationRegularisationRepository;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DerogationsRegularisationJdbc implements DerogationRegularisationRepository {
    private final JdbcTemplate jdbc;

    public DerogationsRegularisationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private DerogationRegularisation mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID centreId = rs.getObject("center_id", UUID.class);
        UUID seanceId = rs.getObject("seance_id", UUID.class);
        String motif = rs.getString("motif");
        String autorite = rs.getString("autorite");
        OffsetDateTime date = rs.getTimestamp("date_derogation") == null ? null
                : rs.getTimestamp("date_derogation").toInstant().atOffset(java.time.ZoneOffset.UTC);
        return new DerogationRegularisation(new DerogationId(id), new CentreId(centreId),
                new SeanceId(seanceId), motif, autorite, date);
    }

    @Override
    public Optional<DerogationRegularisation> trouver(DerogationId id, CentreId centreId) {
        List<DerogationRegularisation> r = jdbc.query(
                "SELECT id, center_id, seance_id, motif, autorite, date_derogation FROM derogations_regularisation WHERE id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), id.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public Optional<DerogationRegularisation> trouverParSeance(SeanceId seanceId, CentreId centreId) {
        List<DerogationRegularisation> r = jdbc.query(
                "SELECT id, center_id, seance_id, motif, autorite, date_derogation FROM derogations_regularisation WHERE seance_id = ? AND center_id = ?",
                (rs, i) -> mapRow(rs), seanceId.valeur(), centreId.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(DerogationRegularisation d) {
        int n = jdbc.update(
                "UPDATE derogations_regularisation SET motif=?, autorite=?, date_derogation=? WHERE id=? AND center_id=?",
                d.motif(), d.autorite(), Timestamp.from(d.date().toInstant()), d.id().valeur(), d.centreId().valeur());
        if (n == 0) {
            jdbc.update(
                    "INSERT INTO derogations_regularisation (id, center_id, seance_id, motif, autorite, date_derogation) VALUES (?,?,?,?,?,?)",
                    d.id().valeur(), d.centreId().valeur(), d.seanceId().valeur(), d.motif(), d.autorite(),
                    Timestamp.from(d.date().toInstant()));
        }
    }
}