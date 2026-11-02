package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;

import java.util.UUID;

/**
 * Consulte la fiche d'un patient du centre de la session (garde de centre sur toute lecture, 404 si inconnu). Pour un patient
 * assuré par un tiers, l'assuré rendu est l'assuré principal actuel.
 */
public class ConsulterLaFiche {

    private final SessionCourante session;
    private final PatientRepository patients;
    private final CouvertureDeLaFiche couverture;

    public ConsulterLaFiche(SessionCourante session, PatientRepository patients, CouvertureDeLaFiche couverture) {
        this.session = session;
        this.patients = patients;
        this.couverture = couverture;
    }

    public FicheDuPatient executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        PatientId id = new PatientId(patientDemande);
        Patient patient = patients.trouver(centre, id).orElseThrow(PatientIntrouvable::new);
        Fiche fiche = patient.fiche();
        Couverture c = fiche.couverture();
        if (!c.assureLuiMeme()) {
            var principal = couverture.assurePrincipal(centre, id);
            if (principal.isPresent()) {
                c = new Couverture(c.numeroAssurance(), c.qualiteAssure(), c.centrePayeurId(), c.assureNumeroAssurance(),
                        principal.get(), c.assureHistoriqueJson(), c.piecesJointesJson());
                fiche = new Fiche(fiche.identite(), fiche.coordonnees(), fiche.suivi(), c, fiche.affectation(),
                        fiche.dateAdmission(), fiche.etat(), fiche.dateEvenement());
            }
        }
        return new FicheDuPatient(patient.id(), patient.centre(), patient.code(), patient.vacancier(), patient.creeLe(), fiche);
    }
}
