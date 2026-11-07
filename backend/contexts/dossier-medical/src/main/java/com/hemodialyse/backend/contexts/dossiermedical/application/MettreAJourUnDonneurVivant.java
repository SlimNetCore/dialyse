package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivantIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LienDeParente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatDeCrossmatch;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDuBilanDuDonneur;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplace tout le dossier d'un donneur vivant (champ absent effacé, REPRODUIT). Donneur inconnu, d'un autre patient ou d'un autre
 * centre : 404 {@code DONNEUR_VIVANT_INTROUVABLE} (écart décidé KB-MED-88, avant : 400). Donneur verrouillé : 409 en cas d'écriture
 * simultanée. Droit : MEDECIN.
 */
public class MettreAJourUnDonneurVivant {
    private final SessionCourante session;
    private final DonneursVivants donneurs;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public MettreAJourUnDonneurVivant(SessionCourante session, DonneursVivants donneurs, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.donneurs = donneurs;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public DonneurVivant executer(UUID centreDemande, UUID patientDemande, UUID id, String nom, String prenom, LocalDate dateNaissance,
                                  LienDeParente lien, String telephone, String groupeSanguin, String typageHla, StatutDuBilanDuDonneur statut,
                                  ResultatDeCrossmatch crossmatch, LocalDate dateCrossmatch, String bilanRealise, String contreIndications,
                                  String decisionFinale, LocalDate dateDecision) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            DonneurVivant donneur = donneurs.trouverEnVerrouillant(centre, patient, id).orElseThrow(DonneurVivantIntrouvable::new);
            donneur.mettreAJour(nom, prenom, dateNaissance, lien, telephone, groupeSanguin, typageHla, statut, crossmatch, dateCrossmatch,
                    bilanRealise, contreIndications, decisionFinale, dateDecision, horloge.maintenant());
            donneurs.enregistrer(donneur);
            return donneur;
        });
    }
}
