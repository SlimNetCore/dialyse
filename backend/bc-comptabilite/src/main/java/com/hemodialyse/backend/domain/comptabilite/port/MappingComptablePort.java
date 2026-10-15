package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;

import java.util.UUID;

/**
 * Port sortant — lecture du mapping comptable configurable par centre. Le mapping est tenu par le contexte
 * {@code contexts/comptabilite} (slice C2) ; la génération des écritures encore ici le lit par son langage publié (ACL
 * {@code infrastructure/acl/comptabilite}, retirée avec C5 et C6).
 */
public interface MappingComptablePort {

    /**
     * Retourne le mapping du centre, ou le mapping par défaut s'il n'est pas personnalisé.
     */
    MappingComptable findByCenterId(UUID centerId);
}
