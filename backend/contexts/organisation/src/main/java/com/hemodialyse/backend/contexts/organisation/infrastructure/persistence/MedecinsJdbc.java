package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
        // Les clés de ref.medecins sont « centre:page:taille » : seules les pages déjà mises en cache pour CE centre sont retirées
        // (isolation multi-centre, comme l'administration générique avant PL11-3), après le commit.
        String centre = m.centreId().valeur().toString();
        for (String cle : pagesEnCache.getOrDefault(centre, Set.of())) eviction.retirer("ref.medecins", cle);
    }

    /** Pages ({@code centre:page:taille}) mises en cache par centre ; alimenté quand une page est lue hors cache. */
    private final Map<String, Set<String>> pagesEnCache = new ConcurrentHashMap<>();

    private void ecrire(Medecin m) {
        int n = jdbc.update("UPDATE medecin SET center_id=?, nom=?, prenom=?, specialite=?, actif=? WHERE id=?",
                m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.actif(), m.id().valeur());
        if (n == 0) {
            jdbc.update("INSERT INTO medecin (id, center_id, nom, prenom, specialite, actif) VALUES (?,?,?,?,?,?)",
                    m.id().valeur(), m.centreId().valeur(), m.nom(), m.prenom(), m.specialite(), m.actif());
        }
    }

    @Override
    public List<Medecin> listerTousParCentre(CentreId centreId) {
        return jdbc.query("SELECT id, center_id, nom, prenom, specialite, actif FROM medecin WHERE center_id = ? ORDER BY nom, prenom, id",
                MAPPER, centreId.valeur());
    }

    @Override
    public PageDeMedecins page(CentreId centreId, String recherche, int page, int taille) {
        List<Object> params = new ArrayList<>();
        params.add(centreId.valeur());
        String filtre = " WHERE center_id = ? AND actif = TRUE";
        if (recherche != null && !recherche.isBlank()) {
            String like = "%" + recherche.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filtre += " AND (LOWER(COALESCE(nom, '')) LIKE ? ESCAPE '\\' OR LOWER(COALESCE(prenom, '')) LIKE ? ESCAPE '\\'"
                    + " OR LOWER(COALESCE(specialite, '')) LIKE ? ESCAPE '\\')";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM medecin" + filtre, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(taille);
        pageParams.add((long) page * taille);
        List<Medecin> lignes = jdbc.query("SELECT id, center_id, nom, prenom, specialite, actif FROM medecin" + filtre
                + " ORDER BY nom, prenom, id LIMIT ? OFFSET ?", MAPPER, pageParams.toArray());
        return new PageDeMedecins(lignes, total == null ? 0 : total);
    }

    @Override
    @Cacheable(cacheNames = "ref.medecins", key = "#centreId.valeur().toString() + ':' + #page + ':' + #size")
    public PagedResult<Medecin> listerParCentre(CentreId centreId, int page, int size) {
        String centre = centreId.valeur().toString();
        pagesEnCache.computeIfAbsent(centre, c -> ConcurrentHashMap.newKeySet()).add(centre + ":" + page + ":" + size);
        long total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM medecin WHERE center_id = ? AND actif = TRUE",
                Long.class, centreId.valeur())).orElse(0L);
        int offset = page * size;
        List<Medecin> items = jdbc.query("SELECT id, center_id, nom, prenom, specialite, actif FROM medecin "
                        + "WHERE center_id = ? AND actif = TRUE ORDER BY nom OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                MAPPER, centreId.valeur(), offset, size);
        return PagedResult.of(items, total, page, size);
    }
}
