package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les observations biologiques (table {@code observations_biologiques}, inchangée, sans clé étrangère vers la
 * demande d'examen). Toujours cherchées par (identifiant, patient, centre). Une ligne dont le statut, la source ou le système de codage est
 * illisible en base, ou dont la valeur numérique n'a pas d'unité (donnée incohérente), est ignorée par toutes les lectures, comptage
 * compris. Ordre global des verrous : centre (Planification), fiches (cette table), puis {@code app_user}, puis verrous consultatifs.
 */
public interface ObservationsBiologiques {
    void ajouter(ObservationBiologique observation);

    /** Enregistre la correction (valeur, statut, date de modification). */
    void enregistrerLaCorrection(ObservationBiologique observation);

    /** Lecture avec verrou jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUneObservation}. */
    Optional<ObservationBiologique> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'observation ; faux si elle n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /**
     * Observations du patient, du prélèvement le plus récent au plus ancien (départage : création puis identifiant). {@code code} égalité
     * stricte (nul ou blanc : ignoré) ; {@code du} et {@code au} inclus et facultatifs (début après fin : page vide, reproduit).
     */
    PageDeFiches<ObservationBiologique> duPatient(CentreId centre, PatientId patient, String code, LocalDate du, LocalDate au, int page,
                                                  int taille);

    /** Observations du patient rattachées à la demande d'examen, même ordre. */
    List<ObservationBiologique> deLaDemande(CentreId centre, PatientId patient, UUID demandeDExamen);
}
