package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import java.util.UUID;

public record ReouvertureReponse(UUID centerId, String mois, String statut) {
}
