package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.absence.AbsencePatientService;
import com.hemodialyse.backend.application.absence.AbsencePatientService.Synthese;
import com.hemodialyse.backend.application.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job quotidien : détecte les séances prévues non réalisées des derniers jours (absences « à qualifier »), annule celles
 * dont la séance a été saisie après coup, puis prévient chaque centre des absences à qualifier.
 */
@Component
public class AbsencePatientScheduler {

    /**
     * Fenêtre du contrôle quotidien : une semaine, pour absorber un arrêt de plusieurs jours du serveur.
     */
    static final int JOURS_A_CONTROLER = 7;
    /**
     * Fenêtre du rattrapage au démarrage du serveur (couvre un arrêt prolongé ou un déploiement récent).
     */
    static final int JOURS_RATTRAPAGE_DEMARRAGE = 31;
    private static final Logger log = LoggerFactory.getLogger(AbsencePatientScheduler.class);

    @Value("${app.absences.startup-catchup:true}")
    private boolean rattrapageAuDemarrage = true;

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

    /**
     * Au démarrage : rattrape les {@value #JOURS_RATTRAPAGE_DEMARRAGE} derniers jours de chaque centre, sans attendre
     * la nuit suivante (idempotent). Désactivable par {@code app.absences.startup-catchup=false}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rattraperAuDemarrage() {
        if (!rattrapageAuDemarrage) return;
        for (UUID centre : service.centres()) {
            try {
                service.controlerCentre(centre, JOURS_RATTRAPAGE_DEMARRAGE);
            } catch (RuntimeException e) {
                log.warn("[ABSENCES] Rattrapage au démarrage impossible pour le centre {}", centre, e);
            }
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
