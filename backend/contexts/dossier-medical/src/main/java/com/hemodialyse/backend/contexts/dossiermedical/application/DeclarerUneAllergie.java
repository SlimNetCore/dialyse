package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeVerification;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeReaction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Déclare une allergie pour un patient du centre de la session. Ordre des contrôles : centre de la session (403), substance (système puis
 * code), catégorie, criticité, type de réaction, statut (400 / 422 de forme), puis dans la transaction : patient du centre (404
 * {@code PATIENT_INTROUVABLE}, écart décidé KB-MED-61 : le legacy créait une ligne orpheline), règles de l'allergie. Aucune unicité : la
 * même substance peut être déclarée plusieurs fois (reproduit, KB-MED-66). Droit : MEDECIN (porté par l'interface). Aucun événement
 * publié, même pour une allergie critique (KB-MED-67), aucune ligne d'audit propre (KB-MED-68).
 */
public class DeclarerUneAllergie {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Allergies allergies;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public DeclarerUneAllergie(SessionCourante session, PatientsDuCentre patients, Allergies allergies, UniteDeTravail uniteDeTravail,
                               GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.allergies = allergies;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Allergie executer(UUID centreDemande, UUID patientDemande, String systemeSaisi, String codeSaisi, String affichageSaisi,
                             String categorieSaisie, String criticiteSaisie, String reactionSaisie, String manifestations,
                             LocalDate dateDeConstatation, String statutSaisi) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        CodeClinique substance = CodeClinique.saisir(SystemeDeCodage.lire(systemeSaisi), codeSaisi, affichageSaisi);
        var categorie = CategorieDAllergie.lire(categorieSaisie);
        var criticite = CriticiteDAllergie.lire(criticiteSaisie);
        var reaction = TypeDeReaction.lire(reactionSaisie);
        var statut = StatutDeVerification.lireFacultatif(statutSaisi);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            var allergie = Allergie.declarer(identifiants.suivant(), patient, centre, substance, categorie, criticite, reaction, manifestations,
                    dateDeConstatation, statut, horloge.aujourdhui(centre), horloge.maintenant());
            allergies.ajouter(allergie);
            return allergie;
        });
    }
}
