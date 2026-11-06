package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/**
 * Les constantes relevées par l'infirmier pendant une séance (volet paramédical, saisi par la Séance, SE2) : poids avant et
 * après, tension avant et après, débit de sang, ultrafiltration, durée.
 * <p>
 * Valeurs RESTITUÉES TELLES QU'ELLES SONT EN BASE, sans borne ni normalisation (KB-MED-09/10/11, décision du 06/11 : bornes
 * physiologiques et notation cmHg « à décider avec les métiers »). Une mesure absente est nulle ; la tension est un texte
 * (« 130/80 », mais aussi « 12/8 » ou « n/a » si la valeur vient d'un import).
 */
public record Constantes(BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant, String tensionApres,
                         Integer debitSangMlMin, BigDecimal ultrafiltrationMl, Integer dureeMinutes) {

    private static final Constantes VIDES = new Constantes(null, null, null, null, null, null, null);

    /** Séance sans volet paramédical : aucune constante relevée. */
    public static Constantes vides() {
        return VIDES;
    }

    public boolean sontVides() {
        return poidsAvantKg == null && poidsApresKg == null && tensionAvant == null && tensionApres == null
                && debitSangMlMin == null && ultrafiltrationMl == null && dureeMinutes == null;
    }
}
