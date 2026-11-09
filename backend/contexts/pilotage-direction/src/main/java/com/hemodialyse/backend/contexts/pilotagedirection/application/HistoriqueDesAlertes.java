package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteLevee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteOuverte;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant : l'historique des alertes d'une société ({@code direction_alert_history}). Chaque opération est indépendante (pas de
 * transaction englobante, comme avant : une alerte ouverte ou résolue l'est même si la suivante échoue).
 */
public interface HistoriqueDesAlertes {

    /** Les lignes non résolues de la société. */
    List<AlerteOuverte> ouvertes(UUID societeId);

    /** Ouvre une ligne : le nom du centre, la gravité et la valeur de {@code alerte} sont figés à cet instant. */
    void ouvrir(UUID societeId, AlerteLevee alerte, OffsetDateTime vueLe);

    void resoudre(UUID alerteId, OffsetDateTime resolueLe);

    /** Les {@code limite} entrées les plus récemment actives : {@code COALESCE(resolue, vue) DESC}, puis vue DESC. */
    List<Entree> recentes(UUID societeId, int limite);

    record Entree(UUID centreId, String centre, String code, String gravite, BigDecimal valeur, OffsetDateTime vueLe,
                  OffsetDateTime resolueLe) {
    }
}
