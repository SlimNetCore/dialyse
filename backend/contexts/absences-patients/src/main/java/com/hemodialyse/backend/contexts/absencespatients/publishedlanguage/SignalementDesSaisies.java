package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Langage publié (besoin des Absences, fourni par la plateforme : centre de notifications, hors contexte) : signale au médecin
 * du centre qu'une absence vient d'être saisie, et rafraîchit les écrans qui l'affichent (saisie temps réel
 * {@code SAISIE_INFIRMIER / ABSENCE}). Appelé dans la transaction de l'écriture : l'adaptateur attend sa validation pour
 * diffuser, et un signalement impossible ne fait jamais échouer la saisie.
 */
public interface SignalementDesSaisies {

    void absenceSaisie(UUID centre, UUID patient, LocalDate jourDeLAbsence);
}
