package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/**
 * Cible clinique KDIGO : bornes (l'une des deux peut être absente pour une cible à sens unique, Kt/V ≥ 1,2 n'a pas de borne haute),
 * unité, référence de la recommandation dont elle est issue (une alerte doit pouvoir être expliquée). Repris mot pour mot de
 * {@code RegleCibleKdigo} (bc-medical, DM9, ADR 0101).
 */
public record RegleDeCible(String code, BigDecimal borneMin, BigDecimal borneMax, String unite, String reference) {
}
