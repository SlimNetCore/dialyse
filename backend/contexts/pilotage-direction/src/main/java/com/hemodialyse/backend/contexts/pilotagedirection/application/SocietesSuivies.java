package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;

import java.util.UUID;

/** Port sortant : la société d'une direction et la liste de ses centres, triés par nom (lecture seule). */
public interface SocietesSuivies {

    /** @throws SocieteInconnue si la société n'existe pas (le jeton a été revérifié par l'appelant : cas anormal) */
    SocieteSuivie societe(UUID societeId);

    class SocieteInconnue extends RuntimeException {
        public SocieteInconnue(UUID societeId) {
            super("Société inconnue : " + societeId);
        }
    }
}
