package com.hemodialyse.backend.contexts.pilotagedirection.interfaces.publie;

import com.hemodialyse.backend.contexts.pilotagedirection.application.HabilitationDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.AccesALaDirection;

import java.util.UUID;

/** Adapter entrant du langage publié {@link AccesALaDirection} : délègue à l'habilitation de la direction. */
public final class AccesPublie implements AccesALaDirection {

    private final HabilitationDeLaDirection habilitation;

    public AccesPublie(HabilitationDeLaDirection habilitation) {
        this.habilitation = habilitation;
    }

    @Override
    public boolean estHabilite(UUID utilisateur, UUID societe) {
        return habilitation.estHabilite(utilisateur, societe);
    }
}
