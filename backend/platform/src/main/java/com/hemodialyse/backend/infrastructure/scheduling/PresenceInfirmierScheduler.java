package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
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
 * Job quotidien : prévient l'administration et le secrétariat de chaque centre dont le planning de présence des
 * infirmiers compte des créneaux en sous-effectif dans les jours à venir, pour organiser les remplacements à l'avance.
 */
@Component
public class PresenceInfirmierScheduler {

    static final int HORIZON_JOURS = 14;
    private static final Logger log = LoggerFactory.getLogger(PresenceInfirmierScheduler.class);
    private final JdbcTemplate jdbc;
    private final PresenceInfirmierQueryService presence;
    private final NotificationService notificationService;

    public PresenceInfirmierScheduler(JdbcTemplate jdbc, PresenceInfirmierQueryService presence,
                                      NotificationService notificationService) {
        this.jdbc = jdbc;
        this.presence = presence;
        this.notificationService = notificationService;
    }

    /**
     * Tous les jours à 07:15.
     */
    @Scheduled(cron = "0 15 7 * * *")
    public void controlerPresence() {
        List<UUID> centres = jdbc.query("SELECT DISTINCT center_id FROM infirmier WHERE actif = TRUE",
                (rs, i) -> rs.getObject(1, UUID.class));
        for (UUID centre : centres) {
            controlerCentre(centre, LocalDate.now(ZoneOffset.UTC));
        }
    }

    void controlerCentre(UUID centre, LocalDate aujourdhui) {
        try {
            List<AlertePresence> alertes = presence.alertes(centre, aujourdhui, HORIZON_JOURS);
            if (!alertes.isEmpty()) {
                notificationService.notifyPresenceSousEffectif(centre, alertes.size(), alertes.get(0).date());
            }
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher le contrôle des autres
            log.warn("[PRESENCE] Contrôle impossible pour le centre {}", centre, e);
        }
    }
}
