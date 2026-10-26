package com.hemodialyse.backend.domain.infirmier.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Lecture des comptes utilisateurs liés aux fiches d'infirmiers, pour « Mon planning » (slice P3). La création, la liaison
 * et l'activation des comptes sont servies par le contexte Planification (slice P2) ; ce port disparaît avec P3.
 */
public interface ComptesInfirmierPort {

    /**
     * Comptes demandés (résumés), sans contrôle de centre : sert à afficher les comptes déjà reliés à une page de
     * fiches du centre.
     */
    List<CompteRef> trouverParIds(Collection<UUID> userIds);

    /**
     * Compte utilisateur résumé.
     */
    record CompteRef(UUID id, String username, String nomComplet, boolean actif) {
    }
}
