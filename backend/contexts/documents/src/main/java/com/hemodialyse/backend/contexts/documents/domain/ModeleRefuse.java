package com.hemodialyse.backend.contexts.documents.domain;

/** Un modèle de document refusé (chemin de gabarit invalide, champ obligatoire absent ou trop long, page demandée hors bornes) : 400, message lisible. */
public class ModeleRefuse extends IllegalArgumentException {

    public ModeleRefuse(String message) {
        super(message);
    }
}
