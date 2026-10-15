package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.Journal;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant — journaux comptables paramétrés par un centre, en lecture seule. Les journaux sont tenus par le contexte
 * {@code contexts/comptabilite} (slice C1) ; la plateforme implémente ce port par une couche anti-corruption
 * ({@code infrastructure/acl/comptabilite}). Retiré avec C2 à C7.
 */
public interface JournalRepositoryPort {

    /**
     * Journaux du centre, triés par code ; les journaux par défaut tant qu'il n'a rien paramétré.
     */
    List<Journal> findByCenter(UUID centerId);
}
