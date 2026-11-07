package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse d'une étape du bilan pré-greffe (mêmes champs que le legacy). */
public record EtapeDeBilanReponse(UUID id, UUID patientId, UUID centerId, String categorie, String libelle, String statut,
                                  LocalDate dateRealisation, String resultat, LocalDate dateExpiration, UUID demandeExamenId, UUID serologieId,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static EtapeDeBilanReponse de(EtapeDeBilan e) {
        return new EtapeDeBilanReponse(e.id(), e.patient().valeur(), e.centre().valeur(), e.categorie().name(), e.libelle(), e.statut().name(),
                e.dateRealisation(), e.resultat(), e.dateExpiration(), e.demandeExamenId(), e.serologieId(), horodatage(e.creeLe()),
                horodatage(e.modifieLe()));
    }
}
