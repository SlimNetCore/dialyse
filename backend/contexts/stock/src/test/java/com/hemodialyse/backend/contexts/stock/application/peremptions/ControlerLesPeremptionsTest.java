package com.hemodialyse.backend.contexts.stock.application.peremptions;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.peremptions.ControlerLesPeremptions.ResultatDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.LotProcheExpiration;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.NotificationsDesPeremptions.LotSignale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrôle quotidien des péremptions (RG-STK-061), avec des doubles en mémoire : centres actifs seulement, articles actifs, fenêtre
 * J+30 et rappel hebdomadaire des périmés, jour de chaque centre, une notification par centre et par jour, échec isolé.
 */
class ControlerLesPeremptionsTest {

    private static final LocalDate JOUR = LocalDate.parse("2026-10-24");
    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE = new CentreId(UUID.randomUUID());

    private final List<CentreId> actifs = new ArrayList<>(List.of(CENTRE));
    private final Map<CentreId, LocalDate> jours = new HashMap<>();
    private final Map<CentreId, List<LecturesDuStock.AlerteDeStock>> lots = new HashMap<>();
    private final List<LocalDate> horizons = new ArrayList<>();
    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final List<LotProcheExpiration> publies = new ArrayList<>();
    private final List<String> journalDesOperations = new ArrayList<>();
    private final Set<String> dejaNotifies = new HashSet<>();
    private final Map<UUID, List<LotSignale>> notifies = new HashMap<>();
    private CentreId centreEnEchec;
    private RuntimeException echec = new IllegalStateException("base indisponible");

    private final HorlogeDesCentres horloge = new HorlogeDesCentres() {
        @Override
        public OffsetDateTime maintenant() {
            return OffsetDateTime.parse("2026-10-24T06:00:00Z");
        }

        @Override
        public LocalDate aujourdHui(CentreId centre) {
            return jours.getOrDefault(centre, JOUR);
        }
    };

    private final LecturesDuStock lectures = new LecturesDePeremptions();

    private final NotificationsDesPeremptions notifications = (centre, jour, signales) -> {
        journalDesOperations.add("notification");
        notifies.put(centre, signales);
        return dejaNotifies.add(centre + "|" + jour);
    };

    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> travail) {
            journalDesOperations.add("transaction");
            return travail.get();
        }
    };

    private final ControlerLesPeremptions controle = new ControlerLesPeremptions(() -> List.copyOf(actifs), horloge, lectures,
            articles, unite, e -> {
        journalDesOperations.add("publication");
        publies.add(e);
    }, notifications);

    private final class LecturesDePeremptions implements LecturesDuStock {
        @Override
        public List<AlerteDeStock> peremptions(CentreId centre, LocalDate auPlusTard) {
            if (centre.equals(centreEnEchec)) {
                throw echec;
            }
            horizons.add(auPlusTard);
            return lots.getOrDefault(centre, List.of()).stream().filter(l -> !l.datePeremption().isAfter(auPlusTard)).toList();
        }

        @Override
        public List<PointDeTendance> tendance(CentreId centre, LocalDate premierJour) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ArticleClasse> classement(CentreId centre, LocalDate premierJour, int limite, TriDuClassement tri) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ArticleValorise> articlesActifsValorises(CentreId centre) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AlerteDeStock> ruptures(CentreId centre) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LigneDeTracabilite> tracabilite(CentreId centre, UUID lotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MouvementDeStock> mouvementsDeLArticle(CentreId centre, UUID articleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> referenceDeReception(CentreId centre, UUID lotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> referenceDeSortie(CentreId centre, UUID seanceId, UUID lotId, UUID articleId) {
            throw new UnsupportedOperationException();
        }
    }

    private UUID article(CentreId centre, String code, boolean actif) {
        UUID id = UUID.randomUUID();
        FicheArticle fiche = FicheArticle.saisie(code, "Libellé " + code, null, null, null, null, "piece", null, null, null, null,
                null, null, null, null, null, false, false, null, false, false, null, null, null);
        articles.enregistrer(Article.reconstituer(id, centre, fiche, actif, BigDecimal.TEN, BigDecimal.ONE,
                OffsetDateTime.parse("2026-01-01T00:00:00Z")));
        return id;
    }

    private UUID lot(CentreId centre, UUID article, String numero, int jours) {
        UUID id = UUID.randomUUID();
        lots.computeIfAbsent(centre, c -> new ArrayList<>()).add(new LecturesDuStock.AlerteDeStock("PEREMPTION", article, "A", id,
                numero, JOUR.plusDays(jours), new BigDecimal("3"), null));
        return id;
    }

    @Test
    void un_centre_sans_lot_proche_n_est_ni_publie_ni_notifie() {
        UUID a = article(CENTRE, "A1", true);
        lot(CENTRE, a, "L-LOIN", 31);
        List<ResultatDuCentre> resultats = controle.controler();
        assertEquals(1, resultats.size());
        assertEquals(0, resultats.get(0).lots());
        assertFalse(resultats.get(0).notifie());
        assertTrue(publies.isEmpty());
        assertTrue(notifies.isEmpty());
        assertEquals(List.of(JOUR.plusDays(30)), horizons);
    }

    @Test
    void les_lots_a_trente_jours_au_plus_sont_publies_puis_notifies_en_une_seule_alerte() {
        UUID a = article(CENTRE, "A1", true);
        lot(CENTRE, a, "L-30", 30);
        lot(CENTRE, a, "L-10", 10);
        ResultatDuCentre resultat = controle.controler().get(0);
        assertEquals(2, resultat.lots());
        assertTrue(resultat.notifie());
        assertNull(resultat.incident());
        assertEquals(JOUR, resultat.jour());
        assertEquals(List.of("L-30", "L-10"), publies.stream().map(LotProcheExpiration::numeroLot).toList());
        assertEquals(List.of("transaction", "publication", "publication", "notification"), journalDesOperations,
                "publication dans une unité de travail, avant la notification");
        List<LotSignale> signales = notifies.get(CENTRE.valeur());
        assertEquals(2, signales.size());
        assertEquals("Libellé A1", signales.get(0).libelleArticle());
        assertEquals("A1", signales.get(0).codeArticle());
    }

    @Test
    void l_evenement_porte_le_contrat_v1_et_un_identifiant_deterministe_par_lot_et_par_jour() {
        UUID a = article(CENTRE, "A1", true);
        UUID lot = lot(CENTRE, a, "L-5", 5);
        controle.controler();
        LotProcheExpiration e = publies.get(0);
        assertEquals(1, e.version());
        assertEquals(CENTRE.valeur(), e.centre());
        assertEquals(a, e.article());
        assertEquals("A1", e.codeArticle());
        assertEquals(lot, e.lot());
        assertEquals(JOUR, e.jourDuControle());
        assertEquals(JOUR.plusDays(5), e.datePeremption());
        assertEquals(new BigDecimal("3"), e.quantite());
        assertFalse(e.perime());
        assertEquals(LotProcheExpiration.v1(CENTRE.valeur(), JOUR, a, "A1", lot, "L-5", JOUR.plusDays(5), new BigDecimal("3"), false)
                .identifiant(), e.identifiant());
        controle.controler();
        assertEquals(publies.get(0).identifiant(), publies.get(1).identifiant(), "rejoué le même jour : même clé d'idempotence");
    }

    @Test
    void deux_passages_le_meme_jour_ne_notifient_qu_une_fois() {
        UUID a = article(CENTRE, "A1", true);
        lot(CENTRE, a, "L-5", 5);
        assertTrue(controle.controler().get(0).notifie());
        assertFalse(controle.controler().get(0).notifie());
    }

    @Test
    void le_lendemain_une_nouvelle_alerte_est_possible() {
        UUID a = article(CENTRE, "A1", true);
        lot(CENTRE, a, "L-5", 5);
        controle.controler();
        jours.put(CENTRE, JOUR.plusDays(1));
        assertTrue(controle.controler().get(0).notifie());
    }

    @Test
    void les_lots_d_articles_inactifs_sont_exclus() {
        UUID actif = article(CENTRE, "A1", true);
        UUID inactif = article(CENTRE, "A2", false);
        lot(CENTRE, actif, "L-ACTIF", 5);
        lot(CENTRE, inactif, "L-INACTIF", 5);
        controle.controler();
        assertEquals(List.of("L-ACTIF"), publies.stream().map(LotProcheExpiration::numeroLot).toList());
    }

    @Test
    void un_lot_perime_est_signale_le_lendemain_puis_chaque_semaine_et_marque_perime() {
        UUID a = article(CENTRE, "A1", true);
        lot(CENTRE, a, "L-HIER", -1);
        lot(CENTRE, a, "L-3", -3);
        lot(CENTRE, a, "L-8", -8);
        ResultatDuCentre resultat = controle.controler().get(0);
        assertEquals(2, resultat.lots());
        assertEquals(List.of("L-HIER", "L-8"), publies.stream().map(LotProcheExpiration::numeroLot).toList());
        assertTrue(publies.stream().allMatch(LotProcheExpiration::perime));
    }

    @Test
    void un_lot_recu_deja_perime_est_signale_le_lendemain_de_sa_reception_sans_attendre_le_rappel_hebdomadaire() {
        UUID a = article(CENTRE, "A1", true);
        UUID lot = UUID.randomUUID();
        lots.computeIfAbsent(CENTRE, c -> new ArrayList<>()).add(new LecturesDuStock.AlerteDeStock("PEREMPTION", a, "A", lot,
                "L-RECU-PERIME", JOUR.minusDays(3), new BigDecimal("3"), null, JOUR.minusDays(1)));
        assertEquals(1, controle.controler().get(0).lots());
        jours.put(CENTRE, JOUR.plusDays(1));
        assertEquals(0, controle.controler().get(0).lots(), "une seule fois");
    }

    @Test
    void seuls_les_centres_actifs_sont_parcourus_et_chacun_a_son_jour() {
        actifs.add(AUTRE);
        jours.put(AUTRE, JOUR.plusDays(1));
        UUID a = article(CENTRE, "A1", true);
        UUID b = article(AUTRE, "B1", true);
        lot(CENTRE, a, "L-A", 30);
        lot(AUTRE, b, "L-B", 30);
        controle.controler();
        assertEquals(List.of(JOUR.plusDays(30), JOUR.plusDays(31)), horizons);
        assertEquals(JOUR.plusDays(1), publies.stream().filter(e -> e.centre().equals(AUTRE.valeur())).findFirst().orElseThrow()
                .jourDuControle());
        CentreId inactif = new CentreId(UUID.randomUUID());
        UUID c = article(inactif, "C1", true);
        lot(inactif, c, "L-C", 5);
        controle.controler();
        assertTrue(publies.stream().noneMatch(e -> e.centre().equals(inactif.valeur())));
    }

    @Test
    void l_echec_d_un_centre_est_isole_et_rendu_comme_incident() {
        actifs.add(AUTRE);
        UUID b = article(AUTRE, "B1", true);
        lot(AUTRE, b, "L-B", 5);
        centreEnEchec = CENTRE;
        List<ResultatDuCentre> resultats = controle.controler();
        assertEquals("IllegalStateException : base indisponible", resultats.get(0).incident());
        assertEquals(CENTRE, resultats.get(0).centre());
        assertNull(resultats.get(1).incident());
        assertEquals(1, resultats.get(1).lots());
        assertEquals(1, publies.size());
    }

    @Test
    void un_incident_sans_message_est_rendu_par_le_nom_de_l_exception() {
        centreEnEchec = CENTRE;
        echec = new IllegalStateException();
        assertEquals("IllegalStateException", controle.controler().get(0).incident());
    }
}
