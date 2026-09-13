package com.hemodialyse.backend.domain.medical.kdigo.valueobject;

import java.math.BigDecimal;

/**
 * Value Object — une cible clinique KDIGO : bornes (l'une des deux peut être absente pour une
 * cible à sens unique, ex. Kt/V ≥ 1.2 n'a pas de borne haute), unité, et référence de la version
 * de la recommandation dont elle est issue (traçabilité — une alerte doit pouvoir être expliquée).
 */
public record RegleCibleKdigo(String code, BigDecimal borneMin, BigDecimal borneMax, String unite, String reference) {
}
