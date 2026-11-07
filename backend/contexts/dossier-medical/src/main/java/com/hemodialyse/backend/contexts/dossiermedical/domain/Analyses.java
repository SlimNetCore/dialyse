package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les analyses (table {@code resultats_analyses}, inchangée, sans contrainte d'unicité ni clé étrangère).
 * Toujours cherchées par (identifiant, patient, centre). Ordre global des verrous : centre (Planification), fiches (cette table), puis
 * {@code app_user}, puis verrous consultatifs.
 */
public interface Analyses {
    void ajouter(Analyse analyse);

    /** Enregistre le remplacement (date, mesures, dates de création et de modification). */
    void enregistrerLeRemplacement(Analyse analyse);

    /** Lecture avec verrou jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUneAnalyse}. */
    Optional<Analyse> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'analyse ; faux si elle n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /** Analyses du patient, de la date de prélèvement la plus récente à la plus ancienne (départage : création puis identifiant). */
    PageDeFiches<Analyse> duPatient(CentreId centre, PatientId patient, Periode periode, int page, int taille);
}
