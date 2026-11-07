package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;

import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse d'une écriture (reproduit du legacy) : identifiant, patient et {@code updatedAt}, qui est la date d'enregistrement de l'abord. */
public record EcritureDAbordVasculaireReponse(UUID id, UUID patientId, OffsetDateTime updatedAt) {
    public static EcritureDAbordVasculaireReponse de(AbordVasculaire a) {
        return new EcritureDAbordVasculaireReponse(a.id(), a.patient().valeur(), horodatage(a.enregistreLe()));
    }
}
