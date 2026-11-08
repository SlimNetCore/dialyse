package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié de la Séance ({@code seance.v1}, ADR 0098, SE7d) : les constantes saisies au volet paramédical de chaque séance d'un
 * patient, lues pour l'historique des constantes du Dossier médical. Toutes les séances du patient DANS le centre, quel que soit leur
 * statut ; aucune lecture de la fiche du patient. Lecture seule.
 */
public interface ConstantesDesSeancesPubliees {

    /** Les sept mesures du volet paramédical du même centre que la séance, brutes ; toutes nulles sans volet. */
    record ConstantesDeSeancePubliees(UUID seance, LocalDate date, BigDecimal poidsAvantKg, BigDecimal poidsApresKg, String tensionAvant,
                                      String tensionApres, Integer debitSangMlMin, BigDecimal ultrafiltrationMl, Integer dureeMinutes) {
    }

    /** Le nombre de séances du patient dans le centre, tous statuts. */
    long nombreDeSeances(UUID centre, UUID patient);

    /**
     * Une page de {@code taille} séances à partir du rang {@code rang} (0 = la première), ordonnées par date de séance décroissante, puis
     * création décroissante, puis identifiant décroissant (octets non signés). Une création nulle se classe selon le SGBD (KB-SEA-79,
     * reproduit) : en DERNIER sur H2, en PREMIER sur PostgreSQL.
     */
    List<ConstantesDeSeancePubliees> page(UUID centre, UUID patient, int taille, long rang);
}
