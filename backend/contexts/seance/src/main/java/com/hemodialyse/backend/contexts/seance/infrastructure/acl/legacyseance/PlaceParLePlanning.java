package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlaceFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/** ACL de la place du patient : traduit le langage publié {@link PlaceFournie} en place de séance (salle et créneau obligatoires). */
public class PlaceParLePlanning implements PlaceDuPatient {

    private final PlaceFournie places;

    public PlaceParLePlanning(PlaceFournie places) {
        this.places = places;
    }

    @Override
    public Optional<PlaceDeSeance> placeLe(CentreId centre, PatientId patient, LocalDate jour) {
        return places.placeLe(centre.valeur(), patient.valeur(), jour).map(p -> traduire(p));
    }

    static PlaceDeSeance traduire(PlaceFournie.Place p) {
        return new PlaceDeSeance(p.salle(), p.creneau(), p.generateur());
    }
}
