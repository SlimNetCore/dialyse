package com.hemodialyse.backend.contexts.seance.domain;

/** Une règle métier refuse la séance : 422 avec {@link MotifDeRefus#code()} et le message de l'API. */
public class SeanceRefusee extends RuntimeException {
    private final MotifDeRefus motif;

    public SeanceRefusee(MotifDeRefus motif, String message) {
        super(message);
        this.motif = motif;
    }

    public MotifDeRefus motif() {
        return motif;
    }
}
