package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Lit le bilan pré-greffe du patient du centre de la session. Patient inconnu ou d'un autre centre : 404 {@code PATIENT_INTROUVABLE},
 * aucun bilan créé (écart décidé KB-MED-85 / 86). REPRODUIT (à confirmer avec les métiers et le DPO) : la première lecture d'un patient
 * valide ouvre un bilan {@code NON_DEBUTE} (la lecture écrit), avec les droits actuels (MEDECIN et ADMIN). Lecture non tracée en audit.
 */
public class ConsulterLeBilanPreGreffe {
    private final SessionCourante session;
    private final UniteDeTravail uniteDeTravail;
    private final OuvertureDuBilanPreGreffe ouverture;

    public ConsulterLeBilanPreGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans, UniteDeTravail uniteDeTravail,
                                     GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.uniteDeTravail = uniteDeTravail;
        this.ouverture = new OuvertureDuBilanPreGreffe(patients, bilans, identifiants, horloge);
    }

    public BilanPreGreffe executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> ouverture.lire(centre, patient));
    }
}
