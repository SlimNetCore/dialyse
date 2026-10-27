package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.doubles.PlacesEnMemoire;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;

/** Contrat des places exécuté contre le double en mémoire. */
class PlacesEnMemoireContratTest extends PlacesContrat {

    private final PlacesEnMemoire monde = new PlacesEnMemoire(null);

    @Override
    protected PlacesDuCentreRepository places() {
        return monde;
    }

    @Override
    protected RisquesInfectieux risques() {
        return monde;
    }

    @Override
    protected NomsDesPatients noms() {
        return monde;
    }

    @Override
    protected VerrouDesPlaces verrou() {
        return monde;
    }

    private static Occupation occupation(PatientId patient, Place place) {
        return place == null ? null : new Occupation(patient, place.salle(), place.creneau(), place.generateur(), place.jours(), null);
    }

    @Override
    protected void patient(PatientId patient, String nomAffiche, Place placement) {
        monde.patient(patient, nomAffiche, occupation(patient, placement));
    }

    @Override
    protected void patientDUnAutreCentre(PatientId patient, String nomAffiche, Place placement) {
        monde.patient(patient, nomAffiche, occupation(patient, placement));
        monde.autreCentre.add(patient);
    }

    @Override
    protected void sortir(PatientId patient) {
        monde.sortis.add(patient);
    }

    @Override
    protected void serologiePositive(PatientId patient) {
        monde.serologiePositive.add(patient);
    }
}
