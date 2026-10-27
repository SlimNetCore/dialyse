package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacementDesPatients;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Place un patient (ou lui retire sa place) depuis sa fiche (RG-PLN-050 à 052) : verrou du centre, ressources et places lues
 * sous le verrou, règles du domaine, écriture des seules colonnes de place. S'exécute dans la transaction de l'appelant
 * (UniteDeTravail rejoint). Une fiche sans place est acceptée sans verrou lors du contrôle ; une place inchangée n'est jamais
 * contrôlée.
 */
public class PlacerLePatient implements PlacementDesPatients {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final PlacesDuCentreRepository places;
    private final LectureDesRessources ressources;
    private final RisquesInfectieux risques;
    private final HorlogeDesCentres horloge;

    public PlacerLePatient(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou, PlacesDuCentreRepository places,
                           LectureDesRessources ressources, RisquesInfectieux risques, HorlogeDesCentres horloge) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.places = places;
        this.ressources = ressources;
        this.risques = risques;
        this.horloge = horloge;
    }

    @Override
    public void verifier(UUID centre, UUID patient, UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours) {
        RefusDuPlacement.traduire(() -> {
            Optional<Place> demandee = demandee(salle, creneau, generateur, jours);
            if (demandee.isEmpty()) return null;
            traiter(new CentreId(centre), patient == null ? null : new PatientId(patient), demandee, false);
            return null;
        });
    }

    @Override
    public void placer(UUID centre, UUID patient, UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours) {
        RefusDuPlacement.traduire(() -> {
            traiter(new CentreId(centre), new PatientId(patient), demandee(salle, creneau, generateur, jours), true);
            return null;
        });
    }

    private static Optional<Place> demandee(UUID salle, UUID creneau, UUID generateur, Set<DayOfWeek> jours) {
        Set<JourDeLaSemaine> joursDemandes = EnumSet.noneOf(JourDeLaSemaine.class);
        if (jours != null) jours.forEach(j -> joursDemandes.add(JourDeLaSemaine.de(j)));
        return Place.demandee(salle == null ? null : new SalleId(salle), creneau == null ? null : new CreneauId(creneau),
                generateur == null ? null : new GenerateurId(generateur), joursDemandes);
    }

    private void traiter(CentreId centre, PatientId patient, Optional<Place> demandee, boolean ecrire) {
        uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            LocalDate jour = horloge.aujourdhui(centre);
            PlacesDuCentre lues = places.charger(centre, ressources.duCentre(centre, jour), jour);
            CategorieDIsolement categorie = CategorieDIsolement.SANS_RISQUE;
            if (patient != null) {
                lues.connaitre(places.placementActuel(centre, patient).orElseThrow(() -> new RegleDePlanificationViolee(
                        "PLACEMENT_PATIENT_INTROUVABLE", "Patient introuvable dans ce centre")));
                categorie = risques.de(centre, patient);
            }
            if (!ecrire) {
                if (patient == null) lues.verifierPourUneCreation(demandee, categorie);
                else lues.verifier(patient, demandee, categorie);
                return null;
            }
            lues.placer(patient, demandee, categorie);
            if (!lues.changements().isEmpty()) places.enregistrer(lues);
            return null;
        });
    }
}
