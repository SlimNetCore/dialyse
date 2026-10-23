package com.hemodialyse.backend.contexts.stock.application.lectures;

import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.ExplicationDuPmp;
import com.hemodialyse.backend.contexts.stock.application.lectures.ConsulterLeStock.TableauDeBord;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.AlerteDeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleClasse;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.ArticleValorise;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.LigneDeTracabilite;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.PointDeTendance;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock.TriDuClassement;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lectures du Stock (RG-STK-042 à 044, 060, 062) : normalisation des paramètres, jours comblés, tri, alertes, explication du PMP.
 */
class ConsulterLeStockTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    private final List<LocalDate> fenetres = new ArrayList<>();
    private final List<Integer> limites = new ArrayList<>();
    private final List<TriDuClassement> tris = new ArrayList<>();
    private final List<LocalDate> peremptions = new ArrayList<>();
    private List<ArticleValorise> valorises = List.of();
    private List<PointDeTendance> tendance = List.of();
    private List<MouvementDeStock> mouvements = List.of();
    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();

    private final LecturesDuStock lectures = new LecturesDuStock() {
        @Override
        public List<PointDeTendance> tendance(CentreId centre, LocalDate premierJour) {
            fenetres.add(premierJour);
            return tendance;
        }

        @Override
        public List<ArticleClasse> classement(CentreId centre, LocalDate premierJour, int limite, TriDuClassement tri) {
            limites.add(limite);
            tris.add(tri);
            return List.of();
        }

        @Override
        public List<ArticleValorise> articlesActifsValorises(CentreId centre) {
            return valorises;
        }

        @Override
        public List<AlerteDeStock> peremptions(CentreId centre, LocalDate auPlusTard) {
            peremptions.add(auPlusTard);
            return List.of(new AlerteDeStock("PEREMPTION", null, "A", null, "L", auPlusTard, BigDecimal.ONE, null));
        }

        @Override
        public List<AlerteDeStock> ruptures(CentreId centre) {
            return List.of(new AlerteDeStock("RUPTURE", null, "B", null, null, null, BigDecimal.ZERO, BigDecimal.ZERO));
        }

        @Override
        public List<LigneDeTracabilite> tracabilite(CentreId centre, UUID lotId) {
            return List.of();
        }

        @Override
        public List<MouvementDeStock> mouvementsDeLArticle(CentreId centre, UUID articleId) {
            return mouvements;
        }

        @Override
        public Optional<String> referenceDeReception(CentreId centre, UUID lotId) {
            return Optional.of("BR-00001");
        }

        @Override
        public Optional<String> referenceDeSortie(CentreId centre, UUID seanceId, UUID lotId, UUID articleId) {
            return Optional.empty();
        }
    };

    private final ConsulterLeStock stock = new ConsulterLeStock(lectures, articles, HORLOGE);

    private static ArticleValorise valorise(String libelle, String code, String quantite, String pmp) {
        BigDecimal q = new BigDecimal(quantite);
        BigDecimal p = new BigDecimal(pmp);
        return new ArticleValorise(UUID.randomUUID(), code, libelle, "piece", q, p, q.multiply(p));
    }

    @Test
    void les_jours_et_le_top_sont_normalises_et_le_tri_inconnu_donne_la_valeur() {
        stock.tableauDeBord(CENTRE, 5, 0, null);
        stock.tableauDeBord(CENTRE, 90, 500, TriDuClassement.depuis("quantity"));
        stock.tableauDeBord(CENTRE, 7, 3, TriDuClassement.depuis("PRIX"));
        assertEquals(List.of(10, 50, 3), limites);
        assertEquals(List.of(TriDuClassement.VALEUR, TriDuClassement.QUANTITE, TriDuClassement.VALEUR), tris);
        assertEquals(List.of(LocalDate.parse("2026-09-24"), LocalDate.parse("2026-07-26"), LocalDate.parse("2026-10-17")), fenetres);
    }

    @Test
    void la_tendance_comble_les_jours_sans_mouvement_jusqu_a_aujourd_hui() {
        tendance = List.of(new PointDeTendance(LocalDate.parse("2026-10-21"), new BigDecimal("-3"), new BigDecimal("12")));
        TableauDeBord tableau = stock.tableauDeBord(CENTRE, 7, 10, TriDuClassement.VALEUR);
        assertEquals(7, tableau.tendance().size());
        assertEquals(LocalDate.parse("2026-10-17"), tableau.tendance().get(0).jour());
        assertEquals(LocalDate.parse("2026-10-23"), tableau.tendance().get(6).jour());
        assertEquals(new BigDecimal("-3"), tableau.tendance().get(4).quantite());
        assertEquals(BigDecimal.ZERO, tableau.tendance().get(5).valeur());
    }

    @Test
    void le_stock_valorise_est_trie_par_libelle_sans_casse_ni_accents_et_les_totaux_s_additionnent() {
        valorises = List.of(valorise("zeta", "Z", "1", "1"), valorise("Élan", "E", "2", "2"), valorise("alpha", "A", "3", "3"),
                valorise("Beta", "B", "4", "4"));
        List<String> ordre = stock.stockValorise(CENTRE).stream().map(ArticleValorise::libelle).toList();
        assertEquals(List.of("alpha", "Beta", "Élan", "zeta"), ordre);
        TableauDeBord tableau = stock.tableauDeBord(CENTRE, 30, 10, TriDuClassement.VALEUR);
        assertEquals(new BigDecimal("10"), tableau.quantiteTotale());
        assertEquals(new BigDecimal("30"), tableau.valeurTotale());
    }

    @Test
    void les_alertes_de_peremption_couvrent_trente_jours_puis_les_ruptures() {
        List<AlerteDeStock> alertes = stock.alertes(CENTRE);
        assertEquals(List.of(LocalDate.parse("2026-11-22")), peremptions);
        assertEquals(List.of("PEREMPTION", "RUPTURE"), alertes.stream().map(AlerteDeStock::type).toList());
    }

    @Test
    void l_explication_d_un_article_inconnu_est_refusee_et_celle_d_un_article_vide_est_vide() {
        assertThrows(ArticleIntrouvable.class, () -> stock.expliquerLePmp(CENTRE, UUID.randomUUID()));
        ReferentielDesArticles referentiel = new ReferentielDesArticles(articles, UUID::randomUUID, HORLOGE);
        UUID id = referentiel.referencer(CENTRE, FicheArticle.saisie("A1", "Alpha", null, null, null, null, "piece", null, null,
                null, null, null, null, null, null, null, false, false, null, false, false, null, null, null)).id();
        ExplicationDuPmp vide = stock.expliquerLePmp(CENTRE, id);
        assertEquals(0, vide.etapes().size());
        assertEquals("A1", vide.code());
        mouvements = List.of(new MouvementDeStock(UUID.randomUUID(), id, UUID.randomUUID(), null,
                MouvementDeStock.Type.ENTREE, BigDecimal.TEN, new BigDecimal("3"), OffsetDateTime.parse("2026-10-01T12:00:00Z"),
                null));
        ExplicationDuPmp une = stock.expliquerLePmp(CENTRE, id);
        assertEquals("BR-00001", une.etapes().get(0).piece());
        assertEquals("2026-10-01", une.etapes().get(0).datePiece());
        assertEquals(new BigDecimal("3.0000"), une.pmpFinal());
    }
}

