package com.hemodialyse.backend.infrastructure.scheduling;

import com.hemodialyse.backend.application.patient.LiberationPlacesService;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job quotidien : libère la place des patients transférés, décédés, greffés, guéris ou dont le séjour est terminé. Il
 * s'exécute après le contrôle des absences (02:30), qui a encore besoin des jours de dialyse de la veille.
 */
@Component
public class LiberationPlacesScheduler {

    private static final Logger log = LoggerFactory.getLogger(LiberationPlacesScheduler.class);

    private final LiberationPlacesService service;

    public LiberationPlacesScheduler(LiberationPlacesService service) {
        this.service = service;
    }

    /**
     * Tous les jours à 04:00.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void libererPlaces() {
        for (CenterId centre : service.centres()) {
            libererCentre(centre);
        }
    }

    void libererCentre(CenterId centre) {
        try {
            int liberees = service.libererCentre(centre);
            if (liberees > 0) log.info("[PLACES] {} place(s) libérée(s) pour le centre {}", liberees, centre.value());
        } catch (RuntimeException e) {
            // un centre en erreur ne doit pas empêcher le traitement des autres
            log.warn("[PLACES] Libération impossible pour le centre {}", centre.value(), e);
        }
    }
}
