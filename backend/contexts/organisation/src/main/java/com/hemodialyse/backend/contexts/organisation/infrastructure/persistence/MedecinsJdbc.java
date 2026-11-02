package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MedecinsJdbc implements MedecinRepository {
    private static final RowMapper<Medecin> MAPPER = (rs, i) ->
            new Medecin(new MedecinId(rs.getObject("id", UUID.class)), new CentreId(rs.getObject("center_id", UUID.class)),
                    rs.getString("nom"), rs.getString("prenom"), rs.getString("specialite"), rs.getBoolean("actif"));

    private final JdbcTemplate jdbc;
    private final EvictionApresCommit eviction;

    public MedecinsJdbc(JdbcTemplate jdbc) {
        this(jdbc, EvictionApresCommit.AUCUNE);
    }

    public MedecinsJdbc(JdbcTemplate jdbc, EvictionApresCommit eviction) {
        this.jdbc = jdbc;
        this.eviction = eviction;
    }

    @Override
    public Optional<Medecin> trouver(MedecinId id) {
        return jdbc.query("SELECT id, center_id, nom, prenom, specialite, actif FROM medecin WHERE id = ?", MAPPER, id.valeur())
                .stream().findFirst();
    }

    @Override
    public void sauvegarder(Medecin m) {
        ecrire(m);
        // Les clés de ref.medecins portent la page et la taille : tout le cache est vidé, après le commit.
        eviction.retirer("ref.medecins", null);
    }

    private void ecrire(Medecin m) {
        int n = jdbc.update("UPDATE medecin SET center_id=?, nom=?, prenom=?, specialite=?, actif=? WHERE id=?",
                m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.actif(), m.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite, actif) VALUES (?,?,?,?,?,?)",
                    m.id().valeur(), m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.actif());
        }
    }

    @Override
    @Cacheable(cacheNames = "ref.medecins", key = "#centreId.valeur().toString() + ':' + #page + ':' + #size")
    public PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size) {
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM medecin WHERE center_id = ? AND actif = TRUE",
                Long.class, centreId.valeur())).orElse(0L);
        int offset = page * size;
        List<Medecin> items = jdbc.query("SELECT id, center_id, nom, prenom, specialite, actif FROM medecin "
                        + "WHERE center_id = ? AND actif = TRUE ORDER BY nom OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                MAPPER, centreId.valeur(), offset, size);
        return PagedResult.of(items, total, page, size);
    }
}
