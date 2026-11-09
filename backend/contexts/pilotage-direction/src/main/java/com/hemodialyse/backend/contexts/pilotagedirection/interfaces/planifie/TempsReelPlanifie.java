package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.planifie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SuivreLeTempsReel;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Cadence du temps réel de la direction : recalcul rapide des sociétés signalées (chaque seconde) et balayage de sécurité de toutes les
 * sociétés suivies (toutes les 10 secondes, après 10 secondes de délai initial), pour les modifications qui n'émettent aucun signal. Sans
 * direction connectée, ces tâches ne font rien. Mêmes cadences que l'ancien {@code DirectionRealtimeScheduler} (retiré).
 */
public final class TempsReelPlanifie {

    /** Les délais sont des constantes pour que leur test les lise (le câblage n'est pas couvert par le filet). */
    public static final long SIGNALEMENTS_TOUTES_LES_MS = 1000;
    public static final long BALAYAGE_TOUTES_LES_MS = 10000;
    public static final long BALAYAGE_DELAI_INITIAL_MS = 10000;

    private final SuivreLeTempsReel tempsReel;

    public TempsReelPlanifie(SuivreLeTempsReel tempsReel) {
        this.tempsReel = tempsReel;
    }

    @Scheduled(fixedDelay = SIGNALEMENTS_TOUTES_LES_MS)
    public void traiterLesSignalements() {
        tempsReel.traiterLesSignalements();
    }

    @Scheduled(fixedDelay = BALAYAGE_TOUTES_LES_MS, initialDelay = BALAYAGE_DELAI_INITIAL_MS)
    public void balayer() {
        tempsReel.balayer();
    }
}
