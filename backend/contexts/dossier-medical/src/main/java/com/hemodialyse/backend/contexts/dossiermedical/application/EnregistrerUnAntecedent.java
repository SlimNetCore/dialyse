package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAntecedent;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un antécédent pour un patient du centre de la session. Ordre des contrôles : centre de la session (403), type puis code
 * (400 / 422 de forme), puis sous verrou : patient du centre (404 {@code PATIENT_INTROUVABLE}, écart décidé KB-MED-61 : le legacy créait
 * une ligne orpheline), diagnostic actif déjà présent (422 {@code BUSINESS_RULE_VIOLATION}, reproduit), règles de l'antécédent. Un
 * libellé libre n'est jamais contrôlé pour les doublons (reproduit, KB-MED-64). Droit : MEDECIN (porté par l'interface). Aucun événement
 * publié (KB-MED-67), aucune ligne d'audit propre (KB-MED-68).
 */
public class EnregistrerUnAntecedent {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Antecedents antecedents;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUnAntecedent(SessionCourante session, PatientsDuCentre patients, Antecedents antecedents,
                                   UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.antecedents = antecedents;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Antecedent executer(UUID centreDemande, UUID patientDemande, String typeSaisi, String systemeSaisi, String codeSaisi,
                               String affichageSaisi, String libelleLibre, LocalDate debut, LocalDate fin, String severite, String note) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var type = TypeDAntecedent.lire(typeSaisi);
        CodeClinique diagnostic = codeSaisi == null || codeSaisi.isBlank() ? null
                : CodeClinique.saisir(SystemeDeCodage.lire(systemeSaisi), codeSaisi, affichageSaisi);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            if (diagnostic != null && antecedents.unDiagnosticActifExisteEnVerrouillant(centre, patient, diagnostic, null)) {
                throw new RegleDeGestionViolee("BUSINESS_RULE_VIOLATION", "Un antécédent actif avec ce diagnostic existe déjà pour ce patient");
            }
            var antecedent = Antecedent.enregistrer(identifiants.suivant(), patient, centre, type, diagnostic, libelleLibre, debut, fin,
                    severite, note, horloge.aujourdhui(centre), horloge.maintenant());
            antecedents.ajouter(antecedent);
            return antecedent;
        });
    }
}
