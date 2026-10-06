package com.hemodialyse.backend.domain.seance.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Trace de la suppression d'une séance : quelle séance (patient, date, statut au moment de la suppression), pourquoi,
 * par qui et quand. La séance elle-même et ses données liées disparaissent ; cette trace reste.
 */
public record SuppressionSeance(UUID id, UUID centerId, UUID seanceId, UUID patientId, LocalDate dateSeance,
                                SeanceStatus statut, MotifSuppressionSeance motif, String supprimePar,
                                Instant supprimeLe) {

    /**
     * Prépare la suppression d'une séance, après vérification qu'elle est supprimable.
     */
    public static SuppressionSeance de(Seance seance, MotifSuppressionSeance motif, String utilisateur, Instant maintenant) {
        seance.verifierSuppressible();
        return new SuppressionSeance(UUID.randomUUID(), seance.getCenterId(), seance.getId(), seance.getPatientId(),
                seance.getDateSeance(), seance.getStatus(), motif,
                utilisateur == null || utilisateur.isBlank() ? "system" : utilisateur, maintenant);
    }
}
