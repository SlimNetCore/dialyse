package com.hemodialyse.backend.contexts.planification.interfaces.jobs;

import com.hemodialyse.backend.contexts.planification.application.ControlerLaPresence;
import com.hemodialyse.backend.contexts.planification.application.ControlerLaPresence.ResultatDuCentre;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

/**
 * Adapter entrant planifié (slice P4) : un seul déclencheur serveur à 07:15, fuseau {@code app.planification.fuseau-par-defaut}
 * (Africa/Algiers) ; le cas d'usage calcule « aujourd'hui » au fuseau de chaque centre (KB-PLN-44). Les incidents ne sont que
 * journalisés : un contrôle ou un centre en échec n'arrête pas le reste, pas même l'échec de la liste des centres. Limite : un seul
 * déclencheur à 07:15 Africa/Algiers sert tous les centres ; un centre d'un autre fuseau est contrôlé à une autre heure locale (son
 * « jour » reste celui de son fuseau). Un cron horaire filtré sur l'heure locale ne serait utile qu'avec un vrai multi-fuseau.
 */
public class ControleDeLaPresenceDe0715 {
    private static final Logger log = LoggerFactory.getLogger(ControleDeLaPresenceDe0715.class);

    private final ControlerLaPresence controler;

    public ControleDeLaPresenceDe0715(ControlerLaPresence controler) {
        this.controler = controler;
    }

    /** Tous les jours à 07:15. */
    @Scheduled(cron = "0 15 7 * * *", zone = "${app.planification.fuseau-par-defaut:Africa/Algiers}")
    public void controlerLaPresence() {
        journaliser(controler.controler());
    }

    static void journaliser(ControlerLaPresence.Bilan bilan) {
        if (bilan.incident() != null) {
            log.error("[PRESENCE] Aucun centre contrôlé : {}", bilan.incident());
        }
        journaliser(bilan.centres());
    }

    private static void journaliser(List<ResultatDuCentre> resultats) {
        for (ResultatDuCentre r : resultats) {
            r.incidents().forEach(incident -> log.warn("[PRESENCE] Contrôle impossible pour le centre {} : {}", r.centre().valeur(), incident));
            if (r.sousEffectif() > 0 || r.sureffectif() > 0) {
                log.info("[PRESENCE] Centre {} ({}) : {} case(s) en sous-effectif (alerte {}), {} en sur-effectif (alerte {})",
                        r.centre().valeur(), r.jour(), r.sousEffectif(), r.sousEffectifNotifie() ? "émise" : "déjà émise ce jour",
                        r.sureffectif(), r.sureffectifNotifie() ? "émise" : "déjà émise ce jour");
            }
        }
    }
}
