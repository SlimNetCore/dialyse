package com.hemodialyse.backend.contexts.documents.domain;

/** Une demande d'impression refusée (champ obligatoire absent, identifiant illisible, modèle introuvable) : 400, message lisible. */
public class DemandeInvalide extends IllegalArgumentException {

    public DemandeInvalide(String message) {
        super(message);
    }
}
