package com.hemodialyse.backend.contexts.seance.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Corps de {@code POST /api/v1/seances} : mêmes champs JSON que le legacy ; {@code dateSeance} absente : le jour du centre. */
public record CreationDeSeanceDemande(UUID centerId, UUID patientId, LocalDate dateSeance) {
}
