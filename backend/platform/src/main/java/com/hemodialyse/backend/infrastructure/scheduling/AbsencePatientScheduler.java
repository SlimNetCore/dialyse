package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.absence.AbsencePatientService.Synthese;
import com.hemodialyse.backend.application.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job quotidien : détecte les séances prévues non réalisées des derniers jours (absences « à qualifier »), annule celles
 * dont la séance a été saisie après coup, puis prévient chaque centre des absences à qualifier.
 */
@Component
public class AbsencePatientScheduler {

    static final int JOURS_A_CONTROLER = 3;
    private static final Logger log = LoggerFactory.getLogger(AbsencePatientScheduler.class);

    private final AbsencePatientService service;
    private final NotificationService notificationService;

    public AbsencePatientScheduler(AbsencePatientService service, NotificationService notificationService) {
        this.service = service;
        this.notificationService = notificationService;
    }

    /**
     * Tous les jours à 02:30.
     */
    @Scheduled(cron = "0 30 2 * * *")
    public void controlerAbsences() {
        for (UUID centre : service.centres()) {
            controlerCentre(centre);
        }
    }

    void controlerCentre(UUID centre) {
        try {
            Synthese synthese = service.controlerCentre(centre, JOURS_A_CONTROLER);
            if (synthese.aQualifier() > 0) {
                notificationService.notifyAbsencesAQualifier(centre, synthese.aQualifier(), synthese.enRetard());
            }
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher le contrôle des autres
            log.warn("[ABSENCES] Contrôle impossible pour le centre {}", centre, e);
        }
    }
}
