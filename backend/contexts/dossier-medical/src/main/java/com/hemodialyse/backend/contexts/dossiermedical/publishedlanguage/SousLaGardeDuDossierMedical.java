package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

/**
 * Marqueur des contrôleurs de la plateforme (résidus legacy lus par ce contexte : KDIGO et export PDF du dossier de greffe, DM7) qui
 * rendent leurs refus par l'advice du Dossier médical : 404 {@code PATIENT_INTROUVABLE}, 403 avant 400, 400 stable. Le contexte ne dépend
 * pas de la plateforme : c'est elle qui implémente ce marqueur.
 */
public interface SousLaGardeDuDossierMedical {
}
