package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.Changement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : diffuse à la direction d'une société les changements de son tableau de bord (uniquement des indicateurs agrégés et
 * anonymes, jamais de donnée nominative ; le client relit ensuite les données par l'API sécurisée). Le transport (STOMP) n'est pas connu.
 */
public interface DiffusionDesChangements {

    void diffuser(UUID societeId, Instant a, List<Changement> changements);
}
