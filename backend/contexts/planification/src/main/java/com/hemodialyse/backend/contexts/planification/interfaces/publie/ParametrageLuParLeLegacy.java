package com.hemodialyse.backend.contexts.planification.interfaces.publie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanning;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDuPlanningRepository;
import com.hemodialyse.backend.contexts.planification.domain.PreferencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.ProfilsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesDePlanification;
import com.hemodialyse.backend.contexts.planification.domain.ReglagesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Competence;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ParametrageDeLaPlanification;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter entrant du langage publié {@link ParametrageDeLaPlanification} : traduit le modèle du contexte en records publiés,
 * sans logique.
 */
public class ParametrageLuParLeLegacy implements ParametrageDeLaPlanification {

    private final ParametresDuPlanningRepository parametres;
    private final ReglagesRepository reglages;
    private final PreferencesRepository preferences;
    private final ProfilsRepository profils;

    public ParametrageLuParLeLegacy(ParametresDuPlanningRepository parametres, ReglagesRepository reglages,
                                    PreferencesRepository preferences, ProfilsRepository profils) {
        this.parametres = parametres;
        this.reglages = reglages;
        this.preferences = preferences;
        this.profils = profils;
    }

    @Override
    public Parametres parametres(UUID centre) {
        ParametresDuPlanning p = parametres.lire(new CentreId(centre));
        Set<DayOfWeek> jours = EnumSet.noneOf(DayOfWeek.class);
        for (JourDeLaSemaine jour : p.joursOuverts()) jours.add(jour.jourCivil());
        Set<UUID> salles = p.sallesIsolement().stream().map(SalleId::valeur).collect(Collectors.toUnmodifiableSet());
        return new Parametres(jours, salles, p.ratio().patientsParInfirmier(), p.patientsParPoste().nombre());
    }

    @Override
    public Reglages reglages(UUID centre) {
        ReglagesDePlanification r = reglages.lire(new CentreId(centre));
        return new Reglages(r.replanificationNocturne(), r.heuresParVacation(), r.heuresHebdoTempsPlein(), r.reposHebdoMin());
    }

    @Override
    public Map<UUID, Preference> preferencesDesPatients(UUID centre) {
        Map<UUID, Preference> resultat = new HashMap<>();
        preferences.duCentre(new CentreId(centre)).forEach((patient, p) -> resultat.put(patient.valeur(),
                new Preference(patient.valeur(), p.creneauPrefere() == null ? null : p.creneauPrefere().valeur(),
                        p.seancesParSemaine(), p.joursAChoisir())));
        return resultat;
    }

    @Override
    public Map<UUID, Profil> profilsDesInfirmiers(UUID centre) {
        Map<UUID, Profil> resultat = new HashMap<>();
        profils.duCentre(new CentreId(centre)).forEach((infirmier, p) -> resultat.put(infirmier.valeur(),
                new Profil(infirmier.valeur(), p.tauxActivite(),
                        p.competences().stream().map(Competence::name).collect(Collectors.toUnmodifiableSet()))));
        return resultat;
    }

    @Override
    public List<UUID> centresEnReplanificationNocturne() {
        return reglages.centresEnReplanificationNocturne().stream().map(CentreId::valeur).toList();
    }
}
