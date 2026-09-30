package com.hemodialyse.backend.domain.migration.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.util.Map;

/**
 * Port Out — correspondances de valeurs saisies par le centre pour traduire les codes de l'ancien système
 * (ex. état « D » → DECEDE). Valeur d'origine stockée normalisée (majuscules, sans accents).
 */
public interface ValueMappingPort {

    /**
     * colonne → (valeur d'origine normalisée → valeur cible).
     */
    Map<String, Map<String, String>> findAll(CenterId centerId);

    void save(CenterId centerId, String column, String normalizedSource, String target);

    void delete(CenterId centerId, String column, String normalizedSource);
}

