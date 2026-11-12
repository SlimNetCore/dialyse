package com.hemodialyse.backend.contexts.facturation.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Données de la synthèse de facturation d'un centre sur une période (bornes incluses, date de facturation). Les montants
 * {@code totalHt}, {@code totalTva} et {@code totalTtc} sont la somme des totaux enregistrés sur les factures de la période,
 * la même base que le tableau de bord (ADR 0034) ; les indicateurs et les répartitions comptent aussi les factures sans ligne.
 */
public record SyntheseMensuelle(
        UUID centre,
        LocalDate debut,
        LocalDate fin,
        List<Caisse> caisses,
        List<Repartition> parCaisse,
        List<Repartition> parSituation,
        long factures,
        long patients,
        long seances,
        BigDecimal totalHt,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        List<Ligne> lignes
) {

    /** Sans tableau forfait x caisse (doubles de test, périodes sans ligne). */
    public SyntheseMensuelle(UUID centre, LocalDate debut, LocalDate fin, List<Caisse> caisses, List<Repartition> parCaisse,
                             List<Repartition> parSituation, long factures, long patients, long seances,
                             BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc) {
        this(centre, debut, fin, caisses, parCaisse, parSituation, factures, patients, seances, totalHt, totalTva, totalTtc, List.of());
    }

    /**
     * Une cellule du tableau forfait x caisse (PL8, ADR 0140 D3 : la Facturation fournit les données au rendu) : le libellé du forfait,
     * le code NORMALISÉ de la caisse (« INCONNU » sans caisse ni code), le HT des lignes et leur part du TTC enregistré de la facture
     * (ADR 0034 ; facture à HT nul : TTC réparti à parts égales entre ses lignes), arrondis à 2 décimales.
     */
    public record Ligne(String forfait, String caisse, BigDecimal ht, BigDecimal ttc) {
    }

    /** Une caisse d'assurance des factures de la période (« INCONNU » sans caisse). */
    public record Caisse(String code, String libelle) {
    }

    /** Une tranche de répartition : patients et séances des factures de la période. */
    public record Repartition(String code, String libelle, long patients, long seances) {
    }

    /**
     * Les colonnes du tableau : une par code de caisse, dans l'ordre de lecture (deux caisses de même code partagent la
     * colonne de la première, KB-FAC-57 reproduit) ; une seule colonne « INCONNU » s'il n'y a aucune facture.
     */
    public List<Caisse> colonnes() {
        List<Caisse> colonnes = new ArrayList<>();
        Set<String> codes = new LinkedHashSet<>();
        for (Caisse caisse : caisses) {
            if (codes.add(caisse.code())) {
                colonnes.add(caisse);
            }
        }
        if (colonnes.isEmpty()) {
            colonnes.add(new Caisse("INCONNU", "Inconnu"));
        }
        return colonnes;
    }
}
