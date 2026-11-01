package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.time.LocalDate;
import java.util.UUID;

public record SeanceSansCaseReponse(UUID patientId, String nom, LocalDate date, JourDeLaSemaine jour) {
}
