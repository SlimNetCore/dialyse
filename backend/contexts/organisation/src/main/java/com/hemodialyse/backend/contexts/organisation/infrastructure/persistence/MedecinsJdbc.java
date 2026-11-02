package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MedecinsJdbc implements MedecinRepository {
    private final JdbcTemplate jdbc;

    public MedecinsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Medecin> trouver(MedecinId id) {
        List<Medecin> r = jdbc.query("SELECT id, center_id, nom, prenom, specialite FROM medecin WHERE id = ?", (rs, i) ->
                new Medecin(new MedecinId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("nom"), rs.getString("prenom"), rs.getString("specialite")), id.valeur());
        return r.stream().findFirst();
    }

    @Override
    public void sauvegarder(Medecin m) {
        int n = jdbc.update("UPDATE medecin SET center_id=?, nom=?, prenom=?, specialite=? WHERE id=?", m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite) VALUES (?,?,?,?,?)", m.id().valeur(), m.centreId().valeur(), m.nom(), m.prenom(), m.specialite());
        }
    }

    @Override
    public void supprimer(CentreId centreId, MedecinId id) {
        jdbc.update("DELETE FROM medecin WHERE id = ? AND center_id = ?", id.valeur(), centreId.valeur());
    }

    @Override
    @Cacheable(cacheNames = "ref.medecins", key = "#centreId.valeur().toString() + ':' + #page + ':' + #size")
    public PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size) {
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM medecin WHERE center_id = ?", Long.class, centreId.valeur())).orElse(0L);
        int offset = page * size;
        List<Medecin> items = jdbc.query("SELECT id, center_id, nom, prenom, specialite FROM medecin WHERE center_id = ? ORDER BY nom OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, i) -> new Medecin(new MedecinId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                        rs.getString("nom"), rs.getString("prenom"), rs.getString("specialite")),
                centreId.valeur(), offset, size);
        // H2/Postgres compatible? OFFSET/FETCH supported. Fallback if not: use LIMIT? We'll keep.
        return PagedResult.of(items, total, page, size);
    }
}
