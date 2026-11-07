package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Une allergie, champ par champ comme le legacy. */
public record AllergieReponse(UUID id, UUID patientId, UUID centerId, String codeSystem, String code, String codeDisplay, String categorie,
                              String criticite, String typeReaction, String manifestations, LocalDate dateConstatation,
                              String statutVerification, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static AllergieReponse de(Allergie a) {
        return new AllergieReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.substance().systeme().name(), a.substance().code(),
                a.substance().affichage(), a.categorie().name(), a.criticite().name(), a.typeDeReaction().name(), a.manifestations(),
                a.dateDeConstatation(), a.statut().name(), horodatage(a.creeLe()), horodatage(a.modifieLe()));
    }
}
