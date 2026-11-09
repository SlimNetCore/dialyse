package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire de la dernière créatinine publiée : mêmes règles que la table (centre, date décroissante, créatinine non nulle). */
public class DerniereCreatinineEnMemoire implements DerniereCreatininePubliee {

    public record Analyse(UUID centre, UUID patient, LocalDate prelevement, BigDecimal creatinine) {
    }

    private final List<Analyse> analyses = new ArrayList<>();

    public void ajouter(Analyse analyse) {
        analyses.add(analyse);
    }

    @Override
    public Optional<BigDecimal> duPatient(UUID centre, UUID patient) {
        return analyses.stream()
                .filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .sorted(Comparator.comparing(Analyse::prelevement).reversed())
                .filter(a -> a.creatinine() != null)
                .max(Comparator.comparing(Analyse::prelevement))
                .map(Analyse::creatinine);
    }
}
