package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.application.ContraintesDesPatients.Contraintes;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.PreferenceDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.SejourDuPatient;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.PatientDeLaSemaine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lit l'état du centre soumis à l'optimisation à partir des ports du contexte : ressources (générateurs en service, fermetures de
 * l'horizon seulement), personnel (la présence de la lecture, P4), patients placés ou en attente avec leur séjour, préférences,
 * profils, contraintes (transporteurs, compétences), indisponibilités de générateurs, déplacements temporaires et séances réalisées.
 * Remplace le chargement du legacy et sa seconde règle de présence. Lecture seule, bornée au centre.
 * <p>
 * Un patient est « placé » s'il a une salle, un créneau et des jours et si sa place n'est pas déjà libérée au début de la période ; il
 * est « en attente » s'il lui manque la salle ou le créneau et qu'il a des jours prescrits ou des jours à choisir.
 */
public class LireLesDonneesDeLOptimisation implements DonneesDeLOptimisation {

    private final LectureDesRessources ressources;
    private final PersonnelDeLaPresence personnel;
    private final SejoursDesPatients patients;
    private final PreferencesRepository preferences;
    private final ProfilsRepository profils;
    private final ContraintesDesPatients contraintes;
    private final IndisponibilitesDeGenerateurs indisponibilites;
    private final DeplacementsTemporairesRepository temporaires;
    private final SeancesRealisees seances;

    @SuppressWarnings("java:S107")
    public LireLesDonneesDeLOptimisation(LectureDesRessources ressources, PersonnelDeLaPresence personnel, SejoursDesPatients patients,
                                         PreferencesRepository preferences, ProfilsRepository profils,
                                         ContraintesDesPatients contraintes, IndisponibilitesDeGenerateurs indisponibilites,
                                         DeplacementsTemporairesRepository temporaires, SeancesRealisees seances) {
        this.ressources = ressources;
        this.personnel = personnel;
        this.patients = patients;
        this.preferences = preferences;
        this.profils = profils;
        this.contraintes = contraintes;
        this.indisponibilites = indisponibilites;
        this.temporaires = temporaires;
        this.seances = seances;
    }

    private static PatientAPlacer patient(PatientDeLaSemaine p, Occupation place, Poste actuelle, SejourDuPatient sejour,
                                          LocalDate dernierJour, PreferenceDePlanification pref, Map<PatientId, Contraintes> contraintes) {
        Contraintes c = contraintes.getOrDefault(p.patient(), Contraintes.aucune());
        java.util.Set<JourDeLaSemaine> jours = place.jours();
        return new PatientAPlacer(p.patient(), p.nom() == null ? "" : p.nom(), jours, place.categorie().aRisque(), actuelle,
                sejour.admission(), dernierJour, pref == null ? null : pref.creneauPrefere(),
                pref != null && pref.joursAChoisir() ? pref.seancesParSemaine() : null, c.transporteurs(), c.competences());
    }

    @Override
    public DonneesDOptimisation charger(CentreId centre, LocalDate du, LocalDate au) {
        RessourcesDuCentre lues = ressources.duCentre(centre, du);
        // seules les fermetures de l'horizon comptent : une fermeture plus lointaine ne périme pas une proposition
        RessourcesDuCentre duHorizon = new RessourcesDuCentre(lues.salles(), lues.creneaux(), lues.generateurs(), lues.joursOuverts(),
                lues.fermetures().stream().filter(f -> !f.date().isBefore(du) && !f.date().isAfter(au)).toList());
        Map<UUID, String> codes = new HashMap<>();
        duHorizon.generateurs().forEach(g -> codes.put(g.id().valeur(), g.code()));
        Map<PatientId, PreferenceDePlanification> prefs = preferences.duCentre(centre);
        Map<PatientId, Contraintes> contraintesDuCentre = contraintes.duCentre(centre, du);

        List<PatientAPlacer> aPlacer = new ArrayList<>();
        for (PatientDeLaSemaine p : patients.duCentre(centre)) {
            Occupation place = p.place();
            SejourDuPatient sejour = p.sejour();
            if (sejour.placeLibereeAvant(du)) continue;
            LocalDate dernierJour = sejour.dernierJourDePlace();
            boolean estPlace = place.salle() != null && place.creneau() != null;
            PreferenceDePlanification pref = prefs.get(p.patient());
            if (estPlace) {
                if (place.jours().isEmpty()) continue;
                Poste actuelle = new Poste(place.salle(), place.creneau(), place.generateur(),
                        place.generateur() == null ? null : codes.get(place.generateur().valeur()));
                aPlacer.add(patient(p, place, actuelle, sejour, dernierJour, pref, contraintesDuCentre));
            } else {
                if (place.jours().isEmpty() && (pref == null || !pref.joursAChoisir())) continue;
                Poste actuelle = place.salle() == null && place.creneau() == null && place.generateur() == null ? null
                        : new Poste(place.salle(), place.creneau(), place.generateur(), null);
                aPlacer.add(patient(p, place, actuelle, sejour, dernierJour, pref, contraintesDuCentre));
            }
        }
        aPlacer.sort(Comparator.comparing(PatientAPlacer::nom).thenComparing(p -> p.patient().valeur()));

        Map<UUID, ProfilDePlanification> profilsParInfirmier = new HashMap<>();
        for (Map.Entry<InfirmierId, ProfilDePlanification> e : profils.duCentre(centre).entrySet()) {
            profilsParInfirmier.put(e.getKey().valeur(), e.getValue());
        }
        return new DonneesDOptimisation(centre, duHorizon, personnel.lire(centre, du, au), aPlacer, profilsParInfirmier,
                indisponibilites.entre(centre, du, au), temporaires.entre(centre, du, au), seances.duCentre(centre, du, au),
                new ArrayList<>(prefs.values()));
    }
}
