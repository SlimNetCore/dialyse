package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.TypeDeConflit;

import java.util.List;
import java.util.UUID;

public record ConflitReponse(TypeDeConflit type, UUID salleId, UUID creneauId, JourDeLaSemaine jour,
                             List<String> patients) {
}
