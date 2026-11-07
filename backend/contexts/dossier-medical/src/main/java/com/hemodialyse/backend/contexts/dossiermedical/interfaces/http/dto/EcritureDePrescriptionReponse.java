package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Réponse d'une création ou d'une modification : identifiant, patient, date de dernière modification (comme le legacy). */
public record EcritureDePrescriptionReponse(UUID id, UUID patientId, OffsetDateTime updatedAt) {
    public static EcritureDePrescriptionReponse de(PrescriptionMedicale p) {
        return new EcritureDePrescriptionReponse(p.id(), p.patient().valeur(), OffsetDateTime.ofInstant(p.modifieeLe(), ZoneOffset.UTC));
    }
}
