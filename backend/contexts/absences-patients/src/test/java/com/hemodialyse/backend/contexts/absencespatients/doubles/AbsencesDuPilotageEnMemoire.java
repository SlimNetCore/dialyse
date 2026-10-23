package com.hemodialyse.backend.contexts.absencespatients.doubles;

import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Double en mémoire du langage publié {@link AbsencesDuPilotage} : passe le même contrat que l'adaptateur JDBC.
 */
public class AbsencesDuPilotageEnMemoire implements AbsencesDuPilotage {

    private record Ligne(UUID centre, UUID patient, LocalDate date, String statut, String motif, BigDecimal ht, BigDecimal ttc) {
    }

    private record Cle(UUID centre, String statut, String motif) {
    }

    private final List<Ligne> lignes = new ArrayList<>();
    private LocalDate aujourdhui = LocalDate.of(2026, 3, 10);

    public void fixerAujourdhui(LocalDate jour) {
        this.aujourdhui = jour;
    }

    public void poser(UUID centre, UUID patient, LocalDate date, String statut, String motif, BigDecimal ht, BigDecimal ttc) {
        lignes.add(new Ligne(centre, patient, date, statut, motif, ht, ttc));
    }

    private Stream<Ligne> horsAnnulees(Collection<UUID> centres, LocalDate du, LocalDate au) {
        return lignes.stream().filter(l -> centres.contains(l.centre) && !l.date.isBefore(du) && !l.date.isAfter(au)
                && !"ANNULEE".equals(l.statut));
    }

    @Override
    public LocalDate aujourdhui() {
        return aujourdhui;
    }

    @Override
    public long nombreHorsAnnulees(UUID centre, LocalDate du, LocalDate au) {
        return horsAnnulees(List.of(centre), du, au).count();
    }

    @Override
    public List<AbsenceDuCentre> horsAnnulees(UUID centre, LocalDate du, LocalDate au) {
        return horsAnnulees(List.of(centre), du, au).map(l -> new AbsenceDuCentre(l.patient, l.date, l.statut)).toList();
    }

    @Override
    public List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
        Map<Cle, List<Ligne>> groupes = new LinkedHashMap<>();
        horsAnnulees(centres, du, au).forEach(l -> groupes.computeIfAbsent(new Cle(l.centre, l.statut, l.motif),
                k -> new ArrayList<>()).add(l));
        return groupes.entrySet().stream().map(e -> new CumulDuCentre(e.getKey().centre(), e.getKey().statut(),
                e.getKey().motif(), e.getValue().size(),
                e.getValue().stream().map(Ligne::ht).reduce(BigDecimal.ZERO, BigDecimal::add),
                e.getValue().stream().map(Ligne::ttc).reduce(BigDecimal.ZERO, BigDecimal::add))).toList();
    }

    @Override
    public List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au) {
        Map<List<Integer>, List<Ligne>> parMois = new LinkedHashMap<>();
        horsAnnulees(centres, du, au).filter(l -> List.of("A_QUALIFIER", "JUSTIFIEE", "NON_JUSTIFIEE").contains(l.statut))
                .forEach(l -> parMois.computeIfAbsent(List.of(l.date.getYear(), l.date.getMonthValue()),
                        k -> new ArrayList<>()).add(l));
        return parMois.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<List<Integer>, List<Ligne>> e) -> e.getKey().get(0))
                        .thenComparing(e -> e.getKey().get(1)))
                .map(e -> new AbsencesDuMois(e.getKey().get(0), e.getKey().get(1), e.getValue().size(),
                        e.getValue().stream().map(Ligne::ht).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }
}
