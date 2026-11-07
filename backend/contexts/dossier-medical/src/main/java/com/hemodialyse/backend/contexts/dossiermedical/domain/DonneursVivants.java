package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant du domaine : les donneurs vivants (table {@code donneurs_vivants}, inchangée). Toujours cherchés par (identifiant, patient,
 * centre). Une ligne dont le lien de parenté, le statut ou le crossmatch est illisible en base est ignorée par la lecture (WARN avec son
 * identifiant seul).
 */
public interface DonneursVivants {
    /** Donneurs du patient, du plus récemment créé au plus ancien (départage : identifiant). */
    List<DonneurVivant> duPatient(CentreId centre, PatientId patient);

    void ajouter(DonneurVivant donneur);

    void enregistrer(DonneurVivant donneur);

    /** Lecture avec verrou de ligne. Lève {@link EcritureConcurrenteSurUnDonneurVivant}. */
    Optional<DonneurVivant> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime le donneur (lisible ou non) ; faux s'il n'existait pas. */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);
}
