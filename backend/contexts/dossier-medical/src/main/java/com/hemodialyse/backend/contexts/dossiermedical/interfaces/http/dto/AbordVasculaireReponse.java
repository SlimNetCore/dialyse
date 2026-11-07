package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Un abord vasculaire, champ par champ comme le legacy. */
public record AbordVasculaireReponse(UUID id, UUID patientId, UUID centerId, String typeAbord, String cote, String localisation,
                                     LocalDate dateCreation, LocalDate dateFin, Boolean actif, String complications, OffsetDateTime createdAt) {
    public static AbordVasculaireReponse de(AbordVasculaire a) {
        return new AbordVasculaireReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.type(), a.cote(), a.localisation(), a.creation(),
                a.fin(), a.actif(), a.complications(), horodatage(a.enregistreLe()));
    }
}
