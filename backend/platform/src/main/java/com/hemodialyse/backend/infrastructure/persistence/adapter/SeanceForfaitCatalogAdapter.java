package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.port.SeanceForfaitCatalogPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SeanceForfaitCatalogAdapter implements SeanceForfaitCatalogPort {

    private final JdbcTemplate jdbc;

    public SeanceForfaitCatalogAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SeanceForfaitSnapshot> findById(CenterId centerId, UUID forfaitId) {
        return jdbc.query(
                """
                        SELECT id, code, libelle, prix
                        FROM forfait
                        WHERE center_id = ?
                          AND id = ?
                        """,
                rs -> rs.next()
                        ? Optional.of(new SeanceForfaitSnapshot(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("libelle"),
                        rs.getBigDecimal("prix")
                ))
                        : Optional.empty(),
                centerId.value(),
                forfaitId
        );
    }
}

