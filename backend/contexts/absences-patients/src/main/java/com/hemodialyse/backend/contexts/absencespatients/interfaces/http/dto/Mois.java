package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record Mois(String mois, String statut, UUID cloturePar, Instant clotureLe, UUID rouvertPar,
                   Instant rouvertLe, String motifDeReouverture) {
}
