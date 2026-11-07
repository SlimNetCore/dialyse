package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;

/**
 * Faits du planning d'un patient pour une date, lus par le port du planning : le domaine en déduit s'il est attendu.
 *
 * @param centreFerme       jour férié ou fermeture exceptionnelle du centre
 * @param jourDeDialyse     la date tombe un jour de dialyse de la fiche du patient
 * @param enSommeil         patient mis en sommeil
 * @param etatDuPatient     état (transféré, décédé, occasionnel…) ou {@code null} pour un patient régulier
 * @param dateDeLEtat       date de l'évènement d'état (sortie, fin de séjour) ou {@code null}
 * @param dateDAdmission    date d'admission au centre ou {@code null}
 */
public record SituationDuPlanning(boolean centreFerme, boolean jourDeDialyse, boolean enSommeil, String etatDuPatient,
                                  LocalDate dateDeLEtat, LocalDate dateDAdmission) {
}
