package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementsDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;
import java.util.UUID;

/**
 * Modifie la fiche d'un patient du centre de la session (404 s'il n'en fait pas partie). Même ordre que l'admission : garde de
 * centre, contrôle de la place sous le verrou du centre, puis relecture du patient, unicité du numéro (aussi à la modification,
 * décision du 02/11), modification, mouvement éventuel, place, libération si échue, couverture.
 */
public class ModifierLaFiche {

    private final SessionCourante session;
    private final HorlogeDesCentres horloge;
    private final Identifiants identifiants;
    private final PlaceDeLaFiche place;
    private final PatientRepository patients;
    private final MouvementsDesPatients mouvements;
    private final CouvertureDeLaFiche couverture;
    private final UniteDeTravail unite;

    public ModifierLaFiche(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants,
                           PlaceDeLaFiche place, PatientRepository patients, MouvementsDesPatients mouvements,
                           CouvertureDeLaFiche couverture, UniteDeTravail unite) {
        this.session = session;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.place = place;
        this.patients = patients;
        this.mouvements = mouvements;
        this.couverture = couverture;
        this.unite = unite;
    }

    public PatientEnregistre executer(UUID patientDemande, SaisieDeLaFiche saisie) {
        CentreId centre = session.centre(saisie.centreDemande());
        PatientId id = new PatientId(patientDemande);
        return unite.executer(() -> {
            var fiche = saisie.fiche();
            place.verifier(centre, id, fiche.affectation(), fiche.etat(), fiche.dateEvenement());
            Patient patient = patients.trouver(centre, id).orElseThrow(PatientIntrouvable::new);
            Patient.verifierNumeroLibre(patients.trouverLeDetenteurDuNumero(centre, fiche.couverture().numeroAssurance()),
                    Optional.of(id));
            patient.modifier(fiche, horloge.aujourdhui(centre), horloge.maintenant(), identifiants::nouveau);
            patient.verifierAttestation(saisie.attestation() != null && saisie.attestation().complete(),
                    couverture.attestationEnregistree(centre, id));
            patients.enregistrer(patient);
            patient.retirerLesMouvements().forEach(mouvements::ajouter);
            place.placer(centre, id, patient.fiche().affectation(), patient.etat(), patient.fiche().dateEvenement());
            place.libererSiEchue(centre, id);
            couverture.enregistrer(centre, id, patient.fiche().couverture(), saisie.attestation(), saisie.demande());
            return new PatientEnregistre(id, centre, patient.vacancier(), patient.code());
        });
    }
}
