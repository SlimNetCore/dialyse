package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Admet un patient (création de la fiche). Ordre : garde de centre, contrôle de la place sous le verrou du centre,
 * unicité du numéro d'assurance, admission, enregistrement, place, couverture.
 */
public class AdmettreUnPatient {

    private final SessionCourante session;
    private final HorlogeDesCentres horloge;
    private final Identifiants identifiants;
    private final PlaceDeLaFiche place;
    private final PatientRepository patients;
    private final MouvementsDesPatients mouvements;
    private final CouvertureDeLaFiche couverture;
    private final UniteDeTravail unite;
    private final VerrouDuCentre verrou;

    public AdmettreUnPatient(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                             PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                             CouvertureDeLaFiche couverture, UniteDeTravail unite, VerrouDuCentre verrou) {
        this.verrou = verrou;
        this.session = session;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.place = place;
        this.patients = patients;
        this.mouvements = mouvements;
        this.couverture = couverture;
        this.unite = unite;
    }

    public PatientEnregistre executer(SaisieDeLaFiche saisie) {
        CentreId centre = session.centre(saisie.centreDemande());
        return unite.executer(() -> {
            // verrou du centre EN TÊTE (ordre global des verrous, unicité du numéro), même sans place
            verrou.prendre(centre);
            var fiche = saisie.fiche();
            place.verifier(centre, null, fiche.affectation(), fiche.etat(), fiche.dateEvenement());
            Patient.verifierNumeroLibre(patients.trouverLeDetenteurDuNumero(centre, fiche.couverture().numeroAssurance()),
                    java.util.Optional.empty());
            Patient patient = Patient.admettre(new PatientId(identifiants.nouveau()), identifiants.nouveauCodePatient(), centre,
                    fiche, horloge.aujourdhui(centre), horloge.maintenant(), identifiants::nouveau);
            patient.verifierAttestation(saisie.attestation() != null && saisie.attestation().complete(), false);
            patients.enregistrer(patient);
            patient.retirerLesMouvements().forEach(mouvements::ajouter);
            place.placer(centre, patient.id(), fiche.affectation(), patient.etat(), fiche.dateEvenement());
            couverture.enregistrer(centre, patient.id(), patient.fiche().couverture(), saisie.attestation(), saisie.demande());
            return new PatientEnregistre(patient.id(), centre, patient.vacancier(), patient.code());
        });
    }
}
