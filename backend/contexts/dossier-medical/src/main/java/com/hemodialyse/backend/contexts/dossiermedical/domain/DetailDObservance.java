package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Ce que disait la prescription au moment de l'alerte : unité, dose prescrite, fréquence (tout absent pour une alerte sans dose). */
public record DetailDObservance(String unite, Integer dosePrescrite, Integer frequenceValeur, String frequenceUnite) {
    public static final DetailDObservance AUCUN = new DetailDObservance(null, null, null, null);
}
