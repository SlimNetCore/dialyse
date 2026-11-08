package com.hemodialyse.backend.contexts.seance.domain;

import java.util.UUID;

/**
 * Identité de la facture qui a pris la séance (colonne {@code seances.facture_id}, posée par la Facturation, jamais par la Séance). Une
 * référence par identité vers un autre contexte : la Séance ne lit rien d'autre de la facture.
 */
public record FactureId(UUID valeur) {
    public FactureId {
        if (valeur == null) throw new SaisieInvalide("Facture requise");
    }
}
