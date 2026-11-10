package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.domain.DetailPayeur;
import com.hemodialyse.backend.contexts.organisation.domain.EntreeReferentiel;
import com.hemodialyse.backend.contexts.organisation.domain.ReferentielsCommunsRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Adaptateur JDBC du port {@link ReferentielsCommunsRepository}.
 * Requêtes reprises telles quelles du legacy ({@code ReferentialRepositoryAdapter}) :
 * mêmes tables orphelines, même tri, même filtrage {@code center_id}.
 * Caches {@code ref.*} déjà enregistrés dans {@code CacheConfig} (TTL référentiels).
 */
public class ReferentielsCommunsJdbc implements ReferentielsCommunsRepository {

    private final JdbcTemplate jdbc;

    public ReferentielsCommunsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private EntreeReferentiel ligne(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new EntreeReferentiel(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7) == null ? null : rs.getBoolean(7));
    }

    @Override
    @Cacheable(cacheNames = "ref.centresPayeurs", key = "#centreId.valeur().toString()")
    public List<EntreeReferentiel> centresPayeurs(CentreId centreId) {
        return jdbc.query("SELECT id, code, nom, adresse, null, null, null FROM centre_payeur "
                        + "WHERE center_id = ? ORDER BY nom",
                (rs, i) -> ligne(rs), centreId.valeur());
    }

    @Override
    @Cacheable(cacheNames = "ref.centresPayeursDetails", key = "#centreId.valeur().toString()")
    public List<DetailPayeur> centresPayeursDetails(CentreId centreId) {
        return jdbc.query(
                "SELECT cp.id, cp.code, cp.nom, cp.adresse, ag.code, ag.nom, ca.code, ca.nom "
                        + "FROM centre_payeur cp "
                        + "LEFT JOIN agence ag ON ag.id = cp.agence_id "
                        + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id "
                        + "WHERE cp.center_id = ? ORDER BY cp.nom",
                (rs, i) -> new DetailPayeur(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getString(7), rs.getString(8)),
                centreId.valeur());
    }

    @Override
    @Cacheable(cacheNames = "ref.agences", key = "#centreId.valeur().toString()")
    public List<EntreeReferentiel> agences(CentreId centreId) {
        return jdbc.query("SELECT id, code, nom, null, null, null, null FROM agence "
                        + "WHERE center_id = ? ORDER BY nom",
                (rs, i) -> ligne(rs), centreId.valeur());
    }

    @Override
    @Cacheable(cacheNames = "ref.caisses", key = "#centreId.valeur().toString()")
    public List<EntreeReferentiel> caisses(CentreId centreId) {
        return jdbc.query("SELECT id, code, nom, null, null, type_caisse, null FROM caisse_assurance "
                        + "WHERE center_id = ? ORDER BY nom",
                (rs, i) -> ligne(rs), centreId.valeur());
    }
}
