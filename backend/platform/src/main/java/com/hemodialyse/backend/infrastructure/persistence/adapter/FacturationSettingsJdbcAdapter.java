package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.port.FacturationSettingsRepositoryPort;
import com.hemodialyse.backend.domain.facturation.valueobject.ParametresFacturation;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

@Component
public class FacturationSettingsJdbcAdapter implements FacturationSettingsRepositoryPort {

    private final JdbcTemplate jdbc;

    public FacturationSettingsJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ParametresFacturation findByCenterId(CenterId centerId) {
        var rows = jdbc.query(
                "SELECT tva_rate, code_format, regroupement_multi_forfait, updated_at FROM facturation_settings WHERE center_id = ?",
                (rs, rowNum) -> new ParametresFacturation(
                        rs.getBigDecimal("tva_rate"),
                        rs.getString("code_format"),
                        rs.getBoolean("regroupement_multi_forfait"),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                centerId.value()
        );
        if (!rows.isEmpty()) {
            return rows.getFirst();
        }
        ParametresFacturation defaults = ParametresFacturation.defaults();
        return save(centerId, "system", defaults);
    }

    @Override
    public ParametresFacturation save(CenterId centerId, String userId, ParametresFacturation settings) {
        jdbc.update(
                """
                        MERGE INTO facturation_settings (center_id, tva_rate, code_format, regroupement_multi_forfait, updated_at, updated_by)
                        KEY (center_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                centerId.value(),
                settings.tvaRate(),
                settings.codeFormat(),
                settings.regroupementMultiForfait(),
                Timestamp.from(OffsetDateTime.now().toInstant()),
                userId == null || userId.isBlank() ? "system" : userId
        );
        return findByCenterId(centerId);
    }
}

