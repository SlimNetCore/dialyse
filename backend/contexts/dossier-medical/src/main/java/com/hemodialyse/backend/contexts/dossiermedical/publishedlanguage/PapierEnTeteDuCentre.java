package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.UUID;

/**
 * Port fourni par la plateforme (moteur de documents) : l'identité imprimée du centre et de sa société (logo, en-tête, pied de page),
 * posée sur un document HTML avant son rendu PDF (PL2, export du dossier de greffe).
 */
public interface PapierEnTeteDuCentre {

    /** Le document HTML avec l'en-tête et le pied de page d'identité du centre ; rendu tel quel si le document n'a pas de corps. */
    String habiller(String html, UUID centre);
}
