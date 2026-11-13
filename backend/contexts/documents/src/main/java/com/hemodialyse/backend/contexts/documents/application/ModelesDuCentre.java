package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;

import java.util.Optional;
import java.util.UUID;

/** Port sortant : le modèle de document d'un centre (table {@code modele_document}), pour y rattacher l'historique de ses versions. */
public interface ModelesDuCentre {

    /** Le modèle de cet identifiant s'il appartient à ce centre ; vide sinon (un modèle d'un autre centre est inexistant). */
    Optional<ModeleDuCentre> duCentre(UUID centre, UUID modele);
}
