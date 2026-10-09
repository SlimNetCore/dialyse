package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.SanteBase.AlerteSante;
import com.hemodialyse.backend.application.supervision.SanteBase.IndexInutilise;
import com.hemodialyse.backend.application.supervision.SanteBase.StatutPartitionnement;
import com.hemodialyse.backend.application.supervision.SanteBase.TableDiagnostiquee;
import com.hemodialyse.backend.application.supervision.SanteBase.TableSante;
import com.hemodialyse.backend.application.supervision.port.SanteBasePort.Generale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Règles de lecture des indicateurs de santé de la base. Classe pure ; les seuils de partitionnement sont ceux de
 * l'étude {@code docs/architecture/partitionnement-postgresql.md} (§6).
 */
public final class DiagnosticSante {

    static final long PARTITION_LIGNES = 20_000_000L;
    static final long PARTITION_OCTETS = 10L * 1024 * 1024 * 1024;
    static final double CACHE_ATTENTION_PCT = 95;
    static final double CACHE_CRITIQUE_PCT = 90;
    static final double CONNEXIONS_ATTENTION_PCT = 80;
    static final double MORTES_ATTENTION_PCT = 20;
    static final long MORTES_MIN = 1000;
    static final long SCANS_COMPLETS_MIN = 1000;
    static final long LIGNES_PAR_SCAN_MIN = 1000;
    static final long TABLE_ASSEZ_GRANDE = 10_000;

    private static final Map<String, Integer> ORDRE = Map.of("CRITIQUE", 0, "ATTENTION", 1, "INFO", 2);

    private DiagnosticSante() {
    }

    public static SanteBase evaluer(Generale generale, List<TableSante> tables, List<IndexInutilise> inutilises) {
        List<TableDiagnostiquee> diagnostiquees = tables.stream().map(DiagnosticSante::diagnostiquer).toList();
        List<AlerteSante> alertes = new ArrayList<>();

        if (generale.cachePct() < CACHE_CRITIQUE_PCT) {
            alertes.add(new AlerteSante("CACHE_FAIBLE", "CRITIQUE", "", pct(generale.cachePct())));
        } else if (generale.cachePct() < CACHE_ATTENTION_PCT) {
            alertes.add(new AlerteSante("CACHE_FAIBLE", "ATTENTION", "", pct(generale.cachePct())));
        }
        if (generale.connexionsMax() > 0
                && generale.connexions() * 100.0 / generale.connexionsMax() >= CONNEXIONS_ATTENTION_PCT) {
            alertes.add(new AlerteSante("CONNEXIONS_SATUREES", "ATTENTION", "",
                    generale.connexions() + "/" + generale.connexionsMax()));
        }
        for (TableDiagnostiquee d : diagnostiquees) {
            TableSante t = d.table();
            if (d.partitionnement() == StatutPartitionnement.A_ETUDIER) {
                alertes.add(new AlerteSante("PARTITIONNEMENT_A_ETUDIER", "ATTENTION", t.nom(), String.valueOf(t.lignes())));
            }
            if (t.mortes() >= MORTES_MIN && d.mortesPct() >= MORTES_ATTENTION_PCT) {
                alertes.add(new AlerteSante("LIGNES_MORTES", "ATTENTION", t.nom(), pct(d.mortesPct())));
            }
            if (lueEnEntier(t)) {
                alertes.add(new AlerteSante("TABLE_LUE_EN_ENTIER", "ATTENTION", t.nom(), String.valueOf(t.scansComplets())));
            }
        }
        if (!inutilises.isEmpty()) {
            alertes.add(new AlerteSante("INDEX_INUTILISES", "INFO", "", String.valueOf(inutilises.size())));
        }
        alertes.sort(Comparator.comparing(a -> ORDRE.getOrDefault(a.niveau(), 3)));
        return new SanteBase(true, null, generale.tailleOctets(), generale.cachePct(), generale.connexions(),
                generale.connexionsMax(), generale.statsReset(), diagnostiquees, inutilises, alertes);
    }

    static TableDiagnostiquee diagnostiquer(TableSante t) {
        long total = t.vivantes() + t.mortes();
        double mortesPct = total <= 0 ? 0 : t.mortes() * 100.0 / total;
        return new TableDiagnostiquee(t, mortesPct, statutPartitionnement(t));
    }

    /**
     * {@code A_ETUDIER} à partir des seuils de l'étude, {@code A_SURVEILLER} à la moitié.
     */
    static StatutPartitionnement statutPartitionnement(TableSante t) {
        if (t.lignes() >= PARTITION_LIGNES || t.tailleOctets() >= PARTITION_OCTETS) {
            return StatutPartitionnement.A_ETUDIER;
        }
        if (t.lignes() >= PARTITION_LIGNES / 2 || t.tailleOctets() >= PARTITION_OCTETS / 2) {
            return StatutPartitionnement.A_SURVEILLER;
        }
        return StatutPartitionnement.OK;
    }

    /**
     * Table assez grande, souvent balayée, et chaque balayage lit beaucoup de lignes : il manque sans doute un index.
     */
    static boolean lueEnEntier(TableSante t) {
        return t.vivantes() >= TABLE_ASSEZ_GRANDE && t.scansComplets() >= SCANS_COMPLETS_MIN
                && t.lignesLuesParScans() / Math.max(1, t.scansComplets()) >= LIGNES_PAR_SCAN_MIN
                && t.scansComplets() > t.scansIndex();
    }

    private static String pct(double v) {
        return String.valueOf(Math.round(v));
    }
}
