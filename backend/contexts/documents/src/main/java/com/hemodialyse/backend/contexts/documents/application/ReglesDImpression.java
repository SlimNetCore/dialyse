package com.hemodialyse.backend.contexts.documents.application;

import java.util.Map;
import java.util.UUID;

/**
 * Port sortant de l'impression : les règles que les contextes propriétaires de certains types de document imposent (profil et centre
 * exigés, paramètres posés par le serveur). Documents les orchestre sans les connaître ; les refus sont ceux du contexte propriétaire
 * (403 {@code ACCESS_DENIED}). Implémenté par {@link ReglesDesTypesDeclares} sur les règles publiées (ADR 0166).
 */
public interface ReglesDImpression {

    /** Voie par type ({@code POST /print}) : exécutée AVANT toute validation de la demande et toute lecture de données. */
    void garderParType(String type, UUID centreDemande);

    /**
     * Voie par modèle ({@code POST /print/{modeleId}}) : seuls les types dont la règle l'exige sont gardés.
     *
     * @param typeDuModele le type du modèle chargé, {@code null} si le modèle est inconnu
     */
    void garderParModele(String typeDuModele, UUID centreDemande);

    /** Les paramètres posés par le serveur pour ce type ; vide pour un type sans règle. */
    Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> parametres);
}
