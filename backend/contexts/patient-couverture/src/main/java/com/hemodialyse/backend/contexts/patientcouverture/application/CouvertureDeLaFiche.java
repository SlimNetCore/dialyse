package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port sortant : ce que la saisie de la fiche enregistre en plus du patient (attestation de droits, prise en charge, assuré
 * principal) et dont les règles sont celles des slices PC2 (assuré), PC3 (prise en charge) et PC4 (attestation). Tant que ces
 * slices ne sont pas reprises, l'adapter est une ACL vers le comportement actuel (tables {@code attestation_droit},
 * {@code prise_en_charge}, {@code assure}, {@code assure_patient}).
 */
public interface CouvertureDeLaFiche {

    /** Attestation fournie sur la fiche (dates obligatoires toutes deux, sinon ignorée). */
    record AttestationSaisie(UUID id, LocalDate debut, LocalDate fin) {
        public boolean complete() {
            return debut != null && fin != null;
        }
    }

    /** Demande de prise en charge saisie sur la fiche. */
    record DemandeDePec(UUID id, LocalDate debut, LocalDate fin, UUID forfaitId) {
        public boolean complete() {
            return debut != null && fin != null;
        }
    }

    boolean attestationEnregistree(CentreId centre, PatientId patient);

    /** Assuré principal actuel d'un patient assuré par un tiers (sert à rendre la fiche), s'il y en a un. */
    java.util.Optional<Couverture.AssureSurLaFiche> assurePrincipal(CentreId centre, PatientId patient);

    /**
     * Enregistre l'attestation (jamais dupliquée), la prise en charge (jamais ramenée à « créée ») et l'assuré principal de la
     * fiche.
     */
    void enregistrer(CentreId centre, PatientId patient, Couverture couverture, AttestationSaisie attestation,
                     DemandeDePec demande);
}
