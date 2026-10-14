package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import java.util.UUID;

/** Identité d'une salle (contexte Organisation) : l'équipement la référence par identité seulement. */
public record SalleId(UUID valeur) {

    public SalleId {
        if (valeur == null) throw new IllegalArgumentException("Salle requise");
    }

    /** Pas de salle : {@code null} (un équipement n'est pas toujours affecté à une salle). */
    public static SalleId ouAucune(UUID valeur) {
        return valeur == null ? null : new SalleId(valeur);
    }
}
