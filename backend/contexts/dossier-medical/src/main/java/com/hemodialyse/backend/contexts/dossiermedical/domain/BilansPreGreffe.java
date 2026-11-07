package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/**
 * Port sortant du domaine : les bilans pré-greffe (tables {@code bilans_pre_greffe} et {@code decisions_rcp}, inchangées, sans clé
 * unique ni clé étrangère). Un doublon existant en base (deux lignes pour un patient, KB-MED-90) est lu sans erreur : le plus ancien
 * est retenu. Ordre global des verrous : centre (Planification), fiches (cette table), puis {@code app_user}, puis verrous consultatifs.
 */
public interface BilansPreGreffe {
    /** Lit le bilan du patient sans verrou (le plus ancien s'il y en a plusieurs). */
    Optional<BilanPreGreffe> trouver(CentreId centre, PatientId patient);

    /** Lit le bilan avec verrou de ligne jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUnBilanPreGreffe}. */
    Optional<BilanPreGreffe> trouverEnVerrouillant(CentreId centre, PatientId patient);

    /** Verrou d'ouverture du bilan du patient (un seul bilan par patient), tenu jusqu'à la fin de la transaction. */
    void verrouillerLOuverture(CentreId centre, PatientId patient);

    void ouvrir(BilanPreGreffe bilan);

    /** Enregistre le bilan et ses décisions (les décisions sont réécrites avec leurs identifiants). */
    void enregistrer(BilanPreGreffe bilan);
}
