package com.hemodialyse.backend.contexts.dossiermedical.domain;

/**
 * Une valeur saisie sort de sa borne (poids sec cible hors de 20 à 300 kg, page ou taille de recherche hors borne) : 400 avec le
 * code {@code VALIDATION_ERROR}, comme la validation de formulaire du legacy (code reproduit, les clients le testent).
 */
public class ValeurHorsBorne extends SaisieInvalide {
    public ValeurHorsBorne(String message) {
        super(message);
    }
}
