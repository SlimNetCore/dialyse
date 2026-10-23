package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Emplacement;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Emplacements;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

/**
 * Table {@code emplacements} (même schéma que le legacy) ; liste des actifs triée par libellé.
 */
public class EmplacementsJdbc implements Emplacements {

    private static final RowMapper<Emplacement> EMPLACEMENT = (rs, i) -> new Emplacement(rs.getObject("id", UUID.class),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("libelle"),
            rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;

    public EmplacementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enregistrer(Emplacement e) {
        jdbc.update("INSERT INTO emplacements (id, center_id, code, libelle, actif) VALUES (?, ?, ?, ?, ?)", e.id(),
                e.centre().valeur(), e.code(), e.libelle(), e.actif());
    }

    @Override
    public List<Emplacement> actifs(CentreId centre) {
        return jdbc.query("SELECT id, center_id, code, libelle, actif FROM emplacements WHERE center_id = ? AND actif = TRUE "
                + "ORDER BY libelle ASC", EMPLACEMENT, centre.valeur());
    }
}

