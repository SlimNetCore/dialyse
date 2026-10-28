package com.hemodialyse.backend.contexts.planification.application;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PatientsAuxGenerateurs;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Lecture publiée : les patients rattachés à des générateurs, tous états confondus (chiffre d'alerte de la Direction). */
public class CompterLesPatientsAuxGenerateurs implements PatientsAuxGenerateurs {

    private final SejoursDesPatients patients;

    public CompterLesPatientsAuxGenerateurs(SejoursDesPatients patients) {
        this.patients = patients;
    }

    @Override
    public long nombre(UUID centre, Collection<UUID> generateurs) {
        if (generateurs.isEmpty()) return 0;
        Set<GenerateurId> cibles = generateurs.stream().map(GenerateurId::new).collect(Collectors.toSet());
        return patients.duCentreSansRisque(new CentreId(centre)).stream()
                .filter(p -> p.place().generateur() != null && cibles.contains(p.place().generateur())).count();
    }
}
