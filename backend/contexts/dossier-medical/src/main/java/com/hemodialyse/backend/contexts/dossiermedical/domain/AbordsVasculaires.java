package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les abords vasculaires des patients (table {@code abords_vasculaires}, inchangée, lue aussi par la
 * Planification pour la compétence CATHETER). Toujours cherchés par (identifiant, patient, centre) : jamais d'enregistrement par
 * identifiant seul (KB-MED-62). Ordre global des verrous : centre (Planification), fiches (cette table), puis {@code app_user}, puis
 * verrous consultatifs.
 */
public interface AbordsVasculaires {
    void ajouter(AbordVasculaire abord);

    /** Enregistre le remplacement de l'abord (tous les champs et la date d'enregistrement ; jamais le patient ni le centre). */
    void enregistrerLeRemplacement(AbordVasculaire abord);

    /** Lecture avec verrou jusqu'à la fin de la transaction. Lève {@link EcritureConcurrenteSurUnAbordVasculaire}. */
    Optional<AbordVasculaire> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'abord ; faux s'il n'existait pas (rien n'a été supprimé). */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);

    /**
     * Abords du patient, de la date de création la plus récente à la plus ancienne ; les abords SANS date en dernier sur tous les SGBD
     * (départage : enregistrement puis identifiant). PostgreSQL les plaçait en tête (KB-MED-71) : écart décidé pour un ordre stable.
     */
    PageDeFiches<AbordVasculaire> duPatient(CentreId centre, PatientId patient, int page, int taille);
}
