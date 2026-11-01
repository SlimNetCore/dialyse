package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Protocole;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleId;
import com.hemodialyse.backend.contexts.organisation.domain.ProtocoleRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ProtocolesJdbc implements ProtocoleRepository {
    private final JdbcTemplate jdbc;

    public ProtocolesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Protocole> trouver(ProtocoleId id) {
        List<Protocole> r = jdbc.query("SELECT id, center_id, code, libelle FROM protocole WHERE id = ?", (rs, i) ->
                new Protocole(new ProtocoleId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle")), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Protocole p) {
        int n = jdbc.update("UPDATE protocole SET center_id=?, code=?, libelle=? WHERE id=?", p.centreId().valeur(), p.code(), p.libelle(), p.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO protocole (id, center_id, code, libelle) VALUES (?,?,?,?)", p.id().valeur(), p.centreId().valeur(), p.code(), p.libelle());
        }
    }

    @Override
    public void supprimer(ProtocoleId id) {
        jdbc.update("DELETE FROM protocole WHERE id = ?", id.valeur());
    }

    @Override
    public PagedResult<Protocole> listerParCentre(CentreId centreId, int page, int size) {
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM protocole WHERE center_id = ?", Long.class, centreId.valeur())).orElse(0L);
        int offset = page * size;
        List<Protocole> items = jdbc.query("SELECT id, center_id, code, libelle FROM protocole WHERE center_id = ? ORDER BY code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, i) -> new Protocole(new ProtocoleId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle")),
                centreId.valeur(), offset, size);
        return PagedResult.of(items, total, page, size);
    }
}
