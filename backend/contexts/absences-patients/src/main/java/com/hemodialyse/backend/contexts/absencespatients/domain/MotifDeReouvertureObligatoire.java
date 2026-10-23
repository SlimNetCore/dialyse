package com.hemodialyse.backend.contexts.absencespatients.domain;

/**
 * Refus 400 {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE} : une réouverture se justifie (et le motif reste borné).
 */
public final class MotifDeReouvertureObligatoire extends RuntimeException {
    public MotifDeReouvertureObligatoire() {
        super("Le motif de réouverture est obligatoire");
    }

    public MotifDeReouvertureObligatoire(String message) {
        super(message);
    }
}
