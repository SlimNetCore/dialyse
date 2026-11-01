package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CentresJdbc implements CentreRepository {
    private static final RowMapper<Centre> MAPPER = (rs, i) -> new Centre(
            new CentreId(rs.getObject("id", UUID.class)),
            new SocieteId(rs.getObject("societe_id", UUID.class)),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("fuseau") == null ? "UTC" : rs.getString("fuseau"),
            rs.getInt("capacite"),
            rs.getBoolean("actif"));
    private final JdbcTemplate jdbc;

    public CentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Centre> trouver(CentreId id) {
        List<Centre> r = jdbc.query("SELECT id, code, name, societe_id, actif FROM centers WHERE id = ?", (rs, i) -> new Centre(
                new CentreId(rs.getObject("id", UUID.class)),
                new SocieteId(rs.getObject("societe_id", UUID.class)),
                rs.getString("code"),
                rs.getString("name"),
                "UTC",
                0,
                rs.getBoolean("actif")), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Centre c) {
        int n = jdbc.update("UPDATE centers SET code=?, name=?, societe_id=?, actif=? WHERE id=?", c.code(), c.nom(), c.societeId().valeur(), c.actif(), c.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,?)", c.id().valeur(), c.code(), c.nom(), c.societeId().valeur(), c.actif());
        }
    }

    @Override
    public List<Centre> listerActifs(SocieteId societeId) {
        return jdbc.query("SELECT id, code, name, societe_id, actif FROM centers WHERE societe_id = ? AND actif = TRUE", (rs, i) -> new Centre(
                new CentreId(rs.getObject("id", UUID.class)),
                new SocieteId(rs.getObject("societe_id", UUID.class)),
                rs.getString("code"),
                rs.getString("name"),
                "UTC",
                0,
                rs.getBoolean("actif")), societeId.valeur());
    }

    @Override
    public List<Centre> listerParSociete(SocieteId societeId) {
        return listerActifs(societeId);
    }
}
