package com.hemodialyse.backend.application.supervision.port;

import com.hemodialyse.backend.application.supervision.SanteBase.IndexInutilise;
import com.hemodialyse.backend.application.supervision.SanteBase.TableSante;

import java.time.Instant;
import java.util.List;

/**
 * Port de sortie : indicateurs de santé du moteur de base de données (PostgreSQL).
 */
public interface SanteBasePort {

    /**
     * La lecture est possible (moteur PostgreSQL).
     */
    boolean disponible();

    Generale generale();

    /**
     * Plus grosses tables, de la plus grosse à la plus petite.
     */
    List<TableSante> plusGrossesTables();

    /**
     * Index jamais utilisés depuis la dernière remise à zéro des statistiques (hors clés primaires et uniques).
     */
    List<IndexInutilise> indexInutilises();

    /**
     * @param tailleOctets  taille de la base
     * @param cachePct      part des lectures servies par le cache
     * @param connexions    connexions ouvertes
     * @param connexionsMax maximum accepté
     * @param statsReset    remise à zéro des statistiques de PostgreSQL, si connue
     */
    record Generale(long tailleOctets, double cachePct, int connexions, int connexionsMax, Instant statsReset) {
    }
}
