package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.contexts.stock.publishedlanguage.PeremptionsDuStock;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PeremptionsDuStock.LotProcheDePeremption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Contrôle quotidien des péremptions à J-30 : pour chaque centre, les lots encore en stock qui périment dans les 30 jours (lus
 * dans le langage publié du Stock, {@link PeremptionsDuStock}) sont signalés au journal technique. Point d'accroche de la
 * notification prévue par la slice S8 (pharmacien, admin ; centres actifs seulement).
 */
@Component
public class ExpirationAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationAlertScheduler.class);
    private static final int WINDOW_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final PeremptionsDuStock peremptions;

    public ExpirationAlertScheduler(JdbcTemplate jdbc, PeremptionsDuStock peremptions) {
        this.jdbc = jdbc;
        this.peremptions = peremptions;
    }

    /**
     * Every day at 07:00.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void scanExpirations() {
        LocalDate threshold = LocalDate.now(ZoneOffset.UTC).plusDays(WINDOW_DAYS);
        List<UUID> centers = jdbc.query("SELECT id FROM centers", (rs, i) -> UUID.fromString(rs.getString(1)));

        for (UUID centerId : centers) {
            List<LotProcheDePeremption> expiring = peremptions.lotsPerimantAuPlusTard(centerId, threshold);
            if (!expiring.isEmpty()) {
                log.warn("[STOCK][J-{}] Centre {} : {} lot(s) en peremption proche", WINDOW_DAYS, centerId, expiring.size());
                for (LotProcheDePeremption lot : expiring) {
                    log.warn("  -> Lot {} (article {}) peremption {} - reste {}",
                            lot.numeroLot(), lot.articleId(), lot.datePeremption(), lot.quantiteRestante());
                }
            }
        }
    }
}
