package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un candidat donneur vivant (statut {@code CANDIDAT}, crossmatch {@code NON_FAIT}). Patient inconnu ou d'un autre centre :
 * 404 (écart décidé KB-MED-86, avant : donneur orphelin ou croisé entre centres). Nom et lien de parenté obligatoires (422, reproduit),
 * bornes de colonne en 400. Droit : MEDECIN.
 */
public class EnregistrerUnDonneurVivant {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final DonneursVivants donneurs;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public EnregistrerUnDonneurVivant(SessionCourante session, PatientsDuCentre patients, DonneursVivants donneurs, UniteDeTravail uniteDeTravail,
                                      GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.donneurs = donneurs;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public DonneurVivant executer(UUID centreDemande, UUID patientDemande, String nom, String prenom, LocalDate dateNaissance,
                                  LienDeParente lien, String telephone, String groupeSanguin, String typageHla) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            DonneurVivant donneur = DonneurVivant.enregistrer(identifiants.suivant(), patient, centre, nom, prenom, dateNaissance, lien,
                    telephone, groupeSanguin, typageHla, horloge.maintenant());
            donneurs.ajouter(donneur);
            return donneur;
        });
    }
}
