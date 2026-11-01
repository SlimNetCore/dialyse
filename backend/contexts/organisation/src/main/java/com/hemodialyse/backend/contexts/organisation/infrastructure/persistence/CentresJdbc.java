package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Centre;
import com.hemodialyse.backend.contexts.organisation.domain.CentreRepository;
import com.hemodialyse.backend.contexts.organisation.domain.Coordonnees;
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
            new Coordonnees(rs.getString("adresse"), rs.getString("ville"), rs.getString("wilaya"),
                    rs.getString("telephone"), rs.getString("email"), rs.getString("site_web")),
            rs.getString("fuseau_horaire") == null ? "UTC" : rs.getString("fuseau_horaire"),
            rs.getBoolean("actif"));
    private static final String COLONNES = "id, code, name, societe_id, adresse, ville, wilaya, "
            + "telephone, email, site_web, fuseau_horaire, actif";
    private final JdbcTemplate jdbc;

    public CentresJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Centre> trouver(CentreId id) {
        List<Centre> r = jdbc.query("SELECT " + COLONNES + " FROM centers WHERE id = ?", MAPPER, id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Centre c) {
        int n = jdbc.update("UPDATE centers SET code=?, name=?, societe_id=?, adresse=?, ville=?, "
                        + "wilaya=?, telephone=?, email=?, site_web=?, fuseau_horaire=?, actif=? WHERE id=?",
                c.code(), c.nom(), c.societeId().valeur(),
                c.coordonnees().adresse(), c.coordonnees().ville(), c.coordonnees().wilaya(),
                c.coordonnees().telephone(), c.coordonnees().email(), c.coordonnees().siteWeb(),
                c.fuseauHoraire(), c.actif(), c.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, adresse, ville, wilaya, "
                            + "telephone, email, site_web, fuseau_horaire, actif) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    c.id().valeur(), c.code(), c.nom(), c.societeId().valeur(),
                    c.coordonnees().adresse(), c.coordonnees().ville(), c.coordonnees().wilaya(),
                    c.coordonnees().telephone(), c.coordonnees().email(), c.coordonnees().siteWeb(),
                    c.fuseauHoraire(), c.actif());
        }
    }

    @Override
    public List<Centre> listerActifs(SocieteId societeId) {
        return jdbc.query("SELECT " + COLONNES + " FROM centers WHERE societe_id = ? AND actif = TRUE",
                MAPPER, societeId.valeur());
    }

    @Override
    public List<Centre> listerParSociete(SocieteId societeId) {
        return jdbc.query("SELECT " + COLONNES + " FROM centers WHERE societe_id = ? ORDER BY code",
                MAPPER, societeId.valeur());
    }
}
