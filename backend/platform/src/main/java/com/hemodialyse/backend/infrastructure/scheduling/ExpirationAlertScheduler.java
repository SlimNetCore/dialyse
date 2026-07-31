package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily J-30 expiry alert job. Scans, per center, lots expiring within 30 days
 * that still hold quantity, and logs a consolidated warning. This is the hook
 * where notifications (email / in-app) can later be dispatched.
 */
@Component
public class ExpirationAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationAlertScheduler.class);
    private static final int WINDOW_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final LotRepositoryPort lotRepo;

    public ExpirationAlertScheduler(JdbcTemplate jdbc, LotRepositoryPort lotRepo) {
        this.jdbc = jdbc;
        this.lotRepo = lotRepo;
    }

    /**
     * Every day at 07:00.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void scanExpirations() {
        LocalDate threshold = LocalDate.now().plusDays(WINDOW_DAYS);
        List<UUID> centers = jdbc.query("SELECT id FROM centers",
                (rs, i) -> UUID.fromString(rs.getString(1)));

        for (UUID centerId : centers) {
            List<Lot> expiring = lotRepo.findExpiringBefore(CenterId.of(centerId), threshold);
            if (!expiring.isEmpty()) {
                log.warn("[STOCK][J-{}] Centre {} : {} lot(s) en peremption proche", WINDOW_DAYS, centerId, expiring.size());
                for (Lot lot : expiring) {
                    log.warn("  -> Lot {} (article {}) peremption {} - reste {}",
                            lot.getNumeroLot(), lot.getArticleId(), lot.getDatePeremption(), lot.getQuantiteRestante());
                }
            }
        }
    }
}

