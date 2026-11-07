package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant du domaine : les alertes d'observance (table {@code alertes_observance}, inchangée). */
public interface Alertes {

    void ajouter(AlerteDObservance alerte);

    /** Enregistre la résolution (date de résolution) d'une alerte existante. */
    void enregistrerLaResolution(AlerteDObservance alerte);

    /** De la plus récente à la plus ancienne (résolues comprises). */
    List<AlerteDObservance> duPatient(CentreId centre, PatientId patient);

    /** Cherchée par (identifiant, patient, centre), verrouillée jusqu'à la fin de la transaction. */
    Optional<AlerteDObservance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** L'alerte OUVERTE (non résolue) de ce traitement et de cette nature pour le patient, s'il y en a une. */
    Optional<AlerteDObservance> ouverte(CentreId centre, PatientId patient, TypeDeTraitement traitement, TypeDAlerte nature);
}
