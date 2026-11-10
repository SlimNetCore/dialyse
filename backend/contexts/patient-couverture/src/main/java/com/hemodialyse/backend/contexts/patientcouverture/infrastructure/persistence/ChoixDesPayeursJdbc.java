package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.ChoixDesPayeurs;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Listes de choix des payeurs sur les tables {@code caisse_assurance}, {@code agence} et {@code centre_payeur} (RF3c). Requêtes reprises
 * TELLES QUELLES de l'Organisation ({@code ReferentielsCommunsJdbc}) : même tri {@code ORDER BY nom} (collation de la base, sans
 * départage), jointures de la liste détaillée non bornées par centre (comme avant).
 */
public class ChoixDesPayeursJdbc implements ChoixDesPayeurs {

    private final JdbcTemplate jdbc;

    public ChoixDesPayeursJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChoixDePayeur> choixDeCaisses(UUID centre) {
        return jdbc.query("SELECT id, code, nom, type_caisse FROM caisse_assurance WHERE center_id = ? ORDER BY nom",
                (rs, i) -> new ChoixDePayeur(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), null, rs.getString(4)), centre);
    }

    @Override
    public List<ChoixDePayeur> choixDAgences(UUID centre) {
        return jdbc.query("SELECT id, code, nom FROM agence WHERE center_id = ? ORDER BY nom",
                (rs, i) -> new ChoixDePayeur(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), null, null), centre);
    }

    @Override
    public List<ChoixDePayeur> choixDeCentresPayeurs(UUID centre) {
        return jdbc.query("SELECT id, code, nom, adresse FROM centre_payeur WHERE center_id = ? ORDER BY nom",
                (rs, i) -> new ChoixDePayeur(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), null), centre);
    }

    @Override
    public List<DetailDeCentrePayeur> detailsDesCentresPayeurs(UUID centre) {
        return jdbc.query("SELECT cp.id, cp.code, cp.nom, cp.adresse, ag.code, ag.nom, ca.code, ca.nom "
                        + "FROM centre_payeur cp "
                        + "LEFT JOIN agence ag ON ag.id = cp.agence_id "
                        + "LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id "
                        + "WHERE cp.center_id = ? ORDER BY cp.nom",
                (rs, i) -> new DetailDeCentrePayeur(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), centre);
    }
}
