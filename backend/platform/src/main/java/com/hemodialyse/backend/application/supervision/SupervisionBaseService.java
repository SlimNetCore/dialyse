package com.hemodialyse.backend.application.supervision;

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

    public SupervisionBaseService(StatistiquesRequetesPort statistiques) {
        this.statistiques = statistiques;
    }

    static RequeteAnalysee analyser(RequeteStatistique requete, double tempsTotalMs) {
        double part = tempsTotalMs > 0 ? requete.tempsTotalMs() * 100 / tempsTotalMs : 0;
        return new RequeteAnalysee(requete, part, NiveauRequete.evaluer(requete.tempsMoyenMs(), part));
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
