package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.infirmier.PresenceInfirmierQueryService;
import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlertePresence;
import com.hemodialyse.backend.domain.infirmier.model.Presence.AlerteSureffectif;
import com.hemodialyse.backend.domain.planning.optimisation.port.ReglagesOptimisationPort;
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
 * infirmiers compte des créneaux en sous-effectif dans les jours à venir, pour organiser les remplacements à l'avance ;
 * prévient l'administrateur des créneaux en sur-effectif (personnel payé sans activité utile), avec les heures concernées.
 */
@Component
public class PresenceInfirmierScheduler {

    static final int HORIZON_JOURS = 14;
    private static final Logger log = LoggerFactory.getLogger(PresenceInfirmierScheduler.class);
    private final JdbcTemplate jdbc;
    private final PresenceInfirmierQueryService presence;
    private final NotificationService notificationService;
    private final ReglagesOptimisationPort reglages;

    public PresenceInfirmierScheduler(JdbcTemplate jdbc, PresenceInfirmierQueryService presence,
                                      NotificationService notificationService, ReglagesOptimisationPort reglages) {
        this.jdbc = jdbc;
        this.presence = presence;
        this.notificationService = notificationService;
        this.reglages = reglages;
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
        // les deux contrôles sont indépendants : l'échec de l'un n'empêche ni l'autre ni les autres centres
        try {
            List<AlertePresence> alertes = presence.alertes(centre, aujourdhui, HORIZON_JOURS);
            if (!alertes.isEmpty()) {
                notificationService.notifyPresenceSousEffectif(centre, alertes.size(), alertes.get(0).date());
            }
        } catch (RuntimeException e) {
            log.warn("[PRESENCE] Contrôle du sous-effectif impossible pour le centre {}", centre, e);
        }
        try {
            List<AlerteSureffectif> surplus = presence.alertesSureffectif(centre, aujourdhui, HORIZON_JOURS);
            if (!surplus.isEmpty()) {
                int vacations = surplus.stream().mapToInt(AlerteSureffectif::surplus).sum();
                int heures = vacations * reglages.lire(centre).heuresParVacation();
                notificationService.notifyPresenceSureffectif(centre, surplus.size(), vacations, heures,
                        surplus.get(0).date());
            }
        } catch (RuntimeException e) {
            log.warn("[PRESENCE] Contrôle du sur-effectif impossible pour le centre {}", centre, e);
        }
    }
}
