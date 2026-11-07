package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanPreGreffe;

import java.util.UUID;

/**
 * Change le statut du bilan pré-greffe selon la machine à états (422 {@code BILAN_GREFFE_TRANSITION_INVALIDE}, reproduite). Le bilan est
 * verrouillé : une écriture simultanée attend 5 s puis 409 {@code BILAN_PRE_GREFFE_ECRITURE_CONCURRENTE} (KB-MED-91). Patient inconnu ou
 * d'un autre centre : 404 (KB-MED-86). Droit : MEDECIN.
 */
public class ChangerLeStatutDuBilanPreGreffe {
    private final SessionCourante session;
    private final BilansPreGreffe bilans;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;
    private final OuvertureDuBilanPreGreffe ouverture;

    public ChangerLeStatutDuBilanPreGreffe(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                           UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.bilans = bilans;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.ouverture = new OuvertureDuBilanPreGreffe(patients, bilans, identifiants, horloge);
    }

    public BilanPreGreffe executer(UUID centreDemande, UUID patientDemande, StatutDuBilanPreGreffe statut) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            BilanPreGreffe bilan = ouverture.verrouiller(centre, patient);
            bilan.changerLeStatut(statut, horloge.aujourdhui(centre), horloge.maintenant());
            bilans.enregistrer(bilan);
            return bilan;
        });
    }
}
