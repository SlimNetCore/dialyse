package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * Règle commune aux cas d'usage du bilan : le patient doit exister dans le centre (404, écart décidé KB-MED-85 / 86), puis le bilan du
 * patient est ouvert à la première utilisation (création paresseuse REPRODUITE du legacy, droits inchangés), sous un verrou d'ouverture
 * qui garantit un seul bilan par patient (KB-MED-90). À appeler dans l'unité de travail.
 */
final class OuvertureDuBilanPreGreffe {
    private final PatientsDuCentre patients;
    private final BilansPreGreffe bilans;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    OuvertureDuBilanPreGreffe(PatientsDuCentre patients, BilansPreGreffe bilans, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.patients = patients;
        this.bilans = bilans;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    void exigerLePatient(CentreId centre, PatientId patient) {
        if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
    }

    /** Le bilan du patient, lu sans verrou ; ouvert s'il n'existe pas. */
    BilanPreGreffe lire(CentreId centre, PatientId patient) {
        exigerLePatient(centre, patient);
        return bilans.trouver(centre, patient).orElseGet(() -> ouvrir(centre, patient, false));
    }

    /** Le bilan du patient, verrouillé jusqu'à la fin de la transaction ; ouvert s'il n'existe pas. */
    BilanPreGreffe verrouiller(CentreId centre, PatientId patient) {
        exigerLePatient(centre, patient);
        return bilans.trouverEnVerrouillant(centre, patient).orElseGet(() -> ouvrir(centre, patient, true));
    }

    private BilanPreGreffe ouvrir(CentreId centre, PatientId patient, boolean verrouille) {
        bilans.verrouillerLOuverture(centre, patient);
        var existant = verrouille ? bilans.trouverEnVerrouillant(centre, patient) : bilans.trouver(centre, patient);
        return existant.orElseGet(() -> {
            BilanPreGreffe nouveau = BilanPreGreffe.ouvrir(identifiants.suivant(), patient, centre, horloge.maintenant());
            bilans.ouvrir(nouveau);
            return nouveau;
        });
    }
}
