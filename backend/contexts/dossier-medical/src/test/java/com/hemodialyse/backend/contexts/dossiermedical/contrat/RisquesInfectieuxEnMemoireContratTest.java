package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ResultatSerologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RisqueInfectieux;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le double en mémoire applique la règle du domaine {@code RisqueInfectieux} aux sérologies du centre ; une sérologie dont le marqueur ou
 * le résultat n'est pas lisible (casse) ne compte pas, comme le SQL en casse stricte.
 */
class RisquesInfectieuxEnMemoireContratTest extends RisquesInfectieuxContrat {

    private final Map<UUID, List<RisqueInfectieux.SerologieObservee>> parCentre = new HashMap<>();

    @Override
    protected RisquesInfectieuxPublies risques() {
        return centre -> RisqueInfectieux.patientsARisque(parCentre.getOrDefault(centre, List.of()));
    }

    @Override
    protected void serologie(UUID centre, UUID patient, String marqueur, String resultat, LocalDate prelevement, Instant saisie) {
        MarqueurSerologique m = lisible(MarqueurSerologique.class, marqueur);
        ResultatSerologique r = lisible(ResultatSerologique.class, resultat);
        if (m == null || r == null) return;
        parCentre.computeIfAbsent(centre, k -> new ArrayList<>()).add(new RisqueInfectieux.SerologieObservee(patient, m, r, prelevement, saisie));
    }

    private static <E extends Enum<E>> E lisible(Class<E> type, String nom) {
        return Set.of(type.getEnumConstants()).stream().filter(e -> e.name().equals(nom)).findFirst().orElse(null);
    }
}
