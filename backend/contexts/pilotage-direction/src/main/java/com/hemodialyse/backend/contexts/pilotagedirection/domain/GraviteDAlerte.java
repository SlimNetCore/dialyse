package com.hemodialyse.backend.contexts.pilotagedirection.domain;

/**
 * La gravité d'une alerte de la direction. Les noms des constantes sont ceux de la colonne {@code severity} de
 * {@code direction_alert_history} et du JSON servi : ce sont des valeurs de contrat, ils ne sont pas renommés (PD3, ADR 0104).
 */
public enum GraviteDAlerte {
    WARNING, CRITICAL
}
