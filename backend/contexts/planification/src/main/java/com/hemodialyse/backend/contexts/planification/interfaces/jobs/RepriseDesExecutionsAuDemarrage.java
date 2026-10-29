package com.hemodialyse.backend.contexts.planification.interfaces.jobs;

import com.hemodialyse.backend.contexts.planification.application.ReprendreLesExecutionsInterrompues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Adapter entrant de démarrage (slice P8) : au démarrage du serveur aucun calcul d'optimisation ne peut être en cours ; ceux restés
 * « en cours » (arrêt du serveur pendant le calcul) échouent pour ne pas bloquer le lancement d'un nouveau. Une base indisponible au
 * démarrage est journalisée sans empêcher le démarrage.
 */
public class RepriseDesExecutionsAuDemarrage {

    private static final Logger log = LoggerFactory.getLogger(RepriseDesExecutionsAuDemarrage.class);

    private final ReprendreLesExecutionsInterrompues reprendre;

    public RepriseDesExecutionsAuDemarrage(ReprendreLesExecutionsInterrompues reprendre) {
        this.reprendre = reprendre;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void interrompreLesCalculsOrphelins() {
        battreEtReprendre();
    }

    /**
     * Toutes les 10 s : signe de vie de cette instance, puis reprise des orphelins (jamais ceux d'une instance vivante).
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void battreEtReprendre() {
        try {
            int interrompues = reprendre.executer();
            if (interrompues > 0) log.warn("[OPTIMISATION] {} calcul(s) orphelin(s) marqué(s) en échec", interrompues);
        } catch (RuntimeException e) {
            log.warn("[OPTIMISATION] Reprise des calculs orphelins impossible", e);
        }
    }
}
