package com.hemodialyse.backend.contexts.documents.interfaces.http;

import java.util.Map;
import java.util.UUID;

/**
 * Corps JSON des routes d'impression (mêmes champs qu'avant : {@code PrintRequest}).
 *
 * @param formatOverride {@code null} = le format du modèle
 * @param params         patientId, pecId, etc.
 */
public record DemandeDImpressionRequete(UUID centerId, String typeDocument, String formatOverride, Map<String, String> params) {
}
