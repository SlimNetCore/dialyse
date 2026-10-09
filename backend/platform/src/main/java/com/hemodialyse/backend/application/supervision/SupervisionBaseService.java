package com.hemodialyse.backend.application.supervision;

import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort;
import com.hemodialyse.backend.application.supervision.port.AnalysePlanPort.PlanGenerique;
import com.hemodialyse.backend.application.supervision.port.SanteBasePort;
import com.hemodialyse.backend.application.supervision.port.StatistiquesRequetesPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Supervision de la base pour le propriétaire de la plateforme : quelles requêtes coûtent le plus cher, afin de
 * décider quoi optimiser (index, requête, cache). Données techniques de toute la plateforme, sans lien avec un centre
 * (le texte des requêtes est normalisé : aucune donnée patient) ; l'accès est réservé au {@code SUPERADMIN}.
 * <p>
 * Pas de cache : la mesure est volatile par nature (règle de cache, AGENTS.md §6).
 */
@Service
public class SupervisionBaseService {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAX = 100;

    private static final Logger log = LoggerFactory.getLogger(SupervisionBaseService.class);

    private final StatistiquesRequetesPort statistiques;
    private final AnalysePlanPort plans;
    private final SanteBasePort sante;

    public SupervisionBaseService(StatistiquesRequetesPort statistiques, AnalysePlanPort plans, SanteBasePort sante) {
        this.statistiques = statistiques;
        this.plans = plans;
        this.sante = sante;
    }

    static RequeteAnalysee analyser(RequeteStatistique requete, double tempsTotalMs) {
        double part = tempsTotalMs > 0 ? requete.tempsTotalMs() * 100 / tempsTotalMs : 0;
        return new RequeteAnalysee(requete, part, NiveauRequete.evaluer(requete.tempsMoyenMs(), part),
                ConseilsRequete.evaluer(requete, part));
    }

    /**
     * Plan d'exécution d'une requête mesurée (sans l'exécuter), avec les tables lues en entier et l'avis sur un index.
     * Le texte analysé est relu côté base à partir de l'identifiant : le client ne fournit jamais de SQL.
     */
    public AnalyseRequete analyse(String queryId) {
        if (!statistiques.statut().disponible()) {
            throw new BusinessException("SUPERVISION_INDISPONIBLE",
                    "La mesure des requêtes n'est pas disponible sur cette base");
        }
        PlanGenerique plan = plans.planGenerique(queryId).orElseThrow(() -> new BusinessException(
                "SUPERVISION_REQUETE_INTROUVABLE", "Cette requête n'est plus dans les mesures (compteurs remis à zéro ?)"));
        return AnalyseurPlan.analyser(plan, plans::definitionsIndex, plans::lignesEstimees);
    }

    /**
     * Santé de la base (taille, cache, connexions, grosses tables) ; indisponible hors PostgreSQL.
     */
    public SanteBase sante() {
        if (!sante.disponible()) {
            return SanteBase.indisponible("BASE_NON_POSTGRESQL");
        }
        return DiagnosticSante.evaluer(sante.generale(), sante.plusGrossesTables(), sante.indexInutilises());
    }

    public StatutStatistiques statut() {
        return statistiques.statut();
    }

    /**
     * Classement paginé ; vide quand la mesure n'est pas disponible (voir {@link #statut()} pour la raison).
     */
    public PagedResult<RequeteAnalysee> requetes(TriRequetes tri, int page, int size) {
        int pageEffective = Math.max(page, 0);
        int tailleEffective = size <= 0 ? TAILLE_PAR_DEFAUT : Math.min(size, TAILLE_MAX);
        StatutStatistiques statut = statistiques.statut();
        if (!statut.disponible()) {
            return PagedResult.of(List.of(), 0, pageEffective, tailleEffective);
        }
        PagedResult<RequeteStatistique> brut = statistiques.classement(tri, pageEffective, tailleEffective);
        List<RequeteAnalysee> analysees = brut.items().stream()
                .map(r -> analyser(r, statut.tempsTotalMs()))
                .toList();
        return PagedResult.of(analysees, brut.total(), brut.page(), brut.size());
    }

    public void reinitialiser() {
        if (!statistiques.statut().disponible()) {
            throw new BusinessException("SUPERVISION_INDISPONIBLE",
                    "La mesure des requêtes n'est pas disponible sur cette base");
        }
        try {
            statistiques.reinitialiser();
        } catch (RuntimeException e) {
            log.warn("[SUPERVISION] Réinitialisation des compteurs impossible", e);
            throw new BusinessException("SUPERVISION_REINITIALISATION_IMPOSSIBLE",
                    "Réinitialisation des compteurs impossible (droits PostgreSQL insuffisants ?)");
        }
    }
}
