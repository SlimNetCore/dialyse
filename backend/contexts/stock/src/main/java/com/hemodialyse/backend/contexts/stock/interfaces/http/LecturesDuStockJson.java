package com.hemodialyse.backend.contexts.stock.interfaces.http;

import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.EtapeExpliquee;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.ExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.TableauDeBord;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.AlerteDeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleClasse;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.LigneDeTracabilite;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.PointDeTendance;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.TriDuClassement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JSON des lectures du Stock, identique au legacy ({@code StockDashboardAnalytics}, {@code StockValoriseItem},
 * {@code AlerteStock}, {@code TracabiliteItem}, {@code PmpExplanation}) : mêmes champs, mêmes noms.
 */
final class LecturesDuStockJson {

    private LecturesDuStockJson() {
    }

    static String tri(TriDuClassement tri) {
        return tri == TriDuClassement.QUANTITE ? "QUANTITY" : "VALUE";
    }

    record Tendance(LocalDate date, BigDecimal quantite, BigDecimal valeur) {
        static Tendance depuis(PointDeTendance p) {
            return new Tendance(p.jour(), p.quantite(), p.valeur());
        }
    }

    record Classe(UUID articleId, String code, String libelle, String unite, BigDecimal quantite, BigDecimal valeur) {
        static Classe depuis(ArticleClasse a) {
            return new Classe(a.articleId(), a.code(), a.libelle(), a.unite(), a.quantite(), a.valeur());
        }
    }

    record Tableau(int days, int topN, String sortBy, BigDecimal stockQuantiteTotale, BigDecimal stockValeurTotale,
                   List<Tendance> trend, List<Classe> topArticles) {
        static Tableau depuis(TableauDeBord t) {
            return new Tableau(t.jours(), t.top(), tri(t.tri()), t.quantiteTotale(), t.valeurTotale(),
                    t.tendance().stream().map(Tendance::depuis).toList(), t.classement().stream().map(Classe::depuis).toList());
        }
    }

    record Valorise(UUID articleId, String code, String libelle, String unite, BigDecimal quantite,
                    BigDecimal pmpCourant,
                    BigDecimal valeur) {
        static Valorise depuis(ArticleValorise a) {
            return new Valorise(a.articleId(), a.code(), a.libelle(), a.unite(), a.quantite(), a.pmpCourant(), a.valeur());
        }
    }

    record Alerte(String type, UUID articleId, String articleLibelle, UUID lotId, String numeroLot,
                  LocalDate datePeremption,
                  BigDecimal quantite, BigDecimal seuil) {
        static Alerte depuis(AlerteDeStock a) {
            return new Alerte(a.type(), a.articleId(), a.articleLibelle(), a.lotId(), a.numeroLot(), a.datePeremption(),
                    a.quantite(), a.seuil());
        }
    }

    record Tracabilite(UUID lotId, String numeroLot, UUID articleId, String articleLibelle, UUID seanceId,
                       UUID patientId,
                       LocalDate dateSortie, BigDecimal quantite) {
        static Tracabilite depuis(LigneDeTracabilite l) {
            return new Tracabilite(l.lotId(), l.numeroLot(), l.articleId(), l.articleLibelle(), l.seanceId(), l.patientId(),
                    l.dateSortie(), l.quantite());
        }
    }

    record Etape(OffsetDateTime date, String piece, String datePiece, String type, BigDecimal quantite,
                 BigDecimal prixUnitaire, BigDecimal quantiteAvant, BigDecimal valeurAvant, BigDecimal pmpAvant,
                 BigDecimal quantiteApres, BigDecimal valeurApres, BigDecimal pmpApres, String formule) {
        static Etape depuis(EtapeExpliquee e) {
            return new Etape(e.date(), e.piece(), e.datePiece(), e.type(), e.quantite(), e.prixUnitaire(), e.quantiteAvant(),
                    e.valeurAvant(), e.pmpAvant(), e.quantiteApres(), e.valeurApres(), e.pmpApres(), e.formule());
        }
    }

    record Explication(UUID articleId, String code, String libelle, String methode, List<Etape> etapes,
                       BigDecimal quantiteFinale, BigDecimal valeurFinale, BigDecimal pmpFinal) {
        static Explication depuis(ExplicationDuPmp e) {
            return new Explication(e.articleId(), e.code(), e.libelle(), e.methode(),
                    e.etapes().stream().map(Etape::depuis).toList(), e.quantiteFinale(), e.valeurFinale(), e.pmpFinal());
        }
    }
}

