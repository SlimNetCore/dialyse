package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

public record ValiderDemandeRequete(UUID centreId, String conclusion) {
}
