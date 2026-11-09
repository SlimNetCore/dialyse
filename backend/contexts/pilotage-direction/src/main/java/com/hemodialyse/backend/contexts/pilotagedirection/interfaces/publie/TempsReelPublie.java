package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.SuivreLeTempsReel;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TempsReelDeLaDirection;

import java.util.UUID;

/** Adaptateur entrant du langage publié {@link TempsReelDeLaDirection} : délègue au suivi du temps réel. */
public final class TempsReelPublie implements TempsReelDeLaDirection {

    private final SuivreLeTempsReel tempsReel;

    public TempsReelPublie(SuivreLeTempsReel tempsReel) {
        this.tempsReel = tempsReel;
    }

    @Override
    public void abonneAjoute(UUID societeId) {
        tempsReel.abonneAjoute(societeId);
    }

    @Override
    public void abonneRetire(UUID societeId) {
        tempsReel.abonneRetire(societeId);
    }

    @Override
    public boolean estSuivie(UUID societeId) {
        return tempsReel.estSuivie(societeId);
    }

    @Override
    public void unCentreAChange(UUID centreId) {
        tempsReel.unCentreAChange(centreId);
    }
}
