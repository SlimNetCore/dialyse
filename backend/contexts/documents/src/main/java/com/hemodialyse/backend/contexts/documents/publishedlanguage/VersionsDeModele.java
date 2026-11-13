package com.hemodialyse.backend.contexts.documents.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié de Documents : les versions de gabarit d'un modèle suivent le modèle. Appelé à la suppression d'un modèle (gestion des
 * modèles, plateforme jusqu'à DOC4a) pour retirer son historique.
 */
public interface VersionsDeModele {

    /** Retire toutes les versions de gabarit du modèle ; sans effet si ce modèle n'appartient pas à ce centre. */
    void retirerToutes(UUID centre, UUID modele);
}
