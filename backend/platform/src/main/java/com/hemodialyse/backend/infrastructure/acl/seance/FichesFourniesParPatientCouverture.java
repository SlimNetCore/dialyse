package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PlacesDesPatients;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FichesFournies;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link FichesFournies} (ADR 0133, S5) en composant des langages publiés : Patient et
 * couverture ({@link FichesDesPatients} : carte, médecin traitant, recherches, références) et Planification
 * ({@link PlacesDesPatients} : salle, créneau, générateur). Toute lecture est bornée au centre demandé.
 *
 * <p>Le nom, la marque et l'état du générateur ne sont pas fournis ici : ils ne l'ont jamais été (nuls), la Séance les précise par la
 * GMAO ({@code Generateurs.preciser}).
 */
@Component
public class FichesFourniesParPatientCouverture implements FichesFournies {

    private final FichesDesPatients patients;
    private final PlacesDesPatients places;

    public FichesFourniesParPatientCouverture(FichesDesPatients patients, PlacesDesPatients places) {
        this.patients = patients;
        this.places = places;
    }

    @Override
    public Optional<Fiche> fiche(UUID centre, UUID patient) {
        return patients.carte(centre, patient).map(carte -> {
            UUID medecin = patients.medecinTraitant(centre, patient).orElse(null);
            Optional<PlacesDesPatients.PlaceDuPatient> place = places.deLaFiche(centre, patient);
            return new Fiche(patient, carte.code(), carte.nom(), carte.prenom(), carte.sexe(), carte.dateNaissance(),
                    carte.numeroAssurance(), carte.groupeSanguin(), carte.telMobile(), medecin,
                    place.map(PlacesDesPatients.PlaceDuPatient::salle).orElse(null),
                    place.map(PlacesDesPatients.PlaceDuPatient::creneau).orElse(null),
                    place.map(PlacesDesPatients.PlaceDuPatient::generateur).orElse(null), null, null, null);
        });
    }

    @Override
    public Optional<UUID> parIdentifiant(UUID centre, UUID identifiant) {
        return patients.existe(centre, identifiant) ? Optional.of(identifiant) : Optional.empty();
    }

    @Override
    public Optional<UUID> parCode(UUID centre, String code) {
        return patients.parCode(centre, code);
    }

    @Override
    public Optional<UUID> parNumeroDAssurance(UUID centre, String numero) {
        return patients.parNumeroDAssurance(centre, numero);
    }

    @Override
    public List<References> tous(UUID centre) {
        return patients.references(centre).stream()
                .map(r -> new References(r.patient(), r.code(), r.numeroAssurance(), r.numeroAssuranceDeLAssure()))
                .toList();
    }
}
