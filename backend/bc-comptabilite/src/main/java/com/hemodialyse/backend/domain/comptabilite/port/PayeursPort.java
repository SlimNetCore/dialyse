package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.shared.PagedResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — payeurs d'un centre (référentiel tenu hors de la comptabilité, lu ici pour leur affecter un compte).
 */
public interface PayeursPort {

    /**
     * Page de payeurs triés par nom ; {@code recherche} (facultative) porte sur le code et le nom.
     */
    PagedResult<Payeur> lister(UUID centerId, String recherche, int page, int size);

    Optional<Payeur> trouver(UUID centerId, UUID payeurId);

    record Payeur(UUID id, String code, String nom) {
    }
}
