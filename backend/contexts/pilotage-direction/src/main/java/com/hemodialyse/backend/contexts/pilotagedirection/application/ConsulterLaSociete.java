package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;

import java.util.UUID;

/** Consulter la société de la direction et la liste de ses centres (les filtres du tableau de bord). */
public final class ConsulterLaSociete {

    private final SocietesSuivies societes;

    public ConsulterLaSociete(SocietesSuivies societes) {
        this.societes = societes;
    }

    public SocieteSuivie execute(UUID societeId) {
        return societes.societe(societeId);
    }
}
