package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié (fourni par le Dossier médical aux routes du dossier de greffe encore servies par la plateforme : KDIGO et export PDF,
 * DM7) : exige que le patient existe dans le centre. Un patient inconnu ou d'un autre centre est refusé en 404 {@code PATIENT_INTROUVABLE}
 * par l'advice du contexte (contrôleur marqué {@link SousLaGardeDuDossierMedical}) ; aucune donnée n'est lue ni écrite.
 */
public interface GardeDePatient {

    void exiger(UUID centre, UUID patient);
}
