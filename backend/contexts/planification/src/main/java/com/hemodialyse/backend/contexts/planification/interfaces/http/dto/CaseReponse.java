package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.UUID;

public record CaseReponse(UUID salleId, UUID creneauId, JourDeLaSemaine jour, int capacite, int occupes) {
}
