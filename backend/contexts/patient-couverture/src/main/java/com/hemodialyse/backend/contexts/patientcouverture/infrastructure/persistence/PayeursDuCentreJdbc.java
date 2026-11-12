package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@link PayeursDuCentre} sur les tables actuelles {@code caisse_assurance}, {@code agence} et {@code centre_payeur}
 * (RF3b). Toutes les lectures sont bornées au centre, y compris les jointures de la chaîne ({@code ag.center_id = cp.center_id},
 * {@code ca.center_id = cp.center_id}). Tri {@code code, id} (collation de la base, comme avant).
 */
public class PayeursDuCentreJdbc implements PayeursDuCentre {

    private static final String CHAINE = """
            SELECT cp.id, cp.code, cp.nom, cp.adresse, cp.agence_id,
                   ag.id AS ag_id, ag.code AS ag_code, ag.nom AS ag_nom, ag.caisse_id AS ag_caisse_id,
                   ca.id AS ca_id, ca.code AS ca_code, ca.nom AS ca_nom, ca.type_caisse AS ca_type
            FROM centre_payeur cp
            LEFT JOIN agence ag ON ag.id = cp.agence_id AND ag.center_id = cp.center_id
            LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id AND ca.center_id = cp.center_id
            WHERE cp.center_id = ?""";

    private final JdbcTemplate jdbc;

    public PayeursDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CaissePubliee> caisses(UUID centre) {
        return jdbc.query("SELECT id, code, nom, type_caisse FROM caisse_assurance WHERE center_id = ? ORDER BY code, id",
                (rs, n) -> new CaissePubliee(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("type_caisse")), centre);
    }

    @Override
    public List<AgencePubliee> agences(UUID centre) {
        return jdbc.query("SELECT id, code, nom, caisse_id FROM agence WHERE center_id = ? ORDER BY code, id",
                (rs, n) -> new AgencePubliee(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getObject("caisse_id", UUID.class)), centre);
    }

    @Override
    public List<CentrePayeurPublie> centresPayeurs(UUID centre) {
        return jdbc.query("SELECT id, code, nom, adresse, agence_id FROM centre_payeur WHERE center_id = ? ORDER BY code, id",
                (rs, n) -> new CentrePayeurPublie(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("nom"),
                        rs.getString("adresse"), rs.getObject("agence_id", UUID.class)), centre);
    }

    @Override
    public Optional<ChaineDuPayeur> detail(UUID centre, UUID centrePayeur) {
        if (centre == null || centrePayeur == null) return Optional.empty();
        return jdbc.query(CHAINE + " AND cp.id = ?", (rs, n) -> chaine(rs), centre, centrePayeur).stream().findFirst();
    }

    @Override
    public List<ChaineDuPayeur> chaines(UUID centre) {
        return jdbc.query(CHAINE + " ORDER BY cp.code, cp.id", (rs, n) -> chaine(rs), centre);
    }

    @Override
    public java.util.Map<UUID, CaissePubliee> caissesDesAgences(java.util.Collection<UUID> agences) {
        java.util.Map<UUID, CaissePubliee> resultat = new java.util.HashMap<>();
        if (agences.isEmpty()) return resultat;
        String marques = String.join(",", java.util.Collections.nCopies(agences.size(), "?"));
        jdbc.query("SELECT ag.id AS agence_id, ca.id, ca.code, ca.nom, ca.type_caisse FROM agence ag "
                + "JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE ag.id IN (" + marques + ")", rs -> {
            resultat.put(rs.getObject("agence_id", UUID.class), new CaissePubliee(rs.getObject("id", UUID.class), rs.getString("code"),
                    rs.getString("nom"), rs.getString("type_caisse")));
        }, agences.toArray());
        return resultat;
    }

    @Override
    public boolean existe(UUID centre, UUID centrePayeur) {
        if (centre == null || centrePayeur == null) return false;
        Long nombre = jdbc.queryForObject("SELECT COUNT(*) FROM centre_payeur WHERE id = ? AND center_id = ?", Long.class,
                centrePayeur, centre);
        return nombre != null && nombre > 0;
    }

    private static ChaineDuPayeur chaine(java.sql.ResultSet rs) throws java.sql.SQLException {
        CentrePayeurPublie centrePayeur = new CentrePayeurPublie(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getString("nom"), rs.getString("adresse"), rs.getObject("agence_id", UUID.class));
        UUID agenceId = rs.getObject("ag_id", UUID.class);
        AgencePubliee agence = agenceId == null ? null : new AgencePubliee(agenceId, rs.getString("ag_code"), rs.getString("ag_nom"),
                rs.getObject("ag_caisse_id", UUID.class));
        UUID caisseId = rs.getObject("ca_id", UUID.class);
        CaissePubliee caisse = caisseId == null ? null : new CaissePubliee(caisseId, rs.getString("ca_code"), rs.getString("ca_nom"),
                rs.getString("ca_type"));
        return new ChaineDuPayeur(centrePayeur, agence, caisse);
    }
}
