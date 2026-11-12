package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PeriodeDeStatistiques;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Les gardes communes aux statistiques d'un patient, dans l'ordre : le patient est identifie ; le centre de la session (403, KB-PL4-5 : AVANT
 * toute validation, aucune donnee divulguee) ; la periode (400) ; puis, apres les controles propres a chaque cas d'usage, le patient du centre
 * ({@link #exigerLePatient}, 404 pour un inconnu ou d'un autre centre : KB-PL4-2, meme reponse, aucune divulgation inter-centres).
 */
final class GardeDesStatistiques {

    private final SessionCourante session;
    private final PatientsDuCentre patients;

    GardeDesStatistiques(SessionCourante session, PatientsDuCentre patients) {
        this.session = session;
        this.patients = patients;
    }

    Demande ouvrir(UUID centreDemande, UUID patient, LocalDate du, LocalDate au) {
        PatientId identite = new PatientId(patient);
        CentreId centre = session.centre(centreDemande);
        return new Demande(centre, identite, new PeriodeDeStatistiques(du, au));
    }

    void exigerLePatient(Demande demande) {
        if (!patients.existe(demande.centre(), demande.patient())) throw new PatientIntrouvable();
    }

    record Demande(CentreId centre, PatientId patient, PeriodeDeStatistiques periode) {
    }
}
