package com.hemodialyse.backend.domain.planning.optimisation.model;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.Instant;
import java.util.UUID;

/**
 * Exécution d'une optimisation d'un centre : paramètres, avancement, résultat. Cycle de vie : {@code EN_COURS} puis
 * {@code TERMINEE} (y compris arrêtée avant son terme : la meilleure solution est conservée) ou {@code ECHEC} ; une exécution terminée peut être {@code appliquee}, une seule
 * fois. {@code empreinte} identifie l'état du centre au lancement : une proposition ne s'applique que si le centre n'a
 * pas changé depuis.
 */
public record RunOptimisation(
        UUID id,
        UUID centerId,
        StatutRun statut,
        ParametresOptimisation parametres,
        Instant creeLe,
        Instant termineLe,
        String lancePar,
        String phase,
        String score,
        String empreinte,
        ResultatOptimisation.Resume resume,
        ResultatOptimisation resultat,
        String erreur,
        Instant appliqueLe
) {

    public enum StatutRun {
        EN_COURS,
        TERMINEE,
        ECHEC
    }

    public RunOptimisation {
        if (id == null) throw new IllegalArgumentException("Identifiant requis");
        if (centerId == null) throw new IllegalArgumentException("Centre requis");
        if (statut == null) throw new IllegalArgumentException("Statut requis");
        if (parametres == null) throw new IllegalArgumentException("Paramètres requis");
    }

    public static RunOptimisation demarrer(UUID centerId, ParametresOptimisation parametres, String lancePar,
                                           String empreinte, Instant maintenant) {
        return new RunOptimisation(UUID.randomUUID(), centerId, StatutRun.EN_COURS, parametres, maintenant, null,
                lancePar, null, null, empreinte, null, null, null, null);
    }

    public boolean enCours() {
        return statut == StatutRun.EN_COURS;
    }

    public RunOptimisation progression(String phase, String score) {
        return new RunOptimisation(id, centerId, statut, parametres, creeLe, termineLe, lancePar, phase, score,
                empreinte, resume, resultat, erreur, appliqueLe);
    }

    public RunOptimisation terminer(ResultatOptimisation resultat, String score, Instant maintenant) {
        return new RunOptimisation(id, centerId, StatutRun.TERMINEE, parametres, creeLe, maintenant, lancePar, phase,
                score, empreinte, resultat.resume(), resultat, null, appliqueLe);
    }

    public RunOptimisation echouer(String erreur, Instant maintenant) {
        return new RunOptimisation(id, centerId, StatutRun.ECHEC, parametres, creeLe, maintenant, lancePar, phase,
                score, empreinte, null, null, erreur, appliqueLe);
    }

    /**
     * Marque la proposition comme appliquée.
     *
     * @throws BusinessException {@code OPTIMISATION_NON_APPLICABLE} si l'exécution n'est pas terminée avec un résultat,
     *                           {@code OPTIMISATION_DEJA_APPLIQUEE} si elle l'est déjà
     */
    public RunOptimisation appliquer(Instant maintenant) {
        if (statut != StatutRun.TERMINEE || resultat == null) {
            throw new BusinessException("OPTIMISATION_NON_APPLICABLE",
                    "Seule une optimisation terminée avec un résultat peut être appliquée");
        }
        if (appliqueLe != null) {
            throw new BusinessException("OPTIMISATION_DEJA_APPLIQUEE", "Cette optimisation a déjà été appliquée");
        }
        return new RunOptimisation(id, centerId, statut, parametres, creeLe, termineLe, lancePar, phase, score,
                empreinte, resume, resultat, erreur, maintenant);
    }
}
