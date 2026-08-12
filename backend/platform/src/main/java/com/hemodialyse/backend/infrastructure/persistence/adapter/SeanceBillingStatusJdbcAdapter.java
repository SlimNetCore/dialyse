package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.SeanceBillingStatusPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SeanceBillingStatusJdbcAdapter implements SeanceBillingStatusPort {

    private final JdbcTemplate jdbc;

    public SeanceBillingStatusJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isBilled(CenterId centerId, UUID seanceId) {
        String statut = jdbc.query(
                "SELECT statut FROM seances WHERE id = ? AND center_id = ?",
                rs -> rs.next() ? rs.getString("statut") : null,
                seanceId,
                centerId.value()
        );
        return "FACTUREE".equalsIgnoreCase(statut);
    }
}

