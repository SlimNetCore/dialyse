package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Concurrency-safe, per-center prefixed sequence generator backed by app_settings.
 * The counter row is locked (SELECT ... FOR UPDATE) inside the surrounding transaction.
 */
@Component
public class StockSequenceAdapter implements StockSequencePort {

    private static final Map<String, String> DEFAULT_PREFIXES = Map.of(
            "SEQ_BL", "BL-",
            "SEQ_BR", "BR-",
            "SEQ_BS", "BS-"
    );

    private final JdbcTemplate jdbc;

    public StockSequenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public String next(CenterId centerId, String cle) {
        String prefixe = ensureRow(centerId, cle);

        Long current = jdbc.queryForObject(
                "SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ? FOR UPDATE",
                Long.class, centerId.value(), cle);
        long nextValue = (current != null ? current : 0L) + 1L;

        jdbc.update("UPDATE app_settings SET dernier_compteur = ? WHERE center_id = ? AND cle = ?",
                nextValue, centerId.value(), cle);

        return prefixe + String.format("%05d", nextValue);
    }

    private String ensureRow(CenterId centerId, String cle) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_settings WHERE center_id = ? AND cle = ?",
                Integer.class, centerId.value(), cle);
        String prefixe = DEFAULT_PREFIXES.getOrDefault(cle, cle + "-");
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, 0)",
                    centerId.value(), cle, prefixe);
            return prefixe;
        }
        String existing = jdbc.queryForObject(
                "SELECT prefixe FROM app_settings WHERE center_id = ? AND cle = ?",
                String.class, centerId.value(), cle);
        return existing != null ? existing : prefixe;
    }
}

