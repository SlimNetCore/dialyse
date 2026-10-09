package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.UUID;

/**
 * Port entrant — compte client de chaque payeur. Ajouter un client ne demande aucun développement : on crée le payeur
 * dans le référentiel, puis on lui affecte ici son compte.
 */
public interface ComptesPayeursUseCase {

    PagedResult<PayeurCompte> lister(UUID centerId, String recherche, int page, int size);

    /**
     * Affecte un compte client au payeur ; un compte vide lui rend le compte client par défaut du centre.
     */
    PayeurCompte definir(UUID centerId, UUID payeurId, String compte);

    /**
     * @param compte compte client propre au payeur, ou {@code null} : le compte client par défaut s'applique
     */
    record PayeurCompte(UUID payeurId, String code, String nom, String compte) {
    }
}
