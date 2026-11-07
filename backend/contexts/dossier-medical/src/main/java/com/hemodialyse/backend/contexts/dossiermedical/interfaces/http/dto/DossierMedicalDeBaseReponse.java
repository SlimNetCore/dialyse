package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse du dossier médical de base (mêmes champs que le legacy). */
public record DossierMedicalDeBaseReponse(UUID id, UUID patientId, UUID centerId, String nephropathieInitiale, LocalDate dateMiseEnDialyse,
                                          String hepatiteBStatut, String hepatiteCStatut, String observationGlobale, String conclusionMedicale,
                                          OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static DossierMedicalDeBaseReponse de(DossierMedicalDeBase d) {
        return new DossierMedicalDeBaseReponse(d.id(), d.patient().valeur(), d.centre().valeur(), d.nephropathieInitiale(),
                d.dateMiseEnDialyse(), d.hepatiteBStatut(), d.hepatiteCStatut(), d.observationGlobale(), d.conclusionMedicale(),
                horodatage(d.creeLe()), horodatage(d.modifieLe()));
    }
}
