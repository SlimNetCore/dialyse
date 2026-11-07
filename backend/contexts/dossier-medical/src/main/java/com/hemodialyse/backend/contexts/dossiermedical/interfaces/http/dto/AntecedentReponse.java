package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Un antécédent, champ par champ comme le legacy. */
public record AntecedentReponse(UUID id, UUID patientId, UUID centerId, String type, String codeSystem, String code, String codeDisplay,
                                String libelleLibre, LocalDate dateDebut, LocalDate dateFin, String statutClinique, String severite,
                                String note, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static AntecedentReponse de(Antecedent a) {
        var d = a.diagnostic();
        return new AntecedentReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.type().name(),
                d == null ? null : d.systeme().name(), d == null ? null : d.code(), d == null ? null : d.affichage(), a.libelleLibre(),
                a.debut(), a.fin(), a.statut().name(), a.severite(), a.note(), horodatage(a.creeLe()), horodatage(a.modifieLe()));
    }
}
