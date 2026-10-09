package com.hemodialyse.backend.application.supervision;

import java.time.Instant;
import java.util.List;

/**
 * Vue d'ensemble de la santé de la base : taille, cache, connexions, plus grosses tables et pistes d'action.
 *
 * @param disponible      la lecture est possible (PostgreSQL)
 * @param raison          sinon {@code BASE_NON_POSTGRESQL}
 * @param tailleOctets    taille de la base
 * @param cachePct        part des lectures servies par le cache mémoire depuis la dernière remise à zéro des statistiques
 * @param connexions      connexions ouvertes sur la base
 * @param connexionsMax   maximum accepté par le serveur
 * @param statsReset      dernière remise à zéro des statistiques de PostgreSQL (les compteurs d'index en dépendent)
 * @param tables          plus grosses tables, avec leur diagnostic
 * @param indexInutilises index jamais utilisés depuis {@code statsReset}
 * @param alertes         points qui demandent attention, les plus importants d'abord
 */
public record SanteBase(
        boolean disponible,
        String raison,
        long tailleOctets,
        double cachePct,
        int connexions,
        int connexionsMax,
        Instant statsReset,
        List<TableDiagnostiquee> tables,
        List<IndexInutilise> indexInutilises,
        List<AlerteSante> alertes
) {

    public static SanteBase indisponible(String raison) {
        return new SanteBase(false, raison, 0, 0, 0, 0, null, List.of(), List.of(), List.of());
    }

    /**
     * Position d'une table face aux seuils de l'étude de partitionnement (20 M de lignes ou 10 Go).
     */
    public enum StatutPartitionnement {
        OK,
        A_SURVEILLER,
        A_ETUDIER
    }

    /**
     * Mesures brutes d'une table.
     */
    public record TableSante(String nom, long tailleOctets, long lignes, long mortes, long vivantes,
                             long scansComplets, long lignesLuesParScans, long scansIndex, Instant dernierAutovacuum) {
    }

    /**
     * Une table et son diagnostic.
     */
    public record TableDiagnostiquee(TableSante table, double mortesPct, StatutPartitionnement partitionnement) {
    }

    public record IndexInutilise(String table, String index, long tailleOctets) {
    }

    /**
     * @param code   identifiant stable ({@code SUPERVISION.SANTE.ALERTE.<code>})
     * @param niveau {@code CRITIQUE} ou {@code ATTENTION} ou {@code INFO}
     * @param cible  table ou ressource concernée (peut être vide)
     * @param valeur chiffre ayant déclenché la règle, formaté
     */
    public record AlerteSante(String code, String niveau, String cible, String valeur) {
    }
}
