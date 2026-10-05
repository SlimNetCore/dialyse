package com.hemodialyse.backend.domain.seance.model;

import java.time.LocalDate;

/**
 * Faits du planning d'un patient pour une date donnée, lus par l'adaptateur : le domaine en déduit s'il est attendu.
 *
 * @param centreFerme       jour férié ou fermeture exceptionnelle du centre
 * @param jourDialyse       la date tombe un jour de dialyse de la fiche patient
 * @param enSommeil         patient mis en sommeil
 * @param etatPatient       état du patient (transféré, décédé, occasionnel…) ou {@code null} pour un patient régulier
 * @param dateEvenementEtat date de l'évènement d'état (sortie, fin de séjour) ou {@code null}
 * @param dateAdmission     date d'admission au centre
 */
public record SituationPlanning(
        boolean centreFerme,
        boolean jourDialyse,
        boolean enSommeil,
        String etatPatient,
        LocalDate dateEvenementEtat,
        LocalDate dateAdmission
) {
}
