package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les demandes d'examen (tables {@code demandes_examen} et {@code lignes_demande_examen}, inchangées ;
 * les lignes n'ont PAS de colonne de centre, ADR 0072). Toujours cherchées par (identifiant, patient, centre). Une ligne de
 * demande dont le statut ou la catégorie est illisible en base est ignorée à la lecture (KB-MED-39 corrigé : elle ne casse plus la liste).
 */
public interface DemandesDExamen {

    /** Enregistre une demande nouvelle (en-tête et lignes). */
    void ajouter(DemandeExamen demande);

    /** Enregistre la transition d'une demande existante (statut, conclusion, date de modification) : les lignes ne changent jamais. */
    void enregistrerLaTransition(DemandeExamen demande);

    /**
     * Lecture avec verrou de la demande jusqu'à la fin de la transaction (deux transitions simultanées se suivent). Lève
     * {@link EcritureConcurrente} si le verrou n'est pas obtenu à temps.
     */
    Optional<DemandeExamen> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Demandes du patient, de la date de demande la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDeDemandesDExamen duPatient(CentreId centre, PatientId patient, int page, int taille);
}
