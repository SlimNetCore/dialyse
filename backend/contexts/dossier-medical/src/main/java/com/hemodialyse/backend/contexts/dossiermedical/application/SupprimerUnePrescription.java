package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Prescriptions;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SignalementDesPrescriptions;

import java.util.UUID;

/**
 * Supprime une prescription (portée par identifiant, patient et centre). REPRODUIT (KB-MED-31, à confirmer avec les métiers) : sans
 * correspondance (identifiant inconnu, autre patient, autre centre) rien n'est supprimé, aucune erreur (204), et le centre est
 * tout de même prévenu. Droit : MEDECIN.
 */
public class SupprimerUnePrescription {

    private final SessionCourante session;
    private final Prescriptions prescriptions;
    private final UniteDeTravail uniteDeTravail;
    private final SignalementDesPrescriptions signalement;

    public SupprimerUnePrescription(SessionCourante session, Prescriptions prescriptions, UniteDeTravail uniteDeTravail,
                                    SignalementDesPrescriptions signalement) {
        this.session = session;
        this.prescriptions = prescriptions;
        this.uniteDeTravail = uniteDeTravail;
        this.signalement = signalement;
    }

    public void executer(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        uniteDeTravail.executer(() -> {
            prescriptions.supprimer(centre, new PatientId(patient), id);
            signalement.prescriptionChangee(centre.valeur(), patient);
            return null;
        });
    }
}
