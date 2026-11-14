package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.DeplacementsDuJourDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Lecture publiée du déplacement temporaire du jour d'un patient (Séance). Lecture seule, bornée au centre. */
public class ConsulterLesDeplacementsDuJour implements DeplacementsDuJourDesPatients {

    private final DeplacementsTemporairesRepository deplacements;

    public ConsulterLesDeplacementsDuJour(DeplacementsTemporairesRepository deplacements) {
        this.deplacements = deplacements;
    }

    @Override
    public Optional<DeplacementDuJour> duPatient(UUID centre, UUID patient, LocalDate le) {
        return deplacements.entre(new CentreId(centre), le, le).stream()
                .filter(d -> d.patient().valeur().equals(patient)).findFirst().map(ConsulterLesDeplacementsDuJour::publie);
    }

    private static DeplacementDuJour publie(DeplacementTemporaire d) {
        return new DeplacementDuJour(d.salle().valeur(), d.creneau().valeur(), d.generateur().valeur());
    }
}
