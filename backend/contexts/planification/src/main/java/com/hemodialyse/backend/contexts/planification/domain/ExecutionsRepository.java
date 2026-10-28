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

    /** Crée ou remplace l'exécution (même identité). */
    void enregistrer(ExecutionDOptimisation execution);

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

    /** Au démarrage : tout calcul encore en cours (tous centres) échoue avec ce motif ; rend le nombre d'exécutions touchées. */
    int interrompreLesEnCours(String motif);
}
