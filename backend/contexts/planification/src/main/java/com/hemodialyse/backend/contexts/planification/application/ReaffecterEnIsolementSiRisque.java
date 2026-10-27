package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.IsolementImpossible;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PatientReplaceEnIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.IssueDeRetour;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.ResultatDeReaffectation;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.RetourEnSalleOrdinaire;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Après l'enregistrement d'une sérologie (RG-PLN-053), dans la <b>même transaction</b> : un patient devenu à risque est replacé en
 * salle d'isolement (ou signalé, une alerte par patient et par jour), un patient redevenu sans risque encore en salle d'isolement
 * reçoit une proposition de retour en salle ordinaire, jamais appliquée. Le patient d'un autre centre est refusé.
 */
public class ReaffecterEnIsolementSiRisque implements ReaffectationEnIsolement {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final PlacesDuCentreRepository places;
    private final LectureDesRessources ressources;
    private final RisquesInfectieux risques;
    private final PatientsDuCentre patients;
    private final NomsDesPatients noms;
    private final NotificationsDuPlacement notifications;
    private final HorlogeDesCentres horloge;

    public ReaffecterEnIsolementSiRisque(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou, PlacesDuCentreRepository places,
                                         LectureDesRessources ressources, RisquesInfectieux risques, PatientsDuCentre patients,
                                         NomsDesPatients noms, NotificationsDuPlacement notifications, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.places = places;
        this.ressources = ressources;
        this.risques = risques;
        this.patients = patients;
        this.noms = noms;
        this.notifications = notifications;
        this.horloge = horloge;
    }

    @Override
    public Issue siRisque(UUID centreId, UUID patientId) {
        CentreId centre = new CentreId(centreId);
        PatientId patient = new PatientId(patientId);
        return RefusDuPlacement.traduire(() -> {
            if (patients.situation(centre, patient) == PatientsDuCentre.Situation.INTROUVABLE) {
                throw new RegleDePlanificationViolee("PLACEMENT_PATIENT_INTROUVABLE", "Patient introuvable dans ce centre");
            }
            return uniteDeTravail.executer(() -> {
                verrou.prendre(centre);
                LocalDate jour = horloge.aujourdhui(centre);
                PlacesDuCentre lues = places.charger(centre, ressources.duCentre(centre, jour), jour);
                places.placementActuel(centre, patient).ifPresent(lues::connaitre);
                CategorieDIsolement categorie = risques.de(centre, patient);
                if (categorie.aRisque()) return reaffecter(lues, patient, categorie, jour);
                return proposerLeRetour(lues, patient, categorie, jour);
            });
        });
    }

    private Issue reaffecter(PlacesDuCentre lues, PatientId patient, CategorieDIsolement categorie, LocalDate jour) {
        ResultatDeReaffectation resultat = lues.replacerEnIsolement(patient, categorie);
        if (!lues.changements().isEmpty()) places.enregistrer(lues);
        CentreId centre = lues.centre();
        for (EvenementDePlacement evenement : lues.evenements()) {
            String nom = nom(centre, patient);
            if (evenement instanceof PatientReplaceEnIsolement replace) {
                notifications.patientReplaceEnIsolement(centre, patient, nom, replace.salle().nom(), jour);
            } else if (evenement instanceof IsolementImpossible) {
                notifications.isolementImpossible(centre, patient, nom, jour);
            }
        }
        return switch (resultat.issue()) {
            case AUCUN_RISQUE -> Issue.AUCUN_RISQUE;
            case NON_PLACE -> Issue.NON_PLACE;
            case DEJA_CONFORME -> Issue.DEJA_CONFORME;
            case DEPLACE -> Issue.DEPLACE;
            case A_REPLANIFIER -> Issue.A_REPLANIFIER;
        };
    }

    private Issue proposerLeRetour(PlacesDuCentre lues, PatientId patient, CategorieDIsolement categorie, LocalDate jour) {
        RetourEnSalleOrdinaire retour = lues.proposerLeRetourEnSalleOrdinaire(patient, categorie);
        if (retour.issue() != IssueDeRetour.PROPOSE) return Issue.AUCUN_RISQUE;
        notifications.retourEnSalleOrdinairePropose(lues.centre(), patient, nom(lues.centre(), patient),
                retour.proposition().salle().nom(), jour);
        return Issue.RETOUR_PROPOSE;
    }

    private String nom(CentreId centre, PatientId patient) {
        return noms.de(centre, List.of(patient)).getOrDefault(patient, "");
    }
}
