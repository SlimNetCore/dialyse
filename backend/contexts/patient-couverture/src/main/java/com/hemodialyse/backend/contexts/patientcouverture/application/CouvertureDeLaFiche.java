package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ce que la saisie de la fiche enregistre en plus du patient : l'attestation de droits (slice PC4 ; l'assuré principal relève de
 * {@link PrincipalDeLaFiche}, PC2, et la demande de PEC de {@link PecDeLaFiche}, PC3). Dans la transaction de la fiche, sous le
 * verrou du centre déjà pris. Une attestation de la fiche n'est jamais dupliquée si la même période existe déjà, et une
 * attestation incomplète (début ou fin absent) est ignorée en silence (reproduit, KB-PAT-41).
 */
public class CouvertureDeLaFiche {

    /** Attestation fournie sur la fiche (dates obligatoires toutes deux, sinon ignorée). */
    public record AttestationSaisie(UUID id, LocalDate debut, LocalDate fin) {
        public boolean complete() {
            return debut != null && fin != null;
        }
    }

    /** Demande de prise en charge saisie sur la fiche. */
    public record DemandeDePec(UUID id, LocalDate debut, LocalDate fin, UUID forfaitId) {
        public boolean complete() {
            return debut != null && fin != null;
        }
    }

    private final Attestations attestations;
    private final Identifiants identifiants;
    private final HorlogeDesCentres horloge;

    public CouvertureDeLaFiche(Attestations attestations, Identifiants identifiants, HorlogeDesCentres horloge) {
        this.attestations = attestations;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public boolean attestationEnregistree(CentreId centre, PatientId patient) {
        return attestations.existe(centre, patient);
    }

    /** Enregistre l'attestation de la fiche si elle est complète et que la même période n'existe pas déjà. */
    public void enregistrer(CentreId centre, PatientId patient, AttestationSaisie saisie) {
        if (saisie == null || !saisie.complete()) return;
        boolean dejaLa = attestations.duPatient(centre, patient).stream().anyMatch(a -> a.memePeriode(saisie.debut(), saisie.fin()));
        if (dejaLa) return;
        UUID id = saisie.id() != null ? saisie.id() : identifiants.nouveau();
        attestations.enregistrer(AttestationDroit.saisieSurLaFiche(id, patient, centre, saisie.debut(), saisie.fin(),
                horloge.maintenant()));
    }
}
