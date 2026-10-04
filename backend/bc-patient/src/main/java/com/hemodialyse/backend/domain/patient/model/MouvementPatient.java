package com.hemodialyse.backend.domain.patient.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mouvement d'un patient dans le centre : trace immuable d'un changement d'état ou de la libération de sa place.
 * Les identifiants de salle, créneau et générateur et les jours de dialyse sont ceux que le patient occupait au moment
 * du mouvement (historique d'affectation).
 *
 * @param etatPrecedent état avant le mouvement (nul à l'admission)
 * @param etatNouveau   état après le mouvement
 * @param dateEffet     date de l'évènement (sortie, fin de séjour, admission) ou date de libération de la place
 * @param joursDialyse  jours de dialyse occupés, codes séparés par des virgules (ex. {@code LUNDI,MERCREDI})
 * @param automatique   mouvement produit par le traitement de libération des places (et non par une saisie)
 */
public record MouvementPatient(
        UUID id,
        UUID centerId,
        UUID patientId,
        TypeMouvementPatient type,
        LocalDate dateEffet,
        String etatPrecedent,
        String etatNouveau,
        UUID salleId,
        UUID positionId,
        UUID generateurId,
        String joursDialyse,
        boolean automatique,
        Instant creeLe
) {
}
