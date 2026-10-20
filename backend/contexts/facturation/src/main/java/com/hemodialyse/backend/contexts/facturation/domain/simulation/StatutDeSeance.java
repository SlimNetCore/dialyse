package com.hemodialyse.backend.contexts.facturation.domain.simulation;

/**
 * Statut d'une séance tel que le module Séance le tient (le vocabulaire reste celui du module Séance, hors Facturation). Seules
 * les séances {@code VALIDEE} ou {@code SIGNEE} sont facturables ; la comparaison est exacte, casse comprise (KB-FAC-17
 * reproduit : une séance « validee » en minuscules est ignorée).
 */
public record StatutDeSeance(String valeur) {

    public static final StatutDeSeance VALIDEE = new StatutDeSeance("VALIDEE");
    public static final StatutDeSeance SIGNEE = new StatutDeSeance("SIGNEE");
    public static final StatutDeSeance ABSENT = new StatutDeSeance("ABSENT");

    public StatutDeSeance {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le statut de la séance est obligatoire");
        }
    }

    public boolean estFacturable() {
        return equals(VALIDEE) || equals(SIGNEE);
    }

    @Override
    public String toString() {
        return valeur;
    }
}
