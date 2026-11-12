package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.persistence;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Instantanés mensuels figés sur {@code direction_snapshot} (V1 : {@code societe_id}, {@code mois}, {@code payload}, {@code generated_at},
 * unicité {@code uq_direction_snapshot}). Propriétaire de la table avec {@code direction_alert_history} (PD3, hors lot). SQL repris MOT POUR
 * MOT de {@code DirectionSnapshotService} (retiré, {@code :61}, {@code :68}, {@code :125-126}) ; l'horodatage est celui de la base
 * ({@code CURRENT_TIMESTAMP}, KB-DIR-06 c). Un doublon refusé par la contrainte d'unicité est AVALÉ : c'est l'idempotence (créé entre-temps
 * par une autre demande), l'instantané reste immuable.
 */
public final class InstantanesMensuelsJdbc implements InstantanesMensuels {

    private final JdbcTemplate jdbc;

    public InstantanesMensuelsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<InstantaneListe> lister(UUID societeId) {
        return jdbc.query("SELECT mois, generated_at FROM direction_snapshot WHERE societe_id = ? ORDER BY mois DESC",
                (rs, i) -> new InstantaneListe(rs.getString("mois"), rs.getObject("generated_at", OffsetDateTime.class)),
                societeId);
    }

    @Override
    public Optional<InstantaneFige> trouver(UUID societeId, MoisDInstantane mois) {
        return jdbc.query("SELECT generated_at, payload FROM direction_snapshot WHERE societe_id = ? AND mois = ?",
                (rs, i) -> new InstantaneFige(mois.texte(), rs.getObject("generated_at", OffsetDateTime.class), rs.getString("payload")),
                societeId, mois.texte()).stream().findFirst();
    }

    @Override
    public boolean enregistrer(InstantaneMensuel instantane) {
        try {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) "
                            + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), instantane.societeId(), instantane.mois().texte(), instantane.contenu().texte());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** PL10a : l'instant de capture vient de l'horloge du Pilotage (même instant que l'ancien {@code CURRENT_TIMESTAMP}, mais testable). */
    @Override
    public boolean enregistrer(InstantaneMensuel instantane, OffsetDateTime genereLe) {
        try {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), instantane.societeId(), instantane.mois().texte(), instantane.contenu().texte(), genereLe);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
