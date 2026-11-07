package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par la plateforme, ADR 0088) : les faits du planning d'un patient à une date. Types primitifs : la
 * plateforme ne connaît aucune couche interne du contexte. Patient inconnu ou d'un autre centre : vide.
 */
public interface PlanningFourni {

    Optional<Situation> situation(UUID centre, UUID patient, LocalDate jour);

    /**
     * @param centreFerme     jour férié ou fermeture exceptionnelle du centre
     * @param jourDeDialyse   la date tombe un jour de dialyse de la fiche
     * @param enSommeil       patient mis en sommeil
     * @param etatDuPatient   état du patient ou nul
     * @param dateDeLEtat     date de l'évènement d'état ou nulle
     * @param dateDAdmission  date d'admission ou nulle
     */
    record Situation(boolean centreFerme, boolean jourDeDialyse, boolean enSommeil, String etatDuPatient, LocalDate dateDeLEtat,
                     LocalDate dateDAdmission) {
    }
}
