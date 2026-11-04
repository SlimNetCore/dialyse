package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Port sortant de lecture : la recherche paginée des attestations du centre, enrichie de l'identité du patient. */
public interface LectureDesAttestations {

    /** Ligne de la recherche ; noms de champs du JSON actuel (snake_case). */
    record Ligne(UUID id, UUID patient_id, LocalDate date_debut, LocalDate date_fin, String code_patient, String nom,
                 String prenom, String numero_assurance) {
    }

    record Page(List<Ligne> items, long total, int page, int size) {
    }

    /**
     * Tri : fin décroissante. Le patient est joint s'il est du MÊME centre que l'attestation ; une attestation orpheline est
     * rendue avec les champs du patient nuls (comme aujourd'hui).
     */
    Page rechercher(CentreId centre, CriteresDAttestation criteres);
}
