package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0099, SE7e) : les séances d'un patient dans un centre et les mesures de leur volet
 * paramédical, lues pour les statistiques paramédicales du patient (écran et export PDF). Lecture seule, aucune transaction propre.
 * <p>
 * Comportement reproduit À L'IDENTIQUE, rien n'est corrigé :
 * <ul>
 *   <li>TOUTES les séances du patient dans le centre, QUEL QUE SOIT leur statut (ANNULEE, CREE, {@code validee} comprises) ; le volet est
 *   joint sur la séance SEULE, sans le centre (un volet posé dans un autre centre est lu) : KB-SEA-85 ;</li>
 *   <li>période optionnelle : {@code du} et {@code au} nuls = sans borne ; bornes incluses ;</li>
 *   <li>moyennes : {@code null} si aucune mesure ; précision du SGBD (12 décimales sur H2, celle du NUMERIC sur PostgreSQL) : seules 10
 *   décimales sont GARANTIES (KB-SEA-87, clos le 2026-11-10, ADR 0117) ;</li>
 *   <li>ordre par date de séance, puis création (la plus ancienne d'abord, création inconnue en dernier), puis identifiant : déterministe
 *   (KB-SEA-86 corrigé le 2026-11-10, ADR 0117).</li>
 * </ul>
 */
public interface StatistiquesDesSeancesPubliees {

    /** Le nombre de séances (E6). */
    long nombreDeSeances(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** La moyenne du poids avant séance, {@code null} sans mesure (E7). */
    Double moyenneDuPoidsAvant(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** La moyenne du poids après séance, {@code null} sans mesure (E7). */
    Double moyenneDuPoidsApres(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** La moyenne de l'ultrafiltration réelle (saisie, sinon perte de poids x 1000), {@code null} sans mesure (E7). */
    Double moyenneDeLUltrafiltration(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /**
     * L'évolution du poids, une ligne par séance (E8) : date de séance, poids avant et après, poids sec en vigueur le jour de la séance
     * (dernière prescription du patient AU centre portant un poids sec, datée au plus tard du jour, la plus récemment mise à jour à date
     * égale), ultrafiltration réelle. Lignes du SGBD : les clés sont TOUJOURS en minuscules, sur H2 comme sur PostgreSQL (contrat de production,
     * KB-SEA-86 corrigé le 2026-11-10, ADR 0117), les valeurs gardent leur type JDBC. Cette forme est servie
     * telle quelle en JSON par la plateforme : la changer changerait le contrat HTTP.
     */
    List<Map<String, Object>> evolutionDuPoids(UUID centre, UUID patient, LocalDate du, LocalDate au);

    /** L'évolution de la tension, une ligne par séance (E9) : date et tensions avant et après BRUTES (texte « 130/80 », nul sans volet). */
    List<TensionsDeSeancePubliees> evolutionDeLaTension(UUID centre, UUID patient, LocalDate du, LocalDate au);

    record TensionsDeSeancePubliees(LocalDate date, String tensionAvant, String tensionApres) {
    }
}
