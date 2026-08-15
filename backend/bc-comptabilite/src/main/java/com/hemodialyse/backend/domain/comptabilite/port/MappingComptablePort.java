package com.hemodialyse.backend.domain.comptabilite.port;

import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;

import java.util.UUID;

/**
 * Port sortant — lecture du mapping comptable configurable par centre.
 */
public interface MappingComptablePort {

    /**
     * Retourne le mapping du centre, ou le mapping par défaut s'il n'est pas personnalisé.
     */
    MappingComptable findByCenterId(UUID centerId);

    void save(MappingComptable mapping);
}

