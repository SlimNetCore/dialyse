package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Machine;
import com.hemodialyse.backend.contexts.organisation.domain.MachineId;
import com.hemodialyse.backend.contexts.organisation.domain.MachineRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MachinesJdbc implements MachineRepository {
    private final JdbcTemplate jdbc;

    public MachinesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Machine> trouver(MachineId id) {
        List<Machine> r = jdbc.query("SELECT id, center_id, code, libelle, actif FROM machine WHERE id = ?", (rs, i) ->
                new Machine(new MachineId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle"), rs.getBoolean("actif")), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Machine m) {
        int n = jdbc.update("UPDATE machine SET center_id=?, code=?, libelle=?, actif=? WHERE id=?", m.centreId().valeur(), m.code(), m.libelle(), m.actif(), m.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO machine (id, center_id, code, libelle, actif) VALUES (?,?,?,?,?)", m.id().valeur(), m.centreId().valeur(), m.code(), m.libelle(), m.actif());
        }
    }

    @Override
    public void supprimer(CentreId centreId, MachineId id) {
        jdbc.update("DELETE FROM machine WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
    }

    @Override
    public PagedResult<Machine> listerParCentre(CentreId centreId, int page, int size) {
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM machine WHERE center_id = ?", Long.class, centreId.valeur())).orElse(0L);
        int offset = page * size;
        List<Machine> items = jdbc.query("SELECT id, center_id, code, libelle, actif FROM machine WHERE center_id = ? ORDER BY code OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, i) -> new Machine(new MachineId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle"), rs.getBoolean("actif")),
                centreId.valeur(), offset, size);
        return PagedResult.of(items, total, page, size);
    }
}
