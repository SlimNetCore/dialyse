package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.Map;
import java.util.UUID;

/**
 * Port FOURNI PAR LA PLATEFORME (transitoire, DOC1) : les règles qui appartiennent aux contextes propriétaires de certains types de
 * document et que la route d'impression applique sans les connaître : profil et centre exigés pour l'ordonnance (Dossier médical) et pour
 * la synthèse mensuelle de facturation (Facturation), et totaux de la synthèse (posés par le serveur). Chaque contexte déclarera ses règles
 * avec son {@link TypeDeDocumentDeclare} (DOC4) ; ce port disparaît alors.
 *
 * <p>Les refus sont des {@code AccessDeniedException} de la plateforme (403, même corps qu'avant).
 */
public interface ReglesDImpressionDesTypesSensibles {

    /** Voie par type ({@code POST /print}) : exécutée AVANT toute validation de la demande ni lecture de données. */
    void garderParType(String type, UUID centreDemande);

    /**
     * Voie par modèle ({@code POST /print/{modeleId}}) : seule la synthèse de facturation est gardée (comportement historique ; une
     * ordonnance imprimée par son modèle n'est pas gardée par profil, question ouverte de l'ADR 0142).
     *
     * @param typeDuModele le type du modèle chargé, {@code null} si le modèle est inconnu
     */
    void garderParModele(String typeDuModele, UUID centreDemande);

    /** Les paramètres posés par le serveur pour ce type (totaux de la synthèse) ; vide pour les autres types. */
    Map<String, Object> donneesDuServeur(String type, UUID centre, Map<String, String> parametres);
}
