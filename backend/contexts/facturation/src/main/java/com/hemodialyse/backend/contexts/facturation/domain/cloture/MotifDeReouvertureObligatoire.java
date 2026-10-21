package com.hemodialyse.backend.contexts.facturation.domain.cloture;

/** Refus 400 {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE} : une réouverture se justifie (et le motif reste borné). */
public final class MotifDeReouvertureObligatoire extends RuntimeException {
    public MotifDeReouvertureObligatoire() {
        super("Le motif de reouverture est obligatoire");
    }

    public MotifDeReouvertureObligatoire(String message) {
        super(message);
    }
}
