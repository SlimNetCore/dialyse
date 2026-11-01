package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Societe;
import com.hemodialyse.backend.contexts.organisation.domain.SocieteRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SocietesJdbc implements SocieteRepository {
    private static final RowMapper<Societe> MAPPER = (rs, i) -> new Societe(
            new SocieteId(rs.getObject("id", UUID.class)),
            rs.getString("code"),
            rs.getString("raison_sociale"),
            rs.getBoolean("actif"));
    private final JdbcTemplate jdbc;

    public SocietesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Societe> trouver(SocieteId id) {
        List<Societe> r = jdbc.query("SELECT id, code, raison_sociale, actif FROM societes WHERE id = ?", MAPPER, id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Societe s) {
        int n = jdbc.update("UPDATE societes SET code=?, raison_sociale=?, actif=? WHERE id=?", s.code(), s.raisonSociale(), s.actif(), s.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)", s.id().valeur(), s.code(), s.raisonSociale(), s.actif());
        }
    }

    @Override
    public List<Societe> lister() {
        return jdbc.query("SELECT id, code, raison_sociale, actif FROM societes ORDER BY code", MAPPER);
    }
}
