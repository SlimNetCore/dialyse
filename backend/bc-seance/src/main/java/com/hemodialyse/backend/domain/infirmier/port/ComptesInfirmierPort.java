package com.hemodialyse.backend.domain.infirmier.port;

import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port vers les comptes utilisateurs de l'application, vus depuis le personnel soignant. Un compte « infirmier » est
 * un compte actif ou non, rattaché au centre et doté du rôle INFIRMIER.
 */
public interface ComptesInfirmierPort {

    /**
     * Compte rattaché au centre avec le rôle INFIRMIER, s'il existe.
     */
    Optional<CompteRef> trouverCompteInfirmier(UUID centerId, UUID userId);

    /**
     * Comptes demandés (résumés), sans contrôle de centre : sert à afficher les comptes déjà reliés à une page de
     * fiches du centre.
     */
    List<CompteRef> trouverParIds(Collection<UUID> userIds);

    /**
     * Comptes INFIRMIER du centre qu'aucune fiche du centre n'utilise encore, paginés par identifiant de connexion.
     */
    PagedResult<CompteRef> comptesLiables(UUID centerId, int page, int size);

    boolean identifiantExiste(String username);

    /**
     * Crée un compte actif rattaché au centre avec le rôle INFIRMIER ; le mot de passe est haché par l'adaptateur.
     */
    CompteRef creerCompteInfirmier(UUID centerId, String username, String motDePasse, String nomComplet, String email);

    void definirActif(UUID userId, boolean actif);

    /**
     * Compte utilisateur résumé.
     */
    record CompteRef(UUID id, String username, String nomComplet, boolean actif) {
    }
}
