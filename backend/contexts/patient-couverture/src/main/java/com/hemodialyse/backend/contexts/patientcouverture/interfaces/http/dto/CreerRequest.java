package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http.dto;

import java.time.LocalDate;
import java.util.UUID;

public record CreerRequest(UUID patientId, UUID centreId, LocalDate dateDebutDemande, LocalDate dateFinDemande,
                           UUID forfaitDemandeId, UUID auteurId) {
}
