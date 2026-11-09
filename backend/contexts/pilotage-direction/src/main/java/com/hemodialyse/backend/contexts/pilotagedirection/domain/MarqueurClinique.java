package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.math.BigDecimal;

/**
 * Patients évalués et répartition face à la cible KDIGO d'un marqueur clinique, tels que le Dossier médical les publie : DÉJÀ MASQUÉS par
 * sa propre politique d'anonymat (seuil 5, ADR 0101). Le Pilotage ne les re-masque pas ; une valeur masquée est {@code null}.
 */
public record MarqueurClinique(Long evalues, BigDecimal pctDansCible, BigDecimal pctSousCible, BigDecimal pctAuDessus) {
}
