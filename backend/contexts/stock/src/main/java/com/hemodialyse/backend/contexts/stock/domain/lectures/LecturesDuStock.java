package com.hemodialyse.backend.contexts.stock.domain.lectures;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : lectures du Stock d'un centre (aucune écriture) — tendance et classement des mouvements, stock valorisé,
 * alertes, traçabilité des lots, historique d'un article pour le rejeu du PMP. Toute lecture est bornée au centre.
 */
public interface LecturesDuStock {

    /**
     * Mouvements (hors inventaires) du premier jour de la fenêtre à aujourd'hui, regroupés par jour (jours sans mouvement
     * absents) : quantité signée (sorties négatives) et valeur au prix du mouvement, sinon au PMP du jour, sinon au PMP
     * courant de l'article.
     */
    List<PointDeTendance> tendance(CentreId centre, LocalDate premierJour);

    /**
     * Articles les plus mouvementés depuis le premier jour : quantité absolue et valeur, triés puis limités.
     */
    List<ArticleClasse> classement(CentreId centre, LocalDate premierJour, int limite, TriDuClassement tri);

    /**
     * Articles actifs du centre avec quantité et PMP courant, triés par code (l'application les ordonne par libellé).
     */
    List<ArticleValorise> articlesActifsValorises(CentreId centre);

    /**
     * Lots à quantité restante positive périmés ou périssant au plus tard à la date, triés par date de péremption.
     */
    List<AlerteDeStock> peremptions(CentreId centre, LocalDate auPlusTard);

    /**
     * Articles actifs en rupture (quantité ≤ 0) ou sous leur seuil, triés par libellé.
     */
    List<AlerteDeStock> ruptures(CentreId centre);

    /**
     * Sorties qui ont consommé le lot, de la plus récente à la plus ancienne.
     */
    List<LigneDeTracabilite> tracabilite(CentreId centre, UUID lotId);

    /**
     * Mouvements de l'article du rejeu du PMP (hors ceux d'un inventaire clôturé), dans l'ordre du rejeu.
     */
    List<MouvementDeStock> mouvementsDeLArticle(CentreId centre, UUID articleId);

    /**
     * Référence du bon de réception d'un lot.
     */
    Optional<String> referenceDeReception(CentreId centre, UUID lotId);

    /**
     * Référence du bon de sortie d'une séance pour ce lot et cet article.
     */
    Optional<String> referenceDeSortie(CentreId centre, UUID seanceId, UUID lotId, UUID articleId);

    enum TriDuClassement {
        VALEUR, QUANTITE;

        /**
         * « QUANTITY » (sans casse) trie par quantité ; tout autre texte, y compris vide, par valeur.
         */
        public static TriDuClassement depuis(String texte) {
            return texte != null && texte.trim().equalsIgnoreCase("QUANTITY") ? QUANTITE : VALEUR;
        }
    }

    record PointDeTendance(LocalDate jour, BigDecimal quantite, BigDecimal valeur) {
    }

    record ArticleClasse(UUID articleId, String code, String libelle, String unite, BigDecimal quantite,
                         BigDecimal valeur) {
    }

    record ArticleValorise(UUID articleId, String code, String libelle, String unite, BigDecimal quantite,
                           BigDecimal pmpCourant, BigDecimal valeur) {
    }

    /**
     * @param type {@code PEREMPTION}, {@code RUPTURE} ou {@code SEUIL}
     */
    record AlerteDeStock(String type, UUID articleId, String articleLibelle, UUID lotId, String numeroLot,
                         LocalDate datePeremption, BigDecimal quantite, BigDecimal seuil) {
    }

    record LigneDeTracabilite(UUID lotId, String numeroLot, UUID articleId, String articleLibelle, UUID seanceId,
                              UUID patientId, LocalDate dateSortie, BigDecimal quantite) {
    }
}

