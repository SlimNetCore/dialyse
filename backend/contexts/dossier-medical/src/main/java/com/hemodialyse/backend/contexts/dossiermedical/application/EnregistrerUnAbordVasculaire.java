package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un abord vasculaire pour un patient du centre de la session. Ordre des contrôles : centre de la session (403), puis dans la
 * transaction : patient du centre (404 {@code PATIENT_INTROUVABLE}, écart décidé KB-MED-61 : le legacy créait une ligne orpheline), date de
 * fin (422), type et bornes (400). Le type est un texte libre (reproduit, KB-MED-63 : la Planification n'exige la compétence CATHETER que
 * pour {@code KT_TUNNELISE} et {@code KT_AIGU}). Droit : MEDECIN (porté par l'interface). Aucun événement, aucune ligne d'audit propre
 * (KB-MED-68).
 */
public class EnregistrerUnAbordVasculaire {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final AbordsVasculaires abords;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUnAbordVasculaire(SessionCourante session, PatientsDuCentre patients, AbordsVasculaires abords,
                                        UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.abords = abords;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public AbordVasculaire executer(UUID centreDemande, UUID patientDemande, String type, String cote, String localisation, LocalDate creation,
                                    LocalDate fin, Boolean actif, String complications) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            var abord = AbordVasculaire.enregistrer(identifiants.suivant(), patient, centre, type, cote, localisation, creation, fin, actif,
                    complications, horloge.maintenant());
            abords.ajouter(abord);
            return abord;
        });
    }
}
