package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (besoin du Dossier médical, fourni par la plateforme jusqu'à PL13b) : la saisie temps réel
 * {@code SAISIE_INFIRMIER / ANEMIE} d'une administration, signalée au médecin. Cette notification est émise par la Séance (saisie de
 * l'infirmier) et n'est pas du catalogue du Dossier médical. L'adaptateur attend la validation de la transaction en cours.
 */
public interface SaisieDeLAdministrationSignalee {

    void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration);
}
