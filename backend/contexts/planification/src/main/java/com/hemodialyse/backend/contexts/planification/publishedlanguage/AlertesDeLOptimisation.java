package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Port consommé par la Planification (slice P8), fourni par le legacy (centre de notifications : journal durable et diffusion temps
 * réel). Les alertes destinées aux infirmiers concernés par une proposition appliquée ont une clé déterministe (type, exécution,
 * infirmier, date) : déjà émises, elles ne sont ni renvoyées ni dupliquées (livraison au moins une fois, plusieurs instances du
 * serveur). Chaque méthode à clé dit si l'alerte a été émise par cet appel.
 */
public interface AlertesDeLOptimisation {

    /**
     * Une proposition appliquée a déplacé des patients ou des séances (médecin, secrétariat).
     */
    void seancesDeplacees(UUID centre, String perimetre, int patients, int seancesTemporaires);

    /**
     * La replanification de nuit a calculé une proposition qui mérite examen (administration).
     */
    void propositionASignaler(UUID centre, UUID execution, String perimetre, String motif, int valeur);

    /**
     * Bilan de la nuit d'un centre (administration) : {@code PROPOSITIONS}, {@code RIEN}, {@code ECHEC} ou {@code REPORTEE}.
     */
    void bilanDeLaNuit(UUID centre, String issue, int propositions, int echecs);

    /**
     * Le roulement de cet infirmier a changé. Clé : exécution et infirmier. Alerte PERSONNELLE : adressée au {@code compte} lié à sa
     * fiche (nul s'il n'en a pas) et à l'administration, jamais aux autres infirmiers.
     */
    boolean roulementModifie(UUID centre, UUID infirmier, UUID compte, UUID execution);

    /**
     * Cet infirmier a été affecté comme remplaçant sur une case. Clé : exécution, infirmier, date, salle et créneau. Alerte
     * PERSONNELLE, comme {@link #roulementModifie}.
     */
    boolean remplacementAffecte(UUID centre, UUID infirmier, UUID compte, LocalDate date, UUID salle, UUID creneau, UUID execution);
}
