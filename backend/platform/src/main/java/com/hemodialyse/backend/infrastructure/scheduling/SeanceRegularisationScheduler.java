package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.application.seance.SeanceRegularisationService;
import com.hemodialyse.backend.application.seance.SeanceRegularisationService.Resume;
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
 * Job quotidien : rappelle à l'administrateur de chaque centre les séances des derniers jours restées « créées »
 * (validation oubliée) à régulariser. Il passe après la détection des absences de 02:30 : sans régularisation, ces
 * patients sont comptés absents.
 */
@Component
public class SeanceRegularisationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeanceRegularisationScheduler.class);

    private final JdbcTemplate jdbc;
    private final SeanceRegularisationService regularisation;
    private final NotificationService notificationService;

    public SeanceRegularisationScheduler(JdbcTemplate jdbc, SeanceRegularisationService regularisation,
                                         NotificationService notificationService) {
        this.jdbc = jdbc;
        this.regularisation = regularisation;
        this.notificationService = notificationService;
    }

    /**
     * Tous les jours à 07:00.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void rappelerRegularisations() {
        List<UUID> centres = jdbc.query("SELECT id FROM centers", (rs, i) -> rs.getObject(1, UUID.class));
        for (UUID centre : centres) {
            rappelerCentre(centre, LocalDate.now(ZoneOffset.UTC));
        }
    }

    void rappelerCentre(UUID centre, LocalDate aujourdhui) {
        try {
            Resume resume = regularisation.aRegulariser(centre, aujourdhui);
            if (resume.total() > 0) {
                notificationService.notifySeancesARegulariser(centre, resume.total(), resume.plusAncienne());
            }
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher le rappel des autres
            log.warn("[SEANCES] Rappel de régularisation impossible pour le centre {}", centre, e);
        }
    }
}
