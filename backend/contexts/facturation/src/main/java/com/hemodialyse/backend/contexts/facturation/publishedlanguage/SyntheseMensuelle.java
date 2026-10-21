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
        BigDecimal totalTtc
) {

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
