package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.referential.port.ReferentialRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
    public List<RefItem> findCentresPayeurs(CenterId c) {
        return jdbc.query("SELECT id, code, nom, adresse, null, null FROM centre_payeur WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), null, null),
            c.value());
    }

    @Override
    public List<RefItem> findAgences(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, null FROM agence WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, null),
            c.value());
    }

    @Override
    public List<RefItem> findCaisses(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, type_caisse FROM caisse_assurance WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, rs.getString(6)),
            c.value());
    }

    @Override
    public List<RefItem> findMedecins(CenterId c) {
        return jdbc.query("SELECT id, null, nom, null, prenom, specialite FROM medecin WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), null, rs.getString(3), null, rs.getString(5), rs.getString(6)),
            c.value());
    }

    @Override
    public List<RefItem> findSalles(CenterId c) {
        return jdbc.query("SELECT id, code, nom, null, null, null FROM salle WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, null),
            c.value());
    }

    @Override
    public List<RefItem> findPositions(CenterId c) {
        return jdbc.query("SELECT id, code, null, null, null, libelle FROM position_creneau WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), null, null, null, rs.getString(6)),
            c.value());
    }

    @Override
    public List<RefItem> findTransporteurs(CenterId c) {
        return jdbc.query("SELECT id, null, nom, null, null, null FROM transporteur WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), null, rs.getString(3), null, null, null),
            c.value());
    }

    @Override
    public List<RefItem> findCategoriesTransport(CenterId c) {
        return jdbc.query("SELECT id, null, null, null, null, libelle FROM categorie_transport WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), null, null, null, null, rs.getString(6)),
            c.value());
    }

    @Override
    public List<RefItem> findForfaits(CenterId c) {
        return jdbc.query("SELECT id, code, prix, null, null, null FROM forfait WHERE center_id = ?",
            (rs, i) -> new RefItem(rs.getString(1), rs.getString(2), rs.getString(3), null, null, null),
            c.value());
    }
}

