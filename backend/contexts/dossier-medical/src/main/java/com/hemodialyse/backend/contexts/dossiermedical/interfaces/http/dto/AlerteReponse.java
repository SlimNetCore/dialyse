package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AlerteDObservance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Une alerte d'observance, champ par champ comme le legacy ({@code type} = la nature de l'alerte). */
public record AlerteReponse(UUID id, UUID patientId, UUID centerId, String typeTraitement, String type, LocalDate periodeDebut, LocalDate periodeFin,
                            int dosesAttendues, int dosesAdministrees, String uniteDose, Integer dosePrescrite, Integer frequenceValeur,
                            String frequenceUnite, String message, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {

    public static AlerteReponse de(AlerteDObservance a) {
        return new AlerteReponse(a.id(), a.patient().valeur(), a.centre().valeur(), a.traitement().name(), a.nature().name(), a.debut(), a.fin(),
                a.attendues(), a.administrees(), a.detail().unite(), a.detail().dosePrescrite(), a.detail().frequenceValeur(),
                a.detail().frequenceUnite(), a.message(), OffsetDateTime.ofInstant(a.creeeLe(), ZoneOffset.UTC),
                a.resolueLe() == null ? null : OffsetDateTime.ofInstant(a.resolueLe(), ZoneOffset.UTC));
    }
}
