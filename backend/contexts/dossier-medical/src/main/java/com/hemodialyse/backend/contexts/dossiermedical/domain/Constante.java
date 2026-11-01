package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.util.UUID;

/**
 * Agrégat {@code Constante} — constantes vitales enregistrées pour un patient.
 */
public record Constante(ConstanteId id, UUID patientId, CentreId centreId, Instant dateEnregistrement, Double poidsKg,
                        String tensionArterielle, Integer pouls, Double temperatureC) {

    public Constante {
        if (patientId == null) throw new IllegalArgumentException("patientId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (dateEnregistrement == null) throw new IllegalArgumentException("dateEnregistrement requise");
    }
}