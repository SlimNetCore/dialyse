package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.medical.ordonnance.port.OrdonnanceNumeroGeneratorPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compteur séquentiel par centre, préfixé {@code ORD-}, stocké dans {@code app_settings}
 * (verrouillé via {@code SELECT ... FOR UPDATE} dans la transaction appelante) — même mécanisme
 * que {@code StockSequenceAdapter}, dupliqué ici plutôt que réutilisé pour ne pas faire dépendre
 * le contexte borné Médical (bc-medical) du contexte Stock (bc-stock).
 */
@Component
public class OrdonnanceNumeroGeneratorAdapter implements OrdonnanceNumeroGeneratorPort {

    private static final String CLE = "SEQ_ORD";
    private static final String PREFIXE = "ORD-";

    private final JdbcTemplate jdbc;

    public OrdonnanceNumeroGeneratorAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public String genererNumero(CenterId centerId) {
        ensureRow(centerId);

        Long current = jdbc.queryForObject(
                "SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ? FOR UPDATE",
                Long.class, centerId.value(), CLE);
        long nextValue = (current != null ? current : 0L) + 1L;

        jdbc.update("UPDATE app_settings SET dernier_compteur = ? WHERE center_id = ? AND cle = ?",
                nextValue, centerId.value(), CLE);

        return PREFIXE + String.format("%05d", nextValue);
    }

    private void ensureRow(CenterId centerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_settings WHERE center_id = ? AND cle = ?",
                Integer.class, centerId.value(), CLE);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, 0)",
                    centerId.value(), CLE, PREFIXE);
        }
    }
}
