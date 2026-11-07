package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre une analyse (bilan à colonnes fixes) pour un patient du centre de la session : patient du centre dans la transaction (404
 * {@code PATIENT_INTROUVABLE}, écart décidé KB-MED-73 : le legacy créait un bilan orphelin), puis bornes de colonne (400, KB-MED-72).
 * Date absente : le jour de l'horloge. Reproduit (à confirmer avec les métiers et le DPO) : aucune règle de valeur ni de date (KB-MED-79),
 * un corps vide donne un bilan vide daté du jour, plusieurs bilans le même jour sont permis. Aucun événement. Droit : MEDECIN.
 */
public class EnregistrerUneAnalyse {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Analyses analyses;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUneAnalyse(SessionCourante session, PatientsDuCentre patients, Analyses analyses, UniteDeTravail uniteDeTravail,
                                 GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.analyses = analyses;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Analyse executer(UUID centreDemande, UUID patientDemande, LocalDate datePrelevement, MesuresDuBilan mesures) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            var analyse = Analyse.enregistrer(identifiants.suivant(), patient, centre, datePrelevement, mesures, horloge.aujourdhui(centre),
                    horloge.maintenant());
            analyses.ajouter(analyse);
            return analyse;
        });
    }
}
