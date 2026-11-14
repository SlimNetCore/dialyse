package com.hemodialyse.backend.contexts.seance.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.DeplacementsDuJourDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.application.PlaceDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Place d'un patient à une date, bornée au centre (SE7f, ADR 0100 ; LEC-PLN-GMAO, ADR 0179). Le déplacement temporaire du jour
 * (maintenance d'un générateur) prime sur la place habituelle de la fiche : l'un et l'autre sont lus par les langages publiés de la
 * Planification ({@link DeplacementsDuJourDesPatients}, {@link PlacesDesPatients}), plus aucun SQL sur {@code deplacement_temporaire}. Sans
 * salle ou sans créneau : vide (P6, golden {@code LecturesDePlaceConsommeesParLaSeance}) ; un déplacement incomplet est ignoré comme avant.
 */
public class PlaceDuPatientParLaPlanification implements PlaceDuPatient {

    private final DeplacementsDuJourDesPatients deplacements;
    private final PlacesDesPatients places;

    public PlaceDuPatientParLaPlanification(DeplacementsDuJourDesPatients deplacements, PlacesDesPatients places) {
        this.deplacements = deplacements;
        this.places = places;
    }

    @Override
    public Optional<PlaceDeSeance> placeLe(CentreId centre, PatientId patient, LocalDate date) {
        Optional<PlaceDeSeance> temporaire = deplacements.duPatient(centre.valeur(), patient.valeur(), date)
                .flatMap(d -> place(d.salle(), d.creneau(), d.generateur()));
        if (temporaire.isPresent()) return temporaire;
        return places.deLaFiche(centre.valeur(), patient.valeur()).flatMap(p -> place(p.salle(), p.creneau(), p.generateur()));
    }

    static Optional<PlaceDeSeance> place(UUID salle, UUID creneau, UUID generateur) {
        return salle == null || creneau == null ? Optional.empty() : Optional.of(new PlaceDeSeance(salle, creneau, generateur));
    }
}
