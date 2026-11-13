package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant de l'annulation d'un lot de reprise (RP1, ADR 0169) sur {@code patients}, {@code assure}, {@code assure_patient},
 * {@code prise_en_charge} et {@code attestation_droit}. Les lectures reprennent celles de l'outil de migration d'origine ; les
 * suppressions sont bornées au centre.
 */
public interface DonneesDeLaReprise {

    record AffectationDeLAssure(UUID id, UUID patient) {
    }

    List<UUID> prisesEnChargeDuPatient(UUID patient);

    List<UUID> attestationsDuPatient(UUID patient);

    List<AffectationDeLAssure> affectationsDeLAssure(UUID centre, String numeroAssurance);

    /** Patients du centre dont la fiche pointe cet assuré. */
    List<UUID> patientsDeLAssure(UUID centre, String numeroAssurance);

    void supprimerLAffectation(UUID centre, UUID affectation);

    void supprimerLesAffectationsDuPatient(UUID centre, UUID patient);

    void supprimerLePatient(UUID centre, UUID patient);

    void supprimerLAssure(UUID centre, String numeroAssurance);

    void supprimerUneAttestation(UUID centre, UUID attestation);

    void supprimerUnePriseEnCharge(UUID centre, UUID priseEnCharge);
}
