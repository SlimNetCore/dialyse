package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : lecture des modèles de document (table {@code modele_document}, possédée par Documents). */
public interface ModelesDeDocument {

    /** Le modèle ACTIF le plus récent du centre pour ce type (déjà normalisé), s'il existe. */
    Optional<ModeleDeDocument> actifDuCentre(UUID centre, String typeNormalise);

    /** Le modèle de cet identifiant, actif ou non, s'il appartient à ce centre. */
    Optional<ModeleDeDocument> duCentre(UUID modele, UUID centre);

    /** Le type d'un modèle, de n'importe quel centre ; vide si le modèle est inconnu ou sans type. */
    Optional<String> typeDuModele(UUID modele);
}
