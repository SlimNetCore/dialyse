package com.hemodialyse.backend.domain.comptabilite.port;

import java.util.Collection;
import java.util.UUID;

/**
 * Port sortant — plan comptable d'un centre, en lecture seule. Le plan est tenu par le contexte
 * {@code contexts/comptabilite} (slice C1) ; la plateforme implémente ce port par une couche anti-corruption
 * ({@code infrastructure/acl/comptabilite}) qui interroge son langage publié. Retiré avec C2 à C7.
 */
public interface PlanComptableUseCase {

    /**
     * Refuse ({@code COMPTE_INCONNU}) tout numéro qui n'est pas un compte actif du plan du centre.
     */
    void exigerActifs(UUID centerId, Collection<String> numeros);
}
