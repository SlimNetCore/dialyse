package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les sérologies des patients (table {@code serologies_patient}, inchangée, sans contrainte d'unicité).
 * Toujours cherchées par (identifiant, patient, centre). Ordre global des verrous : centre (Planification), fiches (cette table),
 * puis {@code app_user}, puis verrous consultatifs.
 */
public interface Serologies {

    /**
     * Dit si un résultat existe déjà pour ce patient, ce marqueur et cette date de prélèvement, EN VERROUILLANT ce triplet jusqu'à la
     * fin de la transaction (deux enregistrements simultanés se suivent : l'unicité n'a pas de contrainte en base, KB-MED-56).
     * Lève {@link EcritureConcurrenteSurUneSerologie} si le verrou n'est pas obtenu à temps.
     */
    boolean existeEnVerrouillant(CentreId centre, PatientId patient, MarqueurSerologique marqueur, LocalDate datePrelevement);

    void ajouter(Serologie serologie);

    /** Enregistre la correction (résultat, conduite à tenir, date de modification) d'une sérologie existante. */
    void enregistrerLaCorrection(Serologie serologie);

    /** Lecture avec verrou de la sérologie jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUneSerologie}. */
    Optional<Serologie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime la sérologie ; faux si elle n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /** Sérologies du patient, de la date de prélèvement la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDeSerologies duPatient(CentreId centre, PatientId patient, int page, int taille);

    /** Toutes les sérologies du patient (pour le statut sérologique). */
    List<Serologie> toutesDuPatient(CentreId centre, PatientId patient);
}
