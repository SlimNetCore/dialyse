package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrégat satellite : dérogation autorisant la régularisation (déverrouillage) d'une séance passée restée « créée ».
 */
public record DerogationRegularisation(DerogationId id, CentreId centreId, SeanceId seanceId, String motif,
                                       String autorite, OffsetDateTime date) {
    public DerogationRegularisation(DerogationId id, CentreId centreId, SeanceId seanceId,
                                    String motif, String autorite, OffsetDateTime date) {
        if (id == null) throw new IllegalArgumentException("DerogationId requis");
        if (centreId == null) throw new IllegalArgumentException("Centre requis");
        if (seanceId == null) throw new IllegalArgumentException("SeanceId requis");
        if (motif == null || motif.isBlank()) throw new IllegalArgumentException("Motif requis");
        if (autorite == null || autorite.isBlank()) throw new IllegalArgumentException("Autorite requise");
        if (date == null) throw new IllegalArgumentException("Date requise");
        this.id = id;
        this.centreId = centreId;
        this.seanceId = seanceId;
        this.motif = motif.trim();
        this.autorite = autorite.trim();
        this.date = date;
    }

    public static DerogationRegularisation declarer(DerogationId id, CentreId centreId, SeanceId seanceId,
                                                    String motif, String autorite, OffsetDateTime date) {
        return new DerogationRegularisation(id, centreId, seanceId, motif, autorite, date);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DerogationRegularisation d)) return false;
        return Objects.equals(id, d.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}