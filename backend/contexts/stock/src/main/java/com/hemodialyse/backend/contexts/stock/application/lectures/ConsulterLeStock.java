package com.hemodialyse.backend.contexts.stock.application.lectures;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.groupes.GroupeDArticles;
import com.hemodialyse.backend.contexts.stock.domain.lectures.CalculDuPmp;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.AlerteDeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleClasse;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.LigneDeTracabilite;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.PointDeTendance;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.TriDuClassement;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;

import java.math.BigDecimal;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lectures du Stock d'un centre (RG-STK-042 à 044, 060, 062) : tableau de bord, stock valorisé, alertes, traçabilité d'un lot,
 * explication du PMP. Aucune écriture.
 */
public final class ConsulterLeStock {

    /**
     * Fenêtre d'alerte de péremption, en jours.
     */
    public static final int JOURS_AVANT_PEREMPTION = 30;
    private static final int[] JOURS_AUTORISES = {7, 30, 90};
    private static final int JOURS_PAR_DEFAUT = 30;
    private static final int TOP_PAR_DEFAUT = 10;
    private static final int TOP_MAX = 50;

    private static final String METHODE =
            "PMP (Poids Moyen Pondere). A chaque ENTREE : PMP = (valeur du stock + quantite recue x prix unitaire) "
                    + "/ (quantite en stock + quantite recue). Une SORTIE valorise au PMP courant et ne modifie pas le PMP. "
                    + "Toute correction d'une reception passee declenche un recalcul en cascade de tout l'historique.";

    private final LecturesDuStock lectures;
    private final Articles articles;
    private final HorlogeDesCentres horloge;

    public ConsulterLeStock(LecturesDuStock lectures, Articles articles, HorlogeDesCentres horloge) {
        this.lectures = lectures;
        this.articles = articles;
        this.horloge = horloge;
    }

    private static int joursRetenus(int jours) {
        for (int autorise : JOURS_AUTORISES) {
            if (autorise == jours) {
                return jours;
            }
        }
        return JOURS_PAR_DEFAUT;
    }

    private static int topRetenu(int top) {
        return top <= 0 ? TOP_PAR_DEFAUT : Math.min(top, TOP_MAX);
    }

    private static BigDecimal somme(List<BigDecimal> valeurs) {
        return valeurs.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Tableau de bord : fenêtre de 7, 30 ou 90 jours (30 sinon), 1 à 50 articles (10 si ≤ 0, 50 au-delà), tri par valeur ou
     * quantité ; jours sans mouvement comblés à zéro, aujourd'hui inclus.
     */
    public TableauDeBord tableauDeBord(CentreId centre, int jours, int top, TriDuClassement tri) {
        int fenetre = joursRetenus(jours);
        int limite = topRetenu(top);
        TriDuClassement triRetenu = tri != null ? tri : TriDuClassement.VALEUR;
        List<ArticleValorise> stock = stockValorise(centre);
        LocalDate premierJour = horloge.aujourdHui(centre).minusDays(fenetre - 1L);
        return new TableauDeBord(fenetre, limite, triRetenu, somme(stock.stream().map(ArticleValorise::quantite).toList()),
                somme(stock.stream().map(ArticleValorise::valeur).toList()),
                comblerLesJours(lectures.tendance(centre, premierJour), premierJour, fenetre),
                lectures.classement(centre, premierJour, limite, triRetenu));
    }

    private static List<PointDeTendance> comblerLesJours(List<PointDeTendance> points, LocalDate premierJour, int jours) {
        Map<LocalDate, PointDeTendance> parJour = new HashMap<>();
        points.forEach(p -> parJour.put(p.jour(), p));
        List<PointDeTendance> complet = new ArrayList<>(jours);
        for (int i = 0; i < jours; i++) {
            LocalDate jour = premierJour.plusDays(i);
            complet.add(parJour.getOrDefault(jour, new PointDeTendance(jour, BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return complet;
    }

    /**
     * Articles actifs triés par libellé sans tenir compte de la casse ni des accents (KB-STK-28 corrigé).
     */
    public List<ArticleValorise> stockValorise(CentreId centre) {
        return lectures.articlesActifsValorises(centre).stream()
                .sorted(Comparator.comparing((ArticleValorise a) -> GroupeDArticles.cle(a.libelle()))
                        .thenComparing(ArticleValorise::code, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Péremptions (≤ 30 jours, lots déjà périmés inclus), puis ruptures et seuils.
     */
    public List<AlerteDeStock> alertes(CentreId centre) {
        List<AlerteDeStock> alertes = new ArrayList<>(lectures.peremptions(centre,
                horloge.aujourdHui(centre).plusDays(JOURS_AVANT_PEREMPTION)));
        alertes.addAll(lectures.ruptures(centre));
        return alertes;
    }

    public List<LigneDeTracabilite> tracabilite(CentreId centre, UUID lotId) {
        return lectures.tracabilite(centre, lotId);
    }

    /**
     * @throws ArticleIntrouvable article inconnu ou d'un autre centre (422, comme le référentiel)
     */
    public ExplicationDuPmp expliquerLePmp(CentreId centre, UUID articleId) {
        Article article = articles.parId(centre, articleId).orElseThrow(ArticleIntrouvable::new);
        List<CalculDuPmp.Etape> etapes = CalculDuPmp.expliquer(lectures.mouvementsDeLArticle(centre, articleId));
        Map<UUID, String> receptions = new HashMap<>();
        Map<String, String> sorties = new HashMap<>();
        List<EtapeExpliquee> expliquees = new ArrayList<>();
        for (CalculDuPmp.Etape e : etapes) {
            MouvementDeStock m = e.mouvement();
            expliquees.add(new EtapeExpliquee(m.creeLe(), piece(centre, m, receptions, sorties),
                    m.creeLe() != null ? m.creeLe().toLocalDate().toString() : null, m.type().name(), m.quantite(),
                    m.prixUnitaire(), e.avant().quantite(), e.avant().valeur(), e.avant().pmp(), e.apres().quantite(),
                    e.apres().valeur(), e.apres().pmp(), e.formule()));
        }
        CalculDuPmp.Etat fin = etapes.isEmpty() ? CalculDuPmp.Etat.vide() : etapes.get(etapes.size() - 1).apres();
        return new ExplicationDuPmp(article.id(), article.fiche().code(), article.fiche().libelle(), METHODE, expliquees,
                fin.quantite(), fin.valeur(), fin.pmp());
    }

    private String piece(CentreId centre, MouvementDeStock m, Map<UUID, String> receptions, Map<String, String> sorties) {
        switch (m.type()) {
            case INVENTAIRE:
                return "INV";
            case ENTREE:
                return m.lotId() == null ? "BR" : receptions.computeIfAbsent(m.lotId(),
                        lot -> lectures.referenceDeReception(centre, lot).orElse("BR"));
            case SORTIE:
                if (m.seanceId() == null) {
                    return "BS";
                }
                String cle = m.seanceId() + ":" + m.lotId() + ":" + m.articleId();
                return sorties.computeIfAbsent(cle, k -> m.lotId() == null ? "BS"
                        : lectures.referenceDeSortie(centre, m.seanceId(), m.lotId(), m.articleId()).orElse("BS"));
            default:
                return "AJUST";
        }
    }

    public record TableauDeBord(int jours, int top, TriDuClassement tri, BigDecimal quantiteTotale,
                                BigDecimal valeurTotale,
                                List<PointDeTendance> tendance, List<ArticleClasse> classement) {
    }

    public record EtapeExpliquee(OffsetDateTime date, String piece, String datePiece, String type, BigDecimal quantite,
                                 BigDecimal prixUnitaire, BigDecimal quantiteAvant, BigDecimal valeurAvant,
                                 BigDecimal pmpAvant, BigDecimal quantiteApres, BigDecimal valeurApres,
                                 BigDecimal pmpApres, String formule) {
    }

    public record ExplicationDuPmp(UUID articleId, String code, String libelle, String methode,
                                   List<EtapeExpliquee> etapes,
                                   BigDecimal quantiteFinale, BigDecimal valeurFinale, BigDecimal pmpFinal) {
    }
}


