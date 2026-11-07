package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionMedicale;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SignalementDesPrescriptions;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Prescrire (créer) ou modifier une prescription médicale d'un patient du centre de la session, puis prévenir le centre. Droit :
 * MEDECIN. Écarts décidés : patient inconnu ou d'un autre centre = 404 à la création (KB-MED-19) ; modifier une prescription
 * inconnue, d'un autre patient ou d'un autre centre = 404, sans création ni réaffectation (KB-MED-17). Reproduit : la dose d'un
 * article connu du centre doit se convertir dans l'unité de stock (422 avec le code du Stock) ; un article inconnu est ignoré sans
 * erreur (KB-MED-32) ; la date absente vaut le jour du centre.
 */
public class PrescrireUnTraitement {

    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Prescriptions prescriptions;
    private final CatalogueDesArticles catalogue;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final SignalementDesPrescriptions signalement;

    public PrescrireUnTraitement(SessionCourante session, PatientsDuCentre patients, Prescriptions prescriptions,
                                 CatalogueDesArticles catalogue, UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants,
                                 HorlogeDuCentre horloge, SignalementDesPrescriptions signalement) {
        this.session = session;
        this.patients = patients;
        this.prescriptions = prescriptions;
        this.catalogue = catalogue;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.signalement = signalement;
    }

    public PrescriptionMedicale creer(UUID centreDemande, UUID patientDemande, ContenuDePrescription saisi) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var contenu = avecLaDate(centre, saisi);
        verifierLesDoses(centre, contenu);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            var prescription = PrescriptionMedicale.prescrire(identifiants.suivant(), patient, centre, contenu, horloge.maintenant());
            prescriptions.ajouter(prescription);
            signalement.prescriptionChangee(centre.valeur(), patient.valeur());
            return prescription;
        });
    }

    public PrescriptionMedicale modifier(UUID centreDemande, UUID patientDemande, UUID id, ContenuDePrescription saisi) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var contenu = avecLaDate(centre, saisi);
        verifierLesDoses(centre, contenu);
        return uniteDeTravail.executer(() -> {
            var prescription = prescriptions.trouverEnVerrouillant(centre, patient, id).orElseThrow(PrescriptionIntrouvable::new);
            prescription.remplacerPar(contenu, horloge.maintenant());
            prescriptions.enregistrerLeRemplacement(prescription);
            signalement.prescriptionChangee(centre.valeur(), patient.valeur());
            return prescription;
        });
    }

    private ContenuDePrescription avecLaDate(CentreId centre, ContenuDePrescription saisi) {
        return saisi.date() != null ? saisi : saisi.aLaDate(horloge.aujourdhui(centre));
    }

    private void verifierLesDoses(CentreId centre, ContenuDePrescription contenu) {
        verifierLaDose(centre, contenu.epo(), "UI");
        verifierLaDose(centre, contenu.fer(), "mg");
    }

    /** Une dose strictement positive sur un article connu du centre doit se convertir ; sinon rien à vérifier (reproduit). */
    private void verifierLaDose(CentreId centre, Traitement traitement, String unite) {
        if (traitement.article() == null || traitement.dose() == null || traitement.dose() <= 0) return;
        if (catalogue.article(centre, traitement.article()).isEmpty()) return;
        catalogue.verifierLaConversionDeLaDose(centre, traitement.article(), traitement.dose(), unite);
    }
}
