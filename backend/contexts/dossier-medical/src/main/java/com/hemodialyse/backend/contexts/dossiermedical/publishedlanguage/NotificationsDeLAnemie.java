package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié (besoin du Dossier médical, fourni par la plateforme : centre de notifications, hors contexte) pour le traitement
 * de l'anémie : (1) la saisie temps réel {@code SAISIE_INFIRMIER / ANEMIE} d'une administration (diffuse le nom, le prénom du patient
 * et l'auteur : minimisation à décider, KB-MED-25) ; (2) l'alerte durable {@code OBSERVANCE_NON_RESPECTEE} destinée aux médecins
 * (renvoyée à chaque nuit tant que l'alerte est ouverte : reproduit, KB-MED-28 c). L'adaptateur attend la validation de la transaction
 * en cours pour diffuser ; un signalement impossible ne fait jamais échouer l'écriture.
 */
public interface NotificationsDeLAnemie {

    void administrationSaisie(UUID centre, UUID patient, LocalDate jourDeLAdministration);

    void observanceNonRespectee(UUID centre, UUID patient, String message, Map<String, String> details);
}
