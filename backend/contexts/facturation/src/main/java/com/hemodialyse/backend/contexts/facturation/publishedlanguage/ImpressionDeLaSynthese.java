package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

/**
 * Port sortant : mettre en page la synthèse (le moteur d'impression, les gabarits et les formats appartiennent à la
 * plateforme). Le tableau forfait x caisse est lu par le gabarit lui-même ; le port reçoit les indicateurs, les répartitions
 * et les totaux enregistrés des factures.
 */
public interface ImpressionDeLaSynthese {

    byte[] imprimer(SyntheseMensuelle synthese, FormatDImpression format);
}
