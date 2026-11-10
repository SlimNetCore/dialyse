package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import java.util.UUID;

/**
 * Un paramètre de requête obligatoire est absent : 400 {@code BAD_REQUEST} avec ce détail (RF1-RF3d). Reconnu par son type par
 * {@link RefusDeParametre}, plus par le texte du message.
 */
final class ParametreObligatoireAbsent extends IllegalArgumentException {

    ParametreObligatoireAbsent(String nom) {
        super("Paramètre obligatoire absent : " + nom);
    }

    /** Le {@code centerId} de la requête, ou ce refus s'il est absent. */
    static UUID centreObligatoire(UUID centerId) {
        if (centerId == null) throw new ParametreObligatoireAbsent("centerId");
        return centerId;
    }
}
