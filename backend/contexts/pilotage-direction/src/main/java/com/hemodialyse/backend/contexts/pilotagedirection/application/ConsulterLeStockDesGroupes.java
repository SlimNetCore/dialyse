package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesGroupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.pilotagedirection.application.StockDesGroupes.Valorisation;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;

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
 * Consulter la valorisation du stock des groupes d'articles (ex. « KIT CNAS ») sur une période, pour la direction d'une société : par centre
 * et consolidée (PL10b2, ADR 0152). Un même nom de groupe (même clé) dans plusieurs centres est consolidé en un seul groupe. Agrégats
 * uniquement (aucune donnée patient), bornés aux centres de la société. Fin de période par défaut : le jour d'Alger (KB-PL10b-4 corrigé).
 */
public final class ConsulterLeStockDesGroupes {

    private final SocietesSuivies societes;
    private final StockDesGroupes stock;
    private final HorlogeDuPilotage horloge;

    public ConsulterLeStockDesGroupes(SocietesSuivies societes, StockDesGroupes stock, HorlogeDuPilotage horloge) {
        this.societes = societes;
        this.stock = stock;
        this.horloge = horloge;
    }

    public StockGroupesOverview execute(UUID societeId, LocalDate from, LocalDate to) {
        Periode periode = Periode.demandee(from, to, horloge.aujourdhui());
        OffsetDateTime debut = periode.debut().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime finExclue = periode.fin().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<CentreDeLaSociete> centres = societes.societe(societeId).centres();
        Map<UUID, String> nomsCentres = new HashMap<>();
        centres.forEach(c -> nomsCentres.put(c.id(), c.nom()));
        List<GroupeDArticles> tous = stock.groupes(nomsCentres.keySet());

        // Valorisation de chaque article (une seule fois par article, même s'il figure dans plusieurs groupes)
        Map<UUID, Map<UUID, Valorisation>> parCentre = new HashMap<>();
        for (UUID centreId : nomsCentres.keySet()) {
            Set<UUID> articleIds = new HashSet<>();
            tous.stream().filter(g -> g.centre().equals(centreId)).forEach(g -> articleIds.addAll(g.articles()));
            if (articleIds.isEmpty()) continue;
            parCentre.put(centreId, stock.valoriser(centreId, articleIds, debut, finExclue));
        }

        Map<String, GroupeAccumulateur> consolides = new LinkedHashMap<>();
        tous.stream()
                .sorted(Comparator.comparing(GroupeDArticles::cle).thenComparing(g -> nomsCentres.getOrDefault(g.centre(), "")))
                .forEach(g -> {
                    Map<UUID, Valorisation> valeurs = parCentre.getOrDefault(g.centre(), Map.of());
                    Valorisation somme = Valorisation.zero();
                    for (UUID articleId : g.articles()) {
                        Valorisation v = valeurs.get(articleId);
                        if (v != null) somme = somme.plus(v);
                    }
                    consolides.computeIfAbsent(g.cle(), k -> new GroupeAccumulateur(g.nom(), g.cle()))
                            .ajouter(new CentreGroupeStock(g.centre(), nomsCentres.get(g.centre()), g.articles().size(), Valeurs.de(somme)), somme);
                });

        List<GroupeStock> resultat = consolides.values().stream()
                .sorted(Comparator.comparing(GroupeAccumulateur::cle))
                .map(GroupeAccumulateur::construire)
                .toList();
        return new StockGroupesOverview(societeId, periode.debut(), periode.fin(), horloge.maintenant(), resultat);
    }

    private static final class GroupeAccumulateur {
        private final String nom;
        private final String cle;
        private final List<CentreGroupeStock> centres = new ArrayList<>();
        private Valorisation total = Valorisation.zero();

        private GroupeAccumulateur(String nom, String cle) {
            this.nom = nom;
            this.cle = cle;
        }

        private String cle() {
            return cle;
        }

        private void ajouter(CentreGroupeStock centre, Valorisation valorisation) {
            centres.add(centre);
            total = total.plus(valorisation);
        }

        private GroupeStock construire() {
            int nbArticles = centres.stream().mapToInt(CentreGroupeStock::nbArticles).sum();
            return new GroupeStock(nom, centres.size(), nbArticles, Valeurs.de(total), List.copyOf(centres));
        }
    }

    /** Valeurs d'un groupe sur la période (DA). */
    public record Valeurs(BigDecimal valeurDebut, BigDecimal entrees, BigDecimal sorties, BigDecimal autresVariations, BigDecimal valeurFin) {
        static Valeurs de(Valorisation v) {
            return new Valeurs(v.valeurDebut(), v.entrees(), v.sorties(), v.autresVariations(), v.valeurFin());
        }
    }

    public record CentreGroupeStock(UUID centerId, String centreNom, int nbArticles, Valeurs valeurs) {
    }

    public record GroupeStock(String nom, int nbCentres, int nbArticles, Valeurs total, List<CentreGroupeStock> centres) {
    }

    public record StockGroupesOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt, List<GroupeStock> groupes) {
    }
}
