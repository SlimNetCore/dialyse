package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Attestations;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.RegleDePatientViolee;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Déclarer, supprimer et lister les attestations de droits (slice PC4, mêmes URL que le legacy). Garde de centre sur chaque
 * appel (centre du jeton, 403 sinon). Les écritures prennent le verrou du centre en tête (ordre global des verrous, ADR 0061) :
 * une attestation supprimée pendant la création d'une PEC est vue ou non vue, jamais à moitié.
 * <p>
 * Décisions du 2026-11-04 (KB-PAT-38 à 42) : un patient inexistant ou d'un autre centre est refusé en 422
 * {@value #PATIENT_INTROUVABLE} ; une suppression d'un identifiant inconnu (ou d'un autre centre, ou d'un autre patient quand
 * le patient est donné) est un 404 sans diffusion. ARGENT reproduit à l'identique, à décider avec les métiers : aucun contrôle de
 * période entre attestations, suppression possible sous une PEC validée.
 */
public class GererLesAttestations {

    public static final String PATIENT_INTROUVABLE = "ATTESTATION_PATIENT_INTROUVABLE";

    private final SessionCourante session;
    private final HorlogeDesCentres horloge;
    private final Identifiants identifiants;
    private final Attestations attestations;
    private final PatientRepository patients;
    private final UniteDeTravail unite;
    private final VerrouDuCentre verrou;

    public GererLesAttestations(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                                Attestations attestations, PatientRepository patients, UniteDeTravail unite,
                                VerrouDuCentre verrou) {
        this.session = session;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.attestations = attestations;
        this.patients = patients;
        this.unite = unite;
        this.verrou = verrou;
    }

    /** Garde de centre seule (les routes qui lisent la liste ou la recherche). */
    public CentreId centre(UUID centreDemande) {
        return session.centre(centreDemande);
    }

    public AttestationDroit declarer(UUID centreDemande, UUID patientDemande, LocalDate debut, LocalDate fin) {
        CentreId centre = session.centre(centreDemande);
        if (patientDemande == null) throw new SaisieInvalide("PatientId cannot be null");
        PatientId patient = new PatientId(patientDemande);
        // forme de la saisie (dates) avant toute prise de verrou
        AttestationDroit attestation = AttestationDroit.declarer(identifiants.nouveau(), patient, centre, debut, fin,
                horloge.maintenant());
        return unite.executer(() -> {
            verrou.prendre(centre);
            if (patients.trouver(centre, patient).isEmpty()) {
                throw new RegleDePatientViolee(PATIENT_INTROUVABLE, "Patient introuvable dans ce centre");
            }
            attestations.enregistrer(attestation);
            return attestation;
        });
    }

    /**
     * @param patientDemande celui de la requête, facultatif : s'il est donné, il doit être celui de l'attestation
     * @return l'attestation supprimée (le patient réel sert à la diffusion)
     */
    public AttestationDroit supprimer(UUID centreDemande, UUID attestationId, UUID patientDemande) {
        CentreId centre = session.centre(centreDemande);
        return unite.executer(() -> {
            verrou.prendre(centre);
            AttestationDroit attestation = attestations.trouver(centre, attestationId).orElseThrow(AttestationIntrouvable::new);
            if (patientDemande != null && !patientDemande.equals(attestation.patient().valeur())) {
                throw new AttestationIntrouvable();
            }
            attestations.supprimer(attestationId);
            return attestation;
        });
    }

    /** Les attestations du patient dans le centre de la session (un patient inconnu a une liste vide, comme aujourd'hui). */
    public List<AttestationDroit> duPatient(UUID centreDemande, UUID patient) {
        return attestations.duPatient(session.centre(centreDemande), new PatientId(patient));
    }
}
