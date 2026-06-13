package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter for all referential lookups — uses JdbcTemplate for simple read-only queries.
 */
@Component
public class ReferentialRepositoryAdapter implements ReferentialRepositoryPort {

    private final JdbcTemplate jdbc;

    public ReferentialRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Cacheable(cacheNames = "ref.centresPayeurs", key = "#c.value().toString()")
    public List<RefItem> findCentresPayeurs(CenterId c) {
        return jdbc.query("SELECT id, code, nom, adresse, null, null FROM centre_payeur WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), null, null, null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.agences", key = "#c.value().toString()")
    public List<RefItem> findAgences(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, null FROM agence WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, null, null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.caisses", key = "#c.value().toString()")
    public List<RefItem> findCaisses(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, type_caisse FROM caisse_assurance WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, rs.getString(6), null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.medecins", key = "#c.value().toString()")
    public List<RefItem> findMedecins(CenterId c) {
        return jdbc.query("SELECT id, null, nom, null, prenom, specialite FROM medecin WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), null, rs.getString(3), null, rs.getString(5), rs.getString(6), null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.salles", key = "#c.value().toString()")
    public List<RefItem> findSalles(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, null FROM salle WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, null, null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.positions", key = "#c.value().toString()")
    public List<RefItem> findPositions(CenterId c) {
        return jdbc.query("SELECT id, code, null, null, null, libelle FROM position_creneau WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), null, null, null, rs.getString(6), null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.transporteurs", key = "#c.value().toString()")
    public List<RefItem> findTransporteurs(CenterId c) {
        return jdbc.query("SELECT id, null, nom, null, null, null FROM transporteur WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), null, rs.getString(3), null, null, null, null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.categoriesTransport", key = "#c.value().toString()")
    public List<RefItem> findCategoriesTransport(CenterId c) {
        return jdbc.query("SELECT id, null, null, null, null, libelle FROM categorie_transport WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), null, null, null, null, rs.getString(6), null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.forfaits", key = "#c.value().toString()")
    public List<RefItem> findForfaits(CenterId c) {
        return jdbc.query("SELECT id, code, libelle, null, null, CAST(prix AS VARCHAR) FROM forfait WHERE center_id = ?",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, rs.getString(6), null),
            c.value());
    }

    @Override
    @Cacheable(cacheNames = "ref.articles", key = "#c.value().toString()")
    public List<RefItem> findArticles(CenterId c) {
        return jdbc.query(
                "SELECT id, code, libelle, null, null, unite, gere_par_lot FROM articles WHERE center_id = ? AND active = true ORDER BY libelle",
                (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, rs.getString(6), rs.getBoolean(7)),
                c.value()
        );
    }

    @Override
    @Cacheable(cacheNames = "ref.centresPayeursDetails", key = "#c.value().toString()")
    public List<CentrePayeurDetail> findCentresPayeursDetails(CenterId c) {
        return jdbc.query(
                "SELECT cp.id, cp.code, cp.nom, cp.adresse, ag.code, ag.nom, ca.code, ca.nom " +
                        "FROM centre_payeur cp " +
                        "LEFT JOIN agence ag ON ag.id = cp.agence_id " +
                        "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id " +
                        "WHERE cp.center_id = ? ORDER BY cp.nom",
                (rs, i) -> new CentrePayeurDetail(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getString(8)
                ),
                c.value()
        );
    }
}
