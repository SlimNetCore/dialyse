package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HistoriqueDesAlertes;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteOuverte;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CleDAlerte;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Historique des alertes sur {@code direction_alert_history} (V1 : {@code societe_id}, {@code center_id}, {@code centre_nom}, {@code code},
 * {@code severity}, {@code valeur}, {@code first_seen_at}, {@code resolved_at}). PROPRIÉTAIRE de la table (PD3, ADR 0104). SQL repris MOT POUR
 * MOT de {@code DirectionAlertHistoryService} (retiré). Aucune contrainte d'unicité sur (centre, code) ouvert : deux écrivains concurrents
 * peuvent ouvrir deux lignes (course reproduite, résiduel PD3-R2).
 */
public final class HistoriqueDesAlertesJdbc implements HistoriqueDesAlertes {

    private final JdbcTemplate jdbc;

    public HistoriqueDesAlertesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AlerteOuverte> ouvertes(UUID societeId) {
        return jdbc.query("SELECT id, center_id, code FROM direction_alert_history "
                        + "WHERE societe_id = ? AND resolved_at IS NULL",
                (rs, i) -> new AlerteOuverte(rs.getObject("id", UUID.class),
                        new CleDAlerte(rs.getObject("center_id", UUID.class), rs.getString("code"))),
                societeId);
    }

    @Override
    public void ouvrir(UUID societeId, AlerteLevee a, OffsetDateTime vueLe) {
        jdbc.update("INSERT INTO direction_alert_history "
                        + "(id, societe_id, center_id, centre_nom, code, severity, valeur, first_seen_at) "
                        + "VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), societeId, a.centreId(), a.centre(), a.code(), a.gravite().name(), a.valeur(),
                Timestamp.from(vueLe.toInstant()));
    }

    @Override
    public void resoudre(UUID alerteId, OffsetDateTime resolueLe) {
        jdbc.update("UPDATE direction_alert_history SET resolved_at = ? WHERE id = ?", Timestamp.from(resolueLe.toInstant()), alerteId);
    }

    @Override
    public List<Entree> recentes(UUID societeId, int limite) {
        return jdbc.query("SELECT center_id, centre_nom, code, severity, valeur, first_seen_at, resolved_at "
                        + "FROM direction_alert_history WHERE societe_id = ? "
                        + "ORDER BY COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC LIMIT ?",
                (rs, i) -> new Entree(rs.getObject("center_id", UUID.class), rs.getString("centre_nom"),
                        rs.getString("code"), rs.getString("severity"), rs.getBigDecimal("valeur"),
                        rs.getObject("first_seen_at", OffsetDateTime.class),
                        rs.getObject("resolved_at", OffsetDateTime.class)),
                societeId, limite);
    }
}
