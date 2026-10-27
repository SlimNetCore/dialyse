package com.hemodialyse.backend.contexts.planification.interfaces.jobs;

import com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues;
import com.hemodialyse.backend.contexts.planification.application.LibererLesPlacesEchues.ResultatDuCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Adapter entrant planifié (slice P7) : un seul déclencheur serveur à 04:00, fuseau {@code app.planification.fuseau-par-defaut}
 * (Africa/Algiers), donc APRÈS la détection des absences de 02:30 (même fuseau) qui a encore besoin des jours de dialyse de la veille
 * (RG-PLN-083). Le jour de libération est celui de chaque centre. Les incidents sont journalisés (et signalés à l'administrateur par
 * le cas d'usage) : un patient ou un centre en échec n'arrête pas le reste.
 */
public class LiberationDesPlacesDe0400 {
    private static final Logger log = LoggerFactory.getLogger(LiberationDesPlacesDe0400.class);

    private final LibererLesPlacesEchues liberer;

    public LiberationDesPlacesDe0400(LibererLesPlacesEchues liberer) {
        this.liberer = liberer;
    }

    /**
     * Tous les jours à 04:00.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "${app.planification.fuseau-par-defaut:Africa/Algiers}")
    public void libererLesPlaces() {
        LibererLesPlacesEchues.Bilan bilan = liberer.liberer();
        if (bilan.incident() != null) {
            log.error("[PLACES] Aucun centre traité : {}", bilan.incident());
        }
        for (ResultatDuCentre r : bilan.centres()) {
            r.incidents().forEach(incident -> log.warn("[PLACES] Centre {} : {}", r.centre().valeur(), incident));
            if (r.liberees() > 0) {
                log.info("[PLACES] {} place(s) libérée(s) pour le centre {} ({})", r.liberees(), r.centre().valeur(), r.jour());
            }
        }
    }
}
