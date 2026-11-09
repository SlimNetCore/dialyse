package com.hemodialyse.backend.contexts.dossiermedical.domain;

/** Position d'une valeur face à sa cible ; {@code NON_EVALUABLE} pour une valeur absente (qui n'est pas une valeur hors cible). */
public enum StatutDeCible {
    DANS_CIBLE,
    SOUS_CIBLE,
    AU_DESSUS_CIBLE,
    NON_EVALUABLE
}
