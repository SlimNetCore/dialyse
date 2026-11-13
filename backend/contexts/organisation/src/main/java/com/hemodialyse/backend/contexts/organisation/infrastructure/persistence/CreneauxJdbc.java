package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class CreneauxJdbc implements CreneauRepository {
    private static final String COLONNES = "SELECT id, center_id, code, libelle, actif FROM position_creneau ";

    private static final RowMapper<Creneau> CRENEAU = (rs, i) -> new Creneau(new CreneauId(rs.getObject("id", UUID.class)),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("libelle"),
            rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;
    private final EvictionApresCommit eviction;

    public CreneauxJdbc(JdbcTemplate jdbc) {
        this(jdbc, EvictionApresCommit.AUCUNE);
    }

    public CreneauxJdbc(JdbcTemplate jdbc, EvictionApresCommit eviction) {
        this.jdbc = jdbc;
        this.eviction = eviction;
    }

    @Override
    public Optional<Creneau> trouver(CentreId centreId, CreneauId id) {
        return jdbc.query(COLONNES + "WHERE center_id = ? AND id = ?", CRENEAU, centreId.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public void sauvegarder(Creneau c) {
        ecrire(c);
        eviction.retirer("ref.positions", c.centreId().valeur().toString());
    }

    private void ecrire(Creneau c) {
        int n = jdbc.update("UPDATE position_creneau SET code=?, libelle=?, actif=? WHERE id=? AND center_id=?",
                c.code(), c.libelle(), c.actif(), c.id().valeur(), c.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO position_creneau (id, center_id, code, libelle, actif) VALUES (?,?,?,?,?)",
                    c.id().valeur(), c.centreId().valeur(), c.code(), c.libelle(), c.actif());
        }
    }

    @Override
    public void supprimer(CentreId centreId, CreneauId id) {
        jdbc.update("DELETE FROM position_creneau WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
        eviction.retirer("ref.positions", centreId.valeur().toString());
    }

    @Override
    @Cacheable(cacheNames = "ref.positions", key = "#centreId.valeur().toString()")
    public List<Creneau> listerParCentre(CentreId centreId) {
        return jdbc.query(COLONNES + "WHERE center_id = ? AND actif = TRUE ORDER BY code", CRENEAU, centreId.valeur());
    }

    @Override
    public List<Creneau> listerTousParCentre(CentreId centreId) {
        return jdbc.query(COLONNES + "WHERE center_id = ? ORDER BY code, id", CRENEAU, centreId.valeur());
    }

    @Override
    public PageDeCreneaux page(CentreId centreId, String recherche, int page, int taille) {
        List<Object> params = new ArrayList<>();
        params.add(centreId.valeur());
        String filtre = " WHERE center_id = ? AND actif = TRUE";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filtre += " AND (LOWER(COALESCE(code, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(libelle, '')) LIKE ? ESCAPE '\\')";
            params.add(like);
            params.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM position_creneau" + filtre, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(taille);
        pageParams.add((long) page * taille);
        List<Creneau> lignes = jdbc.query(COLONNES + filtre + " ORDER BY code, id LIMIT ? OFFSET ?", CRENEAU, pageParams.toArray());
        return new PageDeCreneaux(lignes, total == null ? 0 : total);
    }
}
