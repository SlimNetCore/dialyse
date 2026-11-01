package com.hemodialyse.backend.contexts.organisation.domain;

/**
 * Détail d'un centre payeur avec son agence et sa caisse (value object en lecture seule).
 */
public record DetailPayeur(String id, String codeCentrePayeur, String libelleCentrePayeur,
                           String adresseCentrePayeur, String codeAgence, String libelleAgence,
                           String codeCaisse, String libelleCaisse) {
}
