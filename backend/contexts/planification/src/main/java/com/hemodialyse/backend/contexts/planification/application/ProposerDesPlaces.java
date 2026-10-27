package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Alerte;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.CaseDeGrille;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.DemandeDePlace;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Proposition;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Resultat;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Aide au placement (RG-PLN-030 à 036) : propose les meilleures places et donne la grille de disponibilité du centre. Lecture
 * seule, aucun verrou, aucun cache. Le placement actuel du patient à modifier est libéré pour le calcul ; le risque est celui
 * du patient (sérologies) sauf s'il est forcé par l'appelant.
 */
public class ProposerDesPlaces {

    private final UniteDeTravail uniteDeTravail;
    private final PlacesDuCentreRepository places;
    private final LectureDesRessources ressources;
    private final RisquesInfectieux risques;
    private final HorlogeDesCentres horloge;

    public ProposerDesPlaces(UniteDeTravail uniteDeTravail, PlacesDuCentreRepository places, LectureDesRessources ressources,
                             RisquesInfectieux risques, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.places = places;
        this.ressources = ressources;
        this.risques = risques;
        this.horloge = horloge;
    }

    /**
     * @param patientAIgnorer patient dont on modifie la place ; nul sinon
     * @param isolementForce  nul : risque déduit des sérologies du patient (sans risque s'il est inconnu)
     */
    public record Commande(CentreId centre, PatientId patientAIgnorer, int seances, Set<JourDeLaSemaine> jours,
                           CreneauId creneau, SalleId salle, int limite, Boolean isolementForce) {
    }

    public record Reponse(RessourcesDuCentre ressources, List<Proposition> propositions, List<CaseDeGrille> grille,
                          List<Alerte> alertes, boolean patientARisque) {
    }

    public Reponse executer(Commande commande) {
        return uniteDeTravail.executer(() -> {
            LocalDate jour = horloge.aujourdhui(commande.centre());
            RessourcesDuCentre lues = ressources.duCentre(commande.centre(), jour);
            PlacesDuCentre toutes = places.charger(commande.centre(), lues, jour);
            PlacesDuCentre sansLePatient = commande.patientAIgnorer() == null ? toutes : toutes.sans(commande.patientAIgnorer());
            CategorieDIsolement categorie = commande.isolementForce() != null
                    ? CategorieDIsolement.deduite(commande.isolementForce())
                    : commande.patientAIgnorer() == null ? CategorieDIsolement.SANS_RISQUE
                    : risques.de(commande.centre(), commande.patientAIgnorer());
            Resultat resultat = sansLePatient.proposer(new DemandeDePlace(commande.seances(), commande.jours(),
                    commande.creneau(), commande.salle(), categorie), commande.limite());
            return new Reponse(lues, resultat.propositions(), sansLePatient.grille(), resultat.alertes(), categorie.aRisque());
        });
    }
}
