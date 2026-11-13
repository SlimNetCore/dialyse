package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : les modèles de document (table {@code modele_document}, possédée par Documents), en lecture pour l'impression et en écriture pour leur gestion. */
public interface ModelesDeDocument {

    /** Le modèle ACTIF le plus récent du centre pour ce type (déjà normalisé), s'il existe. */
    Optional<ModeleDeDocument> actifDuCentre(UUID centre, String typeNormalise);

    /** Le modèle de cet identifiant, actif ou non, s'il appartient à ce centre. */
    Optional<ModeleDeDocument> duCentre(UUID modele, UUID centre);

    /** Le type d'un modèle, de n'importe quel centre ; vide si le modèle est inconnu ou sans type. */
    Optional<String> typeDuModele(UUID modele);

    /** Tous les modèles du centre, actifs ou non, par libellé ; {@code type} (comparé tel quel) restreint la liste si renseigné. */
    List<ModeleDuCatalogue> lister(UUID centre, String type);

    /** Une page de modèles du centre, par libellé ; {@code recherche.type()} (comparé tel quel) restreint la recherche si renseigné. */
    List<ModeleDuCatalogue> rechercher(RechercheDeModeles recherche);

    /** Enregistre un nouveau modèle sous cet identifiant. @throws ModeleEnDoublon si le code est déjà pris dans le centre ou l'identifiant déjà attribué */
    void creer(UUID id, DefinitionDeModele modele);

    /** Remplace la définition du modèle de ce centre ; sans effet si ce modèle n'existe pas dans ce centre. @throws ModeleEnDoublon si le code est pris par un autre modèle du centre */
    void modifier(UUID id, DefinitionDeModele modele);

    /** Supprime le modèle de ce centre ; sans effet s'il n'existe pas dans ce centre. */
    void supprimer(UUID centre, UUID id);
}
