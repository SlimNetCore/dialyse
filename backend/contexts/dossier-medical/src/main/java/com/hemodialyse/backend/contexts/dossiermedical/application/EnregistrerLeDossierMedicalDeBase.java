package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre le dossier médical de base du patient (POST et PUT sont la même intention : le premier enregistrement crée le dossier,
 * les suivants le remplacent en entier, l'identifiant et la création sont conservés). Écarts décidés (ADR 0087) : patient inconnu ou
 * d'un autre centre 404 (KB-MED-98, avant : dossier orphelin ou croisé entre centres) ; écriture simultanée ou dossier déjà tenu pour ce
 * patient dans un autre centre : 409 (avant : 500) ; colonne trop longue : 400 (KB-MED-99). Droit : MEDECIN.
 */
public class EnregistrerLeDossierMedicalDeBase {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final DossiersMedicauxDeBase dossiers;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerLeDossierMedicalDeBase(SessionCourante session, PatientsDuCentre patients, DossiersMedicauxDeBase dossiers,
                                             UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.dossiers = dossiers;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public DossierMedicalDeBase executer(UUID centreDemande, UUID patientDemande, String nephropathieInitiale, LocalDate dateMiseEnDialyse,
                                         String hepatiteBStatut, String hepatiteCStatut, String observationGlobale, String conclusionMedicale) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            dossiers.verrouillerLEnregistrement(patient);
            var existant = dossiers.duPatientEnVerrouillant(centre, patient);
            if (existant.isPresent()) {
                DossierMedicalDeBase dossier = existant.get();
                dossier.remplacer(nephropathieInitiale, dateMiseEnDialyse, hepatiteBStatut, hepatiteCStatut, observationGlobale,
                        conclusionMedicale, horloge.maintenant());
                dossiers.enregistrer(dossier);
                return dossier;
            }
            DossierMedicalDeBase dossier = DossierMedicalDeBase.ouvrir(identifiants.suivant(), patient, centre, nephropathieInitiale,
                    dateMiseEnDialyse, hepatiteBStatut, hepatiteCStatut, observationGlobale, conclusionMedicale, horloge.maintenant());
            dossiers.ajouter(dossier);
            return dossier;
        });
    }
}
