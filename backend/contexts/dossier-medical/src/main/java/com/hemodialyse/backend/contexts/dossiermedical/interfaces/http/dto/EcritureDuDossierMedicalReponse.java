package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse d'un enregistrement du dossier médical de base : de quoi rafraîchir la liste côté client (mêmes champs que le legacy). */
public record EcritureDuDossierMedicalReponse(UUID id, UUID patientId, OffsetDateTime updatedAt) {
    public static EcritureDuDossierMedicalReponse de(DossierMedicalDeBase d) {
        return new EcritureDuDossierMedicalReponse(d.id(), d.patient().valeur(), horodatage(d.modifieLe()));
    }
}
