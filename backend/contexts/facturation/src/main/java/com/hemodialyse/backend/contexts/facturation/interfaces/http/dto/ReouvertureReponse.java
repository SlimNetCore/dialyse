package com.hemodialyse.backend.contexts.facturation.interfaces.http.dto;

import java.util.UUID;

public record ReouvertureReponse(UUID centerId, String mois, String statut) {
}
