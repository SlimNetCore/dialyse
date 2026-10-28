package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;

import java.time.LocalDate;

/**
 * Port sortant : les notifications de l'optimisation (centre de notifications : journal durable et diffusion). Chaque notification
 * a une clé déterministe (exécution, destinataire, date) : déjà émise, elle n'est ni renvoyée ni dupliquée.
 */
public interface NotificationsDeLOptimisation {

    /** Une proposition appliquée a déplacé des patients ou des séances (médecin, secrétariat). */
    void seancesDeplacees(CentreId centre, Perimetre perimetre, int patients, int seancesTemporaires);

    /** La replanification de nuit a calculé une proposition qui mérite examen (administration). */
    void propositionASignaler(CentreId centre, ExecutionId execution, Perimetre perimetre, String motif, int valeur);

    /** Bilan de la nuit d'un centre : {@code PROPOSITIONS}, {@code RIEN}, {@code ECHEC} ou {@code REPORTEE}. */
    void bilanDeLaNuit(CentreId centre, String issue, int propositions, int echecs);

    /** Le roulement de cet infirmier a été modifié par une proposition appliquée. Clé : exécution et infirmier. */
    boolean roulementModifie(CentreId centre, InfirmierId infirmier, ExecutionId execution);

    /** Cet infirmier a été affecté comme remplaçant par une proposition appliquée. Clé : exécution, infirmier et date. */
    boolean remplacementAffecte(CentreId centre, InfirmierId infirmier, LocalDate date, SalleId salle, CreneauId creneau,
                                ExecutionId execution);
}
