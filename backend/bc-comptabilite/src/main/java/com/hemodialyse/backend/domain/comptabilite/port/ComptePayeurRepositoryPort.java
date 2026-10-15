package com.hemodialyse.backend.domain.comptabilite.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant — compte client propre à un payeur d'un centre (caisse, mutuelle, entreprise…), en lecture seule. Il est
 * tenu par le contexte {@code contexts/comptabilite} (slice C1) ; la plateforme implémente ce port par une couche
 * anti-corruption ({@code infrastructure/acl/comptabilite}). Retiré avec C2 à C7.
 */
public interface ComptePayeurRepositoryPort {

    Optional<String> find(UUID centerId, UUID payeurId);
}
