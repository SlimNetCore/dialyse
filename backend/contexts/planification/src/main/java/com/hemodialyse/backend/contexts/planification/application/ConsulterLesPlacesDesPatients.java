package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lecture publiée des places des patients (Séance, Absences, Direction, Patient et couverture). Lecture seule, bornée au centre. */
public class ConsulterLesPlacesDesPatients implements PlacesDesPatients {

    private final PlacesDuCentreRepository places;

    public ConsulterLesPlacesDesPatients(PlacesDuCentreRepository places) {
        this.places = places;
    }

    @Override
    public Optional<PlaceDuPatient> deLaFiche(UUID centre, UUID patient) {
        return places.placementActuel(new CentreId(centre), new PatientId(patient)).filter(o -> !o.estVide())
                .map(ConsulterLesPlacesDesPatients::publiee);
    }

    @Override
    public List<PlaceDuPatient> duCentre(UUID centre, LocalDate le) {
        return places.charger(new CentreId(centre), new RessourcesDuCentre(null, null, null, null, null), le).occupations()
                .stream().filter(o -> !o.estVide()).map(ConsulterLesPlacesDesPatients::publiee).toList();
    }

    private static PlaceDuPatient publiee(Occupation o) {
        Set<DayOfWeek> jours = o.jours().stream().map(j -> j.jourCivil()).collect(Collectors.toUnmodifiableSet());
        return new PlaceDuPatient(o.patient().valeur(), o.salle() == null ? null : o.salle().valeur(),
                o.creneau() == null ? null : o.creneau().valeur(),
                o.generateur() == null ? null : o.generateur().valeur(), jours);
    }
}
