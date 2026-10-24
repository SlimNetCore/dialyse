package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.application.stock.ValorisationArticlesService;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.GroupesDArticlesDesCentres.GroupeDArticlesPublie;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ValorisationDuStock;
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
 * société : par centre et consolidée. Les groupes sont définis par centre et lus par le langage publié du Stock
 * ({@link GroupesDArticlesDesCentres}, slice S2) ; un même nom (même clé : sans casse, accents ni espaces multiples) dans
 * plusieurs centres est consolidé en un seul groupe. Agrégats uniquement (aucune donnée patient), bornés aux centres de la
 * société.
 */
@Service
public class DirectionStockGroupesQueryService {

    private static final int MAX_YEARS = 5;

    private final DirectionDashboardQueryService dashboard;
    private final GroupesDArticlesDesCentres groupes;
    private final ValorisationArticlesService valorisation;

    public DirectionStockGroupesQueryService(
            DirectionDashboardQueryService dashboard, GroupesDArticlesDesCentres groupes,
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
        List<GroupeDArticlesPublie> tous = groupes.groupes(nomsCentres.keySet());

        // Valorisation de chaque article (une seule fois par article, même s'il figure dans plusieurs groupes)
        Map<UUID, Map<UUID, ValorisationDuStock>> parCentre = new HashMap<>();
        for (UUID centreId : nomsCentres.keySet()) {
            Set<UUID> articleIds = new HashSet<>();
            tous.stream().filter(g -> g.centre().equals(centreId)).forEach(g -> articleIds.addAll(g.articles()));
            if (articleIds.isEmpty()) continue;
            // Rejeu de l'historique mis en cache par centre, période et articles (voir ValorisationArticlesService)
            parCentre.put(centreId, valorisation.valoriser(centreId, articleIds, debut, finExclue));
        }

        // Consolidation par nom de groupe
        Map<String, GroupeAccumulateur> consolides = new LinkedHashMap<>();
        tous.stream()
                .sorted(Comparator.comparing(GroupeDArticlesPublie::cle).thenComparing(g -> nomsCentres.getOrDefault(g.centre(), "")))
                .forEach(g -> {
                    Map<UUID, ValorisationDuStock> valeurs = parCentre.getOrDefault(g.centre(), Map.of());
                    ValorisationDuStock somme = ValorisationDuStock.zero();
                    for (UUID articleId : g.articles()) {
                        ValorisationDuStock v = valeurs.get(articleId);
                        if (v != null) somme = somme.plus(v);
                    }
                    consolides.computeIfAbsent(g.cle(), k -> new GroupeAccumulateur(g.nom(), g.cle()))
                            .ajouter(new CentreGroupeStock(g.centre(), nomsCentres.get(g.centre()),
                                    g.articles().size(), Valeurs.of(somme)), somme);
                });

        List<GroupeStock> resultat = consolides.values().stream()
                .sorted(Comparator.comparing(GroupeAccumulateur::cle))
                .map(GroupeAccumulateur::build)
                .toList();
        return new StockGroupesOverview(societeId, start, end, OffsetDateTime.now(ZoneOffset.UTC), resultat);
    }

    private static final class GroupeAccumulateur {
        private final String nom;
        private final String cle;
        private final List<CentreGroupeStock> centres = new ArrayList<>();
        private ValorisationDuStock total = ValorisationDuStock.zero();

        private GroupeAccumulateur(String nom, String cle) {
            this.nom = nom;
            this.cle = cle;
        }

        private String cle() {
            return cle;
        }

        private void ajouter(CentreGroupeStock centre, ValorisationDuStock valorisation) {
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
        static Valeurs of(ValorisationDuStock v) {
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
