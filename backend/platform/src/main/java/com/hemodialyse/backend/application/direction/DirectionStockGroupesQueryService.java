package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.stock.ValorisationArticlesService;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.stock.model.GroupeArticle;
import com.hemodialyse.backend.domain.stock.port.GroupeArticleRepositoryPort;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator.Valorisation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Valorisation du stock des groupes d'articles (ex. « KIT CNAS ») sur une période, pour la direction d'une
 * société : par centre et consolidée. Les groupes sont définis par centre ; un même nom (insensible à la casse
 * et aux accents) dans plusieurs centres est consolidé en un seul groupe. Agrégats uniquement (aucune donnée
 * patient), bornés aux centres de la société.
 */
@Service
public class DirectionStockGroupesQueryService {

    private static final int MAX_YEARS = 5;

    private final DirectionDashboardQueryService dashboard;
    private final GroupeArticleRepositoryPort groupes;
    private final ValorisationArticlesService valorisation;

    public DirectionStockGroupesQueryService(
            DirectionDashboardQueryService dashboard, GroupeArticleRepositoryPort groupes,
            ValorisationArticlesService valorisation) {
        this.dashboard = dashboard;
        this.groupes = groupes;
        this.valorisation = valorisation;
    }

    public StockGroupesOverview groupes(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }
        OffsetDateTime debut = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime finExclue = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<CentreInfo> centres = dashboard.societe(societeId).centres();
        Map<UUID, String> nomsCentres = new HashMap<>();
        centres.forEach(c -> nomsCentres.put(c.id(), c.nom()));
        List<GroupeArticle> tous = groupes.findByCenters(nomsCentres.keySet());

        // Valorisation de chaque article (une seule fois par article, même s'il figure dans plusieurs groupes)
        Map<UUID, Map<UUID, Valorisation>> parCentre = new HashMap<>();
        for (UUID centreId : nomsCentres.keySet()) {
            Set<UUID> articleIds = new HashSet<>();
            tous.stream().filter(g -> g.centerId().equals(centreId)).forEach(g -> articleIds.addAll(g.articleIds()));
            if (articleIds.isEmpty()) continue;
            // Rejeu de l'historique mis en cache par centre, période et articles (voir ValorisationArticlesService)
            parCentre.put(centreId, valorisation.valoriser(centreId, articleIds, debut, finExclue));
        }

        // Consolidation par nom de groupe
        Map<String, GroupeAccumulateur> consolides = new LinkedHashMap<>();
        tous.stream()
                .sorted(Comparator.comparing(GroupeArticle::cle).thenComparing(g -> nomsCentres.getOrDefault(g.centerId(), "")))
                .forEach(g -> {
                    Map<UUID, Valorisation> valeurs = parCentre.getOrDefault(g.centerId(), Map.of());
                    Valorisation somme = Valorisation.zero();
                    for (UUID articleId : g.articleIds()) {
                        Valorisation v = valeurs.get(articleId);
                        if (v != null) somme = somme.plus(v);
                    }
                    consolides.computeIfAbsent(g.cle(), k -> new GroupeAccumulateur(g.nom()))
                            .ajouter(new CentreGroupeStock(g.centerId(), nomsCentres.get(g.centerId()),
                                    g.articleIds().size(), Valeurs.of(somme)), somme);
                });

        List<GroupeStock> resultat = consolides.values().stream()
                .map(GroupeAccumulateur::build)
                .sorted(Comparator.comparing(g -> GroupeArticle.cleNom(g.nom())))
                .toList();
        return new StockGroupesOverview(societeId, start, end, OffsetDateTime.now(ZoneOffset.UTC), resultat);
    }

    private static final class GroupeAccumulateur {
        private final String nom;
        private final List<CentreGroupeStock> centres = new ArrayList<>();
        private Valorisation total = Valorisation.zero();

        private GroupeAccumulateur(String nom) {
            this.nom = nom;
        }

        private void ajouter(CentreGroupeStock centre, Valorisation valorisation) {
            centres.add(centre);
            total = total.plus(valorisation);
        }

        private GroupeStock build() {
            int nbArticles = centres.stream().mapToInt(CentreGroupeStock::nbArticles).sum();
            return new GroupeStock(nom, centres.size(), nbArticles, Valeurs.of(total), List.copyOf(centres));
        }
    }

    /**
     * Valeurs d'un groupe sur la période (DA) : stock au début, entrées, sorties valorisées au PMP, autres
     * variations (inventaires, ajustements, arrondis) et stock à la fin.
     */
    public record Valeurs(
            BigDecimal valeurDebut,
            BigDecimal entrees,
            BigDecimal sorties,
            BigDecimal autresVariations,
            BigDecimal valeurFin
    ) {
        static Valeurs of(Valorisation v) {
            return new Valeurs(v.valeurDebut(), v.entrees(), v.sorties(), v.autresVariations(), v.valeurFin());
        }
    }

    public record CentreGroupeStock(UUID centerId, String centreNom, int nbArticles, Valeurs valeurs) {
    }

    public record GroupeStock(String nom, int nbCentres, int nbArticles, Valeurs total,
                              List<CentreGroupeStock> centres) {
    }

    public record StockGroupesOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                                       List<GroupeStock> groupes) {
    }
}
