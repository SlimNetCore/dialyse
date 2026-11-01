package com.hemodialyse.backend.contexts.planification.interfaces.http.dto;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;

import java.util.List;
import java.util.UUID;

public record CelluleReponse(UUID salleId, UUID creneauId, JourDeLaSemaine jour, int capacite,
                             List<OccupantReponse> occupants) {
}
