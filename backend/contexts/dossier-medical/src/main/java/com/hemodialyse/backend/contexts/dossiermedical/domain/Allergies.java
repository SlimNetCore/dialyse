package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les allergies des patients (table {@code allergies_patient}, inchangée, sans contrainte d'unicité ni clé
 * étrangère). Toujours cherchées par (identifiant, patient, centre). Ordre global des verrous : centre (Planification), fiches (cette
 * table), puis {@code app_user}, puis verrous consultatifs.
 */
public interface Allergies {
    void ajouter(Allergie allergie);

    /** Enregistre la correction (criticité, manifestations, statut, date de modification). */
    void enregistrerLaCorrection(Allergie allergie);

    /** Lecture avec verrou jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUneAllergie}. */
    Optional<Allergie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'allergie ; faux si elle n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /** Allergies du patient, de la constatation la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDeFiches<Allergie> duPatient(CentreId centre, PatientId patient, int page, int taille);

    /** Allergies de criticité HAUTE du patient (statut quelconque : reproduit), même ordre. */
    List<Allergie> critiquesDuPatient(CentreId centre, PatientId patient);
}
