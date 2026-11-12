package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (fourni par la Séance au Dossier médical, PL13b, ADR 0154) : la saisie d'une administration (EPO, fer) par l'infirmier est
 * signalée au médecin du centre ({@code SAISIE_INFIRMIER / ANEMIE}). Dans une transaction, le signal attend la validation (jamais d'alerte
 * fantôme si l'écriture est annulée) ; un échec de notification ne fait jamais échouer la saisie.
 */
public interface SignalementDesAdministrationsSaisies {

    void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration);
}
