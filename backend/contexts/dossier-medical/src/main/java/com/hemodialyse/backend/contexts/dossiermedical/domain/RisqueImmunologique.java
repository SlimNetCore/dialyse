package com.hemodialyse.backend.contexts.dossiermedical.domain;

import java.math.BigDecimal;

/** Risque immunologique du candidat à la greffe d'après le PRA (le pire des deux classes), RG-MED-082. */
public record RisqueImmunologique(String code, BigDecimal valeur, String unite, NiveauDeRisqueKdigo niveau, String referenceKdigo) {
}
