package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseFinanciereDesCentres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Double en mémoire des trois lectures de pilotage (tableau de bord, synthèse, synthèse financière publiée) : mêmes règles de
 * comptage, de regroupement et de tri que les requêtes JDBC, vérifiées par le même contrat.
 */
public class PilotageEnMemoire implements LectureDuTableauDeBord, LectureDeLaSynthese, SyntheseFinanciereDesCentres {

    public record Facture(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal ht, BigDecimal tva, BigDecimal ttc,
                          String codeCaisse, String nomCaisse, String situation) {
    }

    private record Seance(UUID centre, UUID facture, LocalDate date, String statut) {
    }

    private record Reglement(UUID facture, BigDecimal montant) {
    }

    private final List<Facture> factures = new ArrayList<>();
    private final List<Seance> seances = new ArrayList<>();
    private final List<Reglement> reglements = new ArrayList<>();

    public void poserLaFacture(Facture facture) {
        factures.add(facture);
    }

    public void poserLaSeance(UUID centre, UUID facture, LocalDate date, String statut) {
        seances.add(new Seance(centre, facture, date, statut));
    }

    public void poserLeReglement(UUID facture, BigDecimal montant) {
        reglements.add(new Reglement(facture, montant));
    }

    private List<Facture> du(UUID centre, LocalDate debut, LocalDate fin) {
        return factures.stream().filter(f -> f.centre().equals(centre) && !f.date().isBefore(debut) && !f.date().isAfter(fin)).toList();
    }

    private long seancesRattachees(Collection<Facture> groupe) {
        Set<UUID> ids = groupe.stream().map(Facture::id).collect(Collectors.toSet());
        return seances.stream().filter(s -> s.facture() != null && ids.contains(s.facture())).count();
    }

    private static long patients(Collection<Facture> groupe) {
        return groupe.stream().map(Facture::patient).distinct().count();
    }

    private static BigDecimal somme(Collection<Facture> groupe, Function<Facture, BigDecimal> montant) {
        return groupe.stream().map(montant).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public TableauDeBordDuMois du(CentreId centre, YearMonth mois) {
        List<Facture> duMois = du(centre.valeur(), mois.atDay(1), mois.atEndOfMonth());
        long facturees = seances.stream().filter(s -> s.centre().equals(centre.valeur()) && "FACTUREE".equals(s.statut())
                && !s.date().isBefore(mois.atDay(1)) && !s.date().isAfter(mois.atEndOfMonth())).count();

        Map<String, List<Facture>> parCaisse = duMois.stream().collect(Collectors.groupingBy(
                f -> f.codeCaisse() == null ? "INCONNU" : f.codeCaisse(), LinkedHashMap::new, Collectors.toList()));
        List<TableauDeBordDuMois.RepartitionParCaisse> caisses = parCaisse.entrySet().stream()
                .map(e -> new TableauDeBordDuMois.RepartitionParCaisse(e.getKey(),
                        e.getValue().get(0).nomCaisse() == null ? "Inconnu" : e.getValue().get(0).nomCaisse(),
                        seancesRattachees(e.getValue()), patients(e.getValue())))
                .sorted(Comparator.comparingLong(TableauDeBordDuMois.RepartitionParCaisse::patients).reversed()
                        .thenComparing(TableauDeBordDuMois.RepartitionParCaisse::code))
                .toList();

        Map<String, List<Facture>> parSituation = duMois.stream().collect(Collectors.groupingBy(
                f -> f.situation() == null ? "INCONNU" : f.situation(), LinkedHashMap::new, Collectors.toList()));
        List<TableauDeBordDuMois.RepartitionParSituation> situations = parSituation.entrySet().stream()
                .map(e -> new TableauDeBordDuMois.RepartitionParSituation(e.getKey(),
                        e.getValue().get(0).situation() == null ? "Inconnu" : e.getValue().get(0).situation(),
                        seancesRattachees(e.getValue())))
                .sorted(Comparator.comparingLong(TableauDeBordDuMois.RepartitionParSituation::seances).reversed()
                        .thenComparing(TableauDeBordDuMois.RepartitionParSituation::code))
                .toList();

        return new TableauDeBordDuMois(centre.valeur(), mois, somme(duMois, Facture::ttc), somme(duMois, Facture::ht), facturees,
                patients(duMois), duMois.size(), caisses, situations);
    }

    private static String sansBlanc(String valeur, String parDefaut) {
        return valeur == null || valeur.trim().isEmpty() ? parDefaut : valeur.trim();
    }

    @Override
    public SyntheseMensuelle de(CentreId centre, LocalDate debut, LocalDate fin) {
        List<Facture> periode = du(centre.valeur(), debut, fin);

        List<SyntheseMensuelle.Caisse> caisses = periode.stream()
                .map(f -> new SyntheseMensuelle.Caisse(sansBlanc(f.codeCaisse(), "INCONNU"), sansBlanc(f.nomCaisse(), "Inconnu")))
                .distinct().sorted(Comparator.comparing(SyntheseMensuelle.Caisse::libelle)).toList();

        return new SyntheseMensuelle(centre.valeur(), debut, fin, caisses,
                repartir(periode, f -> sansBlanc(f.codeCaisse(), "INCONNU"), f -> sansBlanc(f.nomCaisse(), "Inconnu")),
                repartir(periode, f -> sansBlanc(f.situation(), "INCONNU"), f -> sansBlanc(f.situation(), "Inconnu")),
                periode.size(), patients(periode), seancesRattachees(periode),
                somme(periode, Facture::ht), somme(periode, Facture::tva), somme(periode, Facture::ttc));
    }

    private List<SyntheseMensuelle.Repartition> repartir(List<Facture> periode, Function<Facture, String> code,
                                                         Function<Facture, String> libelle) {
        Map<List<String>, List<Facture>> groupes = periode.stream().collect(Collectors.groupingBy(
                f -> List.of(code.apply(f), libelle.apply(f)), LinkedHashMap::new, Collectors.toList()));
        return groupes.entrySet().stream()
                .map(e -> new SyntheseMensuelle.Repartition(e.getKey().get(0), e.getKey().get(1), patients(e.getValue()),
                        seancesRattachees(e.getValue())))
                .sorted(Comparator.comparingLong(SyntheseMensuelle.Repartition::patients).reversed()
                        .thenComparing(Comparator.comparingLong(SyntheseMensuelle.Repartition::seances).reversed())
                        .thenComparing(SyntheseMensuelle.Repartition::code))
                .toList();
    }

    @Override
    public Map<UUID, ChiffresDuCentre> chiffresParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
        Map<UUID, ChiffresDuCentre> resultat = new HashMap<>();
        for (UUID centre : new HashSet<>(centres)) {
            List<Facture> periode = du(centre, du, au);
            if (periode.isEmpty()) {
                continue;
            }
            Set<UUID> ids = periode.stream().map(Facture::id).collect(Collectors.toSet());
            List<Reglement> regles = reglements.stream().filter(r -> ids.contains(r.facture())).toList();
            BigDecimal encaisse = regles.isEmpty() ? BigDecimal.ZERO
                    : regles.stream().map(Reglement::montant).reduce(BigDecimal.ZERO, BigDecimal::add);
            resultat.put(centre, new ChiffresDuCentre(periode.size(), somme(periode, Facture::ht), somme(periode, Facture::ttc),
                    encaisse));
        }
        return resultat;
    }

    @Override
    public List<ChiffresDuMois> chiffresParMois(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<ChiffresDuMois> resultat = new ArrayList<>();
        for (UUID centre : new HashSet<>(centres)) {
            Map<YearMonth, List<Facture>> parMois = du(centre, du, au).stream()
                    .collect(Collectors.groupingBy(f -> YearMonth.from(f.date())));
            parMois.forEach((mois, groupe) -> resultat.add(new ChiffresDuMois(centre, mois.getYear(), mois.getMonthValue(),
                    somme(groupe, Facture::ht), somme(groupe, Facture::ttc))));
        }
        return resultat;
    }

    @Override
    public List<ChiffreDeLaCaisse> chiffreDAffairesParCaisse(Collection<UUID> centres, LocalDate du, LocalDate au) {
        List<ChiffreDeLaCaisse> resultat = new ArrayList<>();
        for (UUID centre : new HashSet<>(centres)) {
            Map<String, List<Facture>> parCaisse = du(centre, du, au).stream()
                    .collect(Collectors.groupingBy(f -> Objects.toString(f.codeCaisse(), "")));
            parCaisse.forEach((code, groupe) -> resultat.add(new ChiffreDeLaCaisse(centre, code, groupe.get(0).nomCaisse(),
                    somme(groupe, Facture::ht))));
        }
        return resultat;
    }
}
