package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class SallesJdbc implements SalleRepository {
    private static final String COLONNES = "SELECT id, center_id, code, nom, ouverte, isolement, capacite, actif FROM salle ";

    private static final RowMapper<Salle> SALLE = (rs, i) -> new Salle(new SalleId(rs.getObject("id", UUID.class)),
            new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("code"), rs.getString("nom"), rs.getBoolean("ouverte"),
            "OUI".equals(rs.getString("isolement")), rs.getObject("capacite") == null ? null : rs.getInt("capacite"),
            rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;
    private final EvictionApresCommit eviction;

    public SallesJdbc(JdbcTemplate jdbc) {
        this(jdbc, EvictionApresCommit.AUCUNE);
    }

    public SallesJdbc(JdbcTemplate jdbc, EvictionApresCommit eviction) {
        this.jdbc = jdbc;
        this.eviction = eviction;
    }

    @Override
    public Optional<Salle> trouver(CentreId centreId, SalleId id) {
        return jdbc.query(COLONNES + "WHERE center_id = ? AND id = ?", SALLE, centreId.valeur(), id.valeur()).stream().findFirst();
    }

    @Override
    public void sauvegarder(Salle s) {
        ecrire(s);
        evincer(s.centreId());
    }

    /** Après l'écriture, et après le commit s'il y a une transaction : salles et générateurs (rattachés aux salles) du centre. */
    private void evincer(CentreId centreId) {
        eviction.retirer("ref.salles", centreId.valeur().toString());
        eviction.retirer("ref.generateurs", centreId.valeur().toString());
    }

    private void ecrire(Salle s) {
        String isolement = s.isolement() ? "OUI" : "NON";
        int n = jdbc.update("UPDATE salle SET code=?, nom=?, ouverte=?, isolement=?, capacite=?, actif=? WHERE id=? AND center_id=?",
                s.code(), s.nom(), s.ouverte(), isolement, s.capacite(), s.actif(), s.id().valeur(), s.centreId().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO salle (id, center_id, code, nom, ouverte, isolement, capacite, actif) VALUES (?,?,?,?,?,?,?,?)",
                    s.id().valeur(), s.centreId().valeur(), s.code(), s.nom(), s.ouverte(), isolement, s.capacite(), s.actif());
        }
    }

    @Override
    public void supprimer(CentreId centreId, SalleId id) {
        jdbc.update("DELETE FROM salle WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
        evincer(centreId);
    }

    @Override
    @Cacheable(cacheNames = "ref.salles", key = "#centreId.valeur().toString()")
    public List<Salle> listerParCentre(CentreId centreId) {
        return jdbc.query(COLONNES + "WHERE center_id = ? AND actif = TRUE ORDER BY code", SALLE, centreId.valeur());
    }

    @Override
    public List<Salle> listerTousParCentre(CentreId centreId) {
        return jdbc.query(COLONNES + "WHERE center_id = ? ORDER BY code, id", SALLE, centreId.valeur());
    }

    @Override
    public PageDeSalles page(CentreId centreId, String recherche, int page, int taille) {
        List<Object> params = new ArrayList<>();
        params.add(centreId.valeur());
        String filtre = " WHERE center_id = ? AND actif = TRUE";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filtre += " AND (LOWER(COALESCE(code, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(nom, '')) LIKE ? ESCAPE '\\')";
            params.add(like);
            params.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM salle" + filtre, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(taille);
        pageParams.add((long) page * taille);
        List<Salle> lignes = jdbc.query(COLONNES + filtre + " ORDER BY code, id LIMIT ? OFFSET ?", SALLE, pageParams.toArray());
        return new PageDeSalles(lignes, total == null ? 0 : total);
    }
}
