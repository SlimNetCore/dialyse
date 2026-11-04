package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port sortant : les attestations de droits (table {@code attestation_droit}). Une lecture ne rend que celles du centre demandé. */
public interface Attestations {

    Optional<AttestationDroit> trouver(CentreId centre, UUID id);

    void enregistrer(AttestationDroit attestation);

    void supprimer(UUID id);

    /** Les attestations du patient dans le centre, sans ordre garanti. */
    List<AttestationDroit> duPatient(CentreId centre, PatientId patient);

    /** Le patient a-t-il au moins une attestation dans ce centre ? */
    boolean existe(CentreId centre, PatientId patient);

    /** Le patient a-t-il une attestation dont la période couvre ce jour (bornes incluses) ? */
    boolean valideLe(CentreId centre, PatientId patient, LocalDate jour);
}
