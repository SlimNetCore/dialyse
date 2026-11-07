package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

/** Corps de la correction d'un résultat : seuls le résultat et la conduite à tenir comptent, le reste est ignoré (comme le legacy). */
public record CorrectionDeSerologieDemande(UUID centerId, String resultat, String conduiteATenir) {
}
