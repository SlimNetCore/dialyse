package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.port.FacturationSettingsRepositoryPort;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * Lecture des paramètres de facturation par la simulation et la validation legacy (jusqu'aux slices F2 et F3). Les écrans
 * passent par le contexte {@code facturation} (slice F1) ; mêmes corrections ici : la lecture d'un centre sans paramètres
 * rend les valeurs par défaut sans rien écrire (KB-FAC-08) et l'écriture est portable H2/PostgreSQL (KB-FAC-07 : l'ancien
 * {@code MERGE … KEY} n'existe que sur H2).
 */
@Component
public class FacturationSettingsJdbcAdapter implements FacturationSettingsRepositoryPort {

    private final JdbcTemplate jdbc;

    public FacturationSettingsJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ParametresFacturation findByCenterId(CenterId centerId) {
        var rows = jdbc.query(
                "SELECT code_format, regroupement_multi_forfait, updated_at FROM facturation_settings WHERE center_id = ?",
                (rs, rowNum) -> new ParametresFacturation(
                        rs.getString("code_format"),
                        rs.getBoolean("regroupement_multi_forfait"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                centerId.value()
        );
        return rows.isEmpty() ? ParametresFacturation.defaults() : rows.getFirst();
    }

    @Override
    public ParametresFacturation save(CenterId centerId, String userId, ParametresFacturation settings) {
        Timestamp now = Timestamp.from(OffsetDateTime.now().toInstant());
        String author = userId == null || userId.isBlank() ? "system" : userId;
        int updated = jdbc.update(
                "UPDATE facturation_settings SET code_format = ?, regroupement_multi_forfait = ?, updated_at = ?, updated_by = ? "
                        + "WHERE center_id = ?",
                settings.codeFormat(), settings.regroupementMultiForfait(), now, author, centerId.value());
        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    centerId.value(), settings.codeFormat(), settings.regroupementMultiForfait(), now, author);
        }
        return findByCenterId(centerId);
    }
}
