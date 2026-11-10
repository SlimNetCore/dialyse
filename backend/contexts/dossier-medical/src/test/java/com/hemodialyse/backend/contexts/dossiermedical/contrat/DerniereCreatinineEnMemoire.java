package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DerniereCreatininePubliee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire de la dernière créatinine publiée : mêmes règles que la table (centre, créatinine non nulle, date de prélèvement, création puis identifiant décroissants : KB-SEA-96). */
public class DerniereCreatinineEnMemoire implements DerniereCreatininePubliee {

    public record Analyse(UUID id, UUID centre, UUID patient, LocalDate prelevement, BigDecimal creatinine, Instant creeLe) {
    }

    private final List<Analyse> analyses = new ArrayList<>();

    public void ajouter(Analyse analyse) {
        analyses.add(analyse);
    }

    private static int octetsNonSignes(UUID a, UUID b) {
        int fort = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return fort != 0 ? fort : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    @Override
    public Optional<BigDecimal> duPatient(UUID centre, UUID patient) {
        return analyses.stream()
                .filter(a -> a.centre().equals(centre) && a.patient().equals(patient))
                .filter(a -> a.creatinine() != null)
                .min(Comparator.comparing(Analyse::prelevement, Comparator.<LocalDate>reverseOrder())
                        .thenComparing(Analyse::creeLe, Comparator.nullsLast(Comparator.<Instant>reverseOrder()))
                        .thenComparing((x, y) -> octetsNonSignes(y.id(), x.id())))
                .map(Analyse::creatinine);
    }
}
