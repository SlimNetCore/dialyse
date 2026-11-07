package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Port sortant : la place du patient en salle d'isolement (Planification, hors contexte). Les deux opérations s'exécutent dans la
 * transaction de l'écriture de la sérologie : la sérologie et la réévaluation réussissent ou échouent ensemble (KB-PLN-59, comportement
 * du legacy conservé).
 */
public interface IsolementDesPatients {

    /**
     * Prend le verrou des places du centre AVANT d'écrire la sérologie (ordre global des verrous : centre, puis fiches, puis verrous
     * consultatifs). Sans effet si le patient est inconnu du centre : le refus vient alors de {@link #reevaluer}. Lève l'écriture
     * concurrente de la Planification (409 {@code PLANIFICATION_ECRITURE_CONCURRENTE}) si le verrou n'est pas obtenu en 5 s.
     */
    void verrouillerLesPlaces(CentreId centre, PatientId patient);

    /**
     * Réévalue la place du patient après l'écriture (replacé en isolement s'il est à risque, retour en salle ordinaire seulement
     * proposé). Un patient inconnu du centre est refusé par la Planification (422 {@code PLACEMENT_PATIENT_INTROUVABLE}) : la
     * sérologie est alors annulée, y compris pour un résultat sans risque (reproduit, KB-MED-50).
     */
    void reevaluer(CentreId centre, PatientId patient);
}
