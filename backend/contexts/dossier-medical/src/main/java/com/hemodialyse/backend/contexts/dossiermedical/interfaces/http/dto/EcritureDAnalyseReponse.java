package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse d'une écriture (reproduit du legacy) : identifiant, patient et date de modification de l'analyse. */
public record EcritureDAnalyseReponse(UUID id, UUID patientId, OffsetDateTime updatedAt) {
    public static EcritureDAnalyseReponse de(Analyse a) {
        return new EcritureDAnalyseReponse(a.id(), a.patient().valeur(), horodatage(a.modifieLe()));
    }
}
