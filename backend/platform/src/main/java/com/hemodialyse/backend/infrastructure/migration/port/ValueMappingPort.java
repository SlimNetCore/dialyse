package com.hemodialyse.backend.infrastructure.migration.port;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Map;

/**
 * Port Out — correspondances de valeurs saisies par le centre pour traduire les codes de l'ancien système
 * (ex. état « D » → DECEDE). Valeur d'origine stockée normalisée (majuscules, sans accents).
 */
public interface ValueMappingPort {

    /**
     * colonne → (valeur d'origine normalisée → valeur cible).
     */
    Map<String, Map<String, String>> findAll(CentreId centerId);

    void save(CentreId centerId, String column, String normalizedSource, String target);

    void delete(CentreId centerId, String column, String normalizedSource);
}

