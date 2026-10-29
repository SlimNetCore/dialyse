package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;

import java.util.Optional;

/**
 * Port sortant : les exécutions d'optimisation (un dépôt par racine d'agrégat), toujours bornées au centre. Les transitions du
 * calcul (progression, fin) sont chacune une écriture ; « une seule en cours par centre » se juge sous le verrou du centre
 * ({@link VerrouDesPlaces}), pris par le cas d'usage avant {@link #enCours}.
 */
public interface ExecutionsRepository {

    /**
     * Crée l'exécution lancée ({@code EN_COURS}), à la charge de l'instance du serveur qui l'a lancée (jeton propre au dépôt) ;
     * son battement de cœur démarre à son lancement.
     */
    void creer(ExecutionDOptimisation execution);

    /**
     * Enregistre la transition d'une exécution, SEULEMENT si elle est encore dans le statut {@code avant} en base (mise à jour
     * conditionnelle, jamais de réinsertion) ; pour un calcul {@code EN_COURS}, seule l'instance qui l'a lancé peut le faire
     * avancer. Faux si la transition n'a pas eu lieu (exécution supprimée, déjà interrompue par la reprise, déjà terminée).
     */
    boolean transiter(ExecutionDOptimisation apres, ExecutionDOptimisation.Statut avant);

    /**
     * Signe de vie de cette instance : renouvelle le battement de cœur de ses calculs en cours.
     */
    void battre(java.time.Instant maintenant);

    /** L'exécution du centre ; vide si elle est inconnue ou d'un autre centre. */
    Optional<ExecutionDOptimisation> parId(CentreId centre, ExecutionId id);

    /** Le calcul en cours du centre, s'il y en a un. */
    Optional<ExecutionDOptimisation> enCours(CentreId centre);

    /** Une page de l'historique du centre, de la plus récente à la plus ancienne. */
    PageDeResultats<ExecutionDOptimisation> page(CentreId centre, Pagination pagination);

    /** Ne garde que les {@code aGarder} exécutions les plus récentes du centre (P20, 20). */
    void purger(CentreId centre, int aGarder);

    /** Supprime l'exécution du centre ; faux si elle est inconnue ou d'un autre centre. */
    boolean supprimer(CentreId centre, ExecutionId id);

    /**
     * Reprise : les calculs ORPHELINS (en cours dont le battement de cœur a plus de {@code delai}, ou n'en a jamais eu) échouent
     * avec ce motif ; ceux d'une instance vivante ne sont jamais touchés. Rend le nombre d'exécutions interrompues.
     */
    int interrompreLesOrphelines(String motif, java.time.Instant maintenant, java.time.Duration delai);
}
