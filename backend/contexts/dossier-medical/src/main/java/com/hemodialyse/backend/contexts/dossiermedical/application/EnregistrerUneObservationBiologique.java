package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre une observation biologique pour un patient du centre de la session. Ordre des contrôles : centre de la session (403),
 * système de codage, code (422), unité de la valeur numérique (422), statut (400), puis dans la transaction : patient du centre (404
 * {@code PATIENT_INTROUVABLE}, écart décidé KB-MED-73 : le legacy créait une ligne orpheline), règles de l'observation (422) et bornes
 * de colonne (400, KB-MED-72). Reproduit (à confirmer avec les métiers et le DPO) : aucune règle de valeur (KB-MED-79) ; la demande
 * d'examen est une référence souple non contrôlée à l'écriture (KB-MED-77) et la saisie ne change jamais son statut (KB-MED-37) ; deux
 * envois identiques créent deux observations. Aucun événement. Droit : MEDECIN (porté par l'interface).
 */
public class EnregistrerUneObservationBiologique {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final ObservationsBiologiques observations;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUneObservationBiologique(SessionCourante session, PatientsDuCentre patients, ObservationsBiologiques observations,
                                               UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.observations = observations;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public ObservationBiologique executer(UUID centreDemande, UUID patientDemande, UUID demandeDExamen, String systemeSaisi, String codeSaisi,
                                          String affichageSaisi, BigDecimal valeurNumerique, String unite, String valeurTexte,
                                          LocalDate datePrelevement, String statutSaisi) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        CodeClinique analyte = CodeClinique.saisir(SystemeDeCodage.lire(systemeSaisi), codeSaisi, affichageSaisi);
        ValeurMesuree valeur = valeurNumerique == null ? null : new ValeurMesuree(valeurNumerique, unite);
        var statut = StatutDObservation.lireFacultatif(statutSaisi);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            var observation = ObservationBiologique.enregistrer(identifiants.suivant(), patient, centre, demandeDExamen, analyte, valeur,
                    valeurTexte, datePrelevement, statut, horloge.aujourdhui(centre), horloge.maintenant());
            observations.ajouter(observation);
            return observation;
        });
    }
}
