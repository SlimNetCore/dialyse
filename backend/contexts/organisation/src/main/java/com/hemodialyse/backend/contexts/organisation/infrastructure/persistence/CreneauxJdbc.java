package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CreneauxJdbc implements CreneauRepository {
    private final JdbcTemplate jdbc;

    public CreneauxJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Creneau> trouver(CentreId centreId, CreneauId id) {
        List<Creneau> r = jdbc.query("SELECT id, center_id, code, libelle FROM position_creneau WHERE center_id = ? AND id = ?",
                (rs, i) -> new Creneau(new CreneauId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle")),
                centreId.valeur(), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Creneau c) {
        int n = jdbc.update("UPDATE position_creneau SET code=?, libelle=? WHERE id=? AND center_id=?", c.code(), c.libelle(), c.id().valeur(), c.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle) VALUES (?,?,?,?)",
                    c.id().valeur(), c.centreId().valeur(), c.code(), c.libelle());
        }
    }

    @Override
    @Cacheable(cacheNames = "ref.positions", key = "#centreId.valeur().toString()")
    public List<Creneau> listerParCentre(CentreId centreId) {
        return jdbc.query("SELECT id, center_id, code, libelle FROM position_creneau WHERE center_id = ? ORDER BY code",
                (rs, i) -> new Creneau(new CreneauId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("code"), rs.getString("libelle")),
                centreId.valeur());
    }
}
