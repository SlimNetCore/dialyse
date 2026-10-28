package com.hemodialyse.backend.contexts.stock.application.sortieseances;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.stock.application.sortieseances.GestionDesSortiesPourSeances.Demande;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sorties pour séances (T35, T36, T40, T41) : FEFO à l'enregistrement, pièce précédente du périmètre rapprochée avant la suivante, sortie
 * supplémentaire au PMP courant pour un écart en plus, retour en stock à la valeur de sortie pour un écart en moins.
 */
class GestionDesSortiesPourSeancesTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    private final List<String> journal = new ArrayList<>();
    private final Map<UUID, SortiePourSeances> enregistrees = new LinkedHashMap<>();
    private final Map<UUID, SortiesDuStock.LotDuStock> lots = new LinkedHashMap<>();
    private final Map<UUID, BigDecimal> parLot = new HashMap<>();
    private final Map<UUID, BigDecimal> horsLots = new HashMap<>();
    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final SortiesPourSeances pieces = new SortiesPourSeances() {
        @Override
        public SortiePourSeances creer(CentreId centre, Function<String, SortiePourSeances> fabrique) {
            SortiePourSeances piece = fabrique.apply(String.format("SPS-%05d", ++compteur));
            enregistrees.put(piece.id(), piece);
            return piece;
        }

        @Override
        public Optional<SortiePourSeances> parId(CentreId centre, UUID id) {
            return Optional.ofNullable(enregistrees.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public List<SortiePourSeances> toutes(CentreId centre) {
            return enregistrees.values().stream().filter(p -> p.centre().equals(centre)).toList();
        }

        @Override
        public Optional<SortiePourSeances> dernieresDuPerimetre(CentreId centre, PerimetreDeSortie perimetre) {
            return enregistrees.values().stream().filter(p -> p.perimetre().equals(perimetre)).reduce((a, b) -> b);
        }

        @Override
        public Optional<String> premiereNonRapprochee(CentreId centre) {
            return enregistrees.values().stream().filter(p -> !p.estRapprochee()).map(SortiePourSeances::reference).findFirst();
        }

        @Override
        public List<SortiePourSeances> duJour(CentreId centre, LocalDate jour) {
            return enregistrees.values().stream().filter(p -> p.jour().equals(jour)).toList().reversed();
        }

        @Override
        public Optional<SortiePourSeances> verrouiller(CentreId centre, UUID id) {
            return parId(centre, id);
        }

        @Override
        public Optional<SortiePourSeances> modifier(CentreId centre, UUID id, UnaryOperator<SortiePourSeances> modification) {
            return parId(centre, id).map(modification).map(p -> {
                enregistrees.put(id, p);
                return p;
            });
        }

        @Override
        public Consommations consommations(CentreId centre, UUID sortieId) {
            return new Consommations(parLot, horsLots);
        }
    };
    private final SortiesDuStock stock = new SortiesDuStock() {
        @Override
        public Optional<LotDuStock> lot(CentreId centre, UUID lotId) {
            return Optional.ofNullable(lots.get(lotId));
        }

        @Override
        public List<LotDuStock> lotsDisponibles(CentreId centre, UUID articleId) {
            return lots.values().stream().filter(l -> l.articleId().equals(articleId) && l.quantiteRestante().signum() > 0)
                    .sorted(java.util.Comparator.comparing(LotDuStock::datePeremption)).toList();
        }

        @Override
        public void consommer(CentreId centre, UUID lotId, BigDecimal quantite) {
            journal.add("consommer:" + lots.get(lotId).numero() + ":" + quantite.toPlainString());
            LotDuStock l = lots.get(lotId);
            lots.put(lotId, new LotDuStock(l.id(), l.articleId(), l.numero(), l.datePeremption(), l.quantiteRestante().subtract(quantite)));
        }

        @Override
        public void restituer(CentreId centre, UUID lotId, BigDecimal quantite) {
            journal.add("restituer:" + lots.get(lotId).numero() + ":" + quantite.toPlainString());
            LotDuStock l = lots.get(lotId);
            lots.put(lotId, new LotDuStock(l.id(), l.articleId(), l.numero(), l.datePeremption(), l.quantiteRestante().add(quantite)));
        }

        @Override
        public void enregistrerSortie(CentreId centre, UUID articleId, UUID seanceId, UUID lotId, BigDecimal quantite, BigDecimal prix,
                                      String auteur, LocalDate jour, OffsetDateTime saisiLe) {
            journal.add("sortie:" + lots.get(lotId).numero() + ":" + quantite.toPlainString() + "@" + prix.toPlainString() + ":" + jour);
        }

        @Override
        public void enregistrerRetour(CentreId centre, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal prix, String auteur,
                                      LocalDate jour, OffsetDateTime saisiLe) {
            journal.add("retour:" + lots.get(lotId).numero() + ":" + quantite.toPlainString() + "@" + prix.toPlainString() + ":" + jour);
        }

        @Override
        public void retirerLaSortie(CentreId centre, UUID seanceId, UUID articleId, UUID lotId, BigDecimal quantite,
                                    OffsetDateTime saisiLe) {
            throw new UnsupportedOperationException();
        }
    };
    private final EcrituresDuStock ecritures = (EcrituresDuStock) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{EcrituresDuStock.class}, (proxy, methode, arguments) -> {
                switch (methode.getName()) {
                    case "verrouiller" -> journal.add("verrou:" + ((Collection<?>) arguments[1]).size());
                    case "exigerAucunInventaireEnCours", "exigerUneDateOuverte", "appliquerLeRejeu" -> {
                    }
                    default -> throw new UnsupportedOperationException(methode.getName());
                }
                return null;
            });
    private final LecturesDuStock lectures = (LecturesDuStock) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{LecturesDuStock.class}, (proxy, methode, arguments) -> {
                if (methode.getName().equals("mouvementsDeLArticle")) {
                    return List.of();
                }
                throw new UnsupportedOperationException(methode.getName());
            });
    private final NotificationsDuStock notifications = new NotificationsDuStock() {
        @Override
        public void mouvementsModifies(UUID centre, String mouvement, String reference, int nombreArticles) {
            journal.add("notification:" + reference);
        }

        @Override
        public void verrousDeRecalculModifies(UUID centre, List<UUID> articles, String etat) {
            throw new UnsupportedOperationException();
        }
    };
    private int compteur;
    private UUID a1;
    private UUID lotTot;
    private UUID lotTard;
    private GestionDesSortiesPourSeances sorties;

    private static FicheArticle fiche(String code) {
        return FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null, null, null, null, null, null, null,
                null, false, false, null, false, false, null, null, null);
    }

    @BeforeEach
    void preparer() {
        a1 = UUID.randomUUID();
        articles.enregistrer(Article.reconstituer(a1, CENTRE, fiche("A1"), true, new BigDecimal("100"), new BigDecimal("8.00"), null));
        lotTard = lot("TARD", LocalDate.of(2027, 6, 1), "50");
        lotTot = lot("TOT", LocalDate.of(2027, 1, 1), "6");
        UniteDeTravail unite = new UniteDeTravail() {
            @Override
            public <T> T executer(java.util.function.Supplier<T> travail) {
                return travail.get();
            }
        };
        sorties = new GestionDesSortiesPourSeances(pieces, articles, stock, ecritures, new RejeuDuPmp(lectures, ecritures), unite,
                notifications, UUID::randomUUID, HorlogeDesCentres.uniforme(HORLOGE));
    }

    private UUID lot(String numero, LocalDate peremption, String quantite) {
        UUID id = UUID.randomUUID();
        lots.put(id, new SortiesDuStock.LotDuStock(id, a1, numero, peremption, new BigDecimal(quantite)));
        return id;
    }

    private SortiePourSeances enregistrer(String quantite) {
        return sorties.enregistrer(CENTRE, LocalDate.of(2026, 10, 24), PerimetreDeSortie.journee(), "gestionnaire",
                List.of(new Demande(a1, new BigDecimal(quantite))));
    }

    @Test
    void l_enregistrement_sort_les_lots_en_fefo_au_pmp_courant_a_la_date_du_jour() {
        SortiePourSeances piece = enregistrer("10");
        assertEquals("SPS-00001", piece.reference());
        assertEquals(2, piece.lignes().size());
        assertEquals(lotTot, piece.lignes().get(0).lotId());
        assertEquals(0, new BigDecimal("6").compareTo(piece.lignes().get(0).quantiteSortie()));
        assertEquals(lotTard, piece.lignes().get(1).lotId());
        assertEquals(0, new BigDecimal("4").compareTo(piece.lignes().get(1).quantiteSortie()));
        assertEquals(new BigDecimal("8.00"), piece.lignes().get(0).valeurUnitaire());
        assertTrue(journal.contains("sortie:TOT:6@8.00:2026-10-23"), journal.toString());
        assertTrue(journal.contains("sortie:TARD:4@8.00:2026-10-23"), journal.toString());
        assertEquals(SortiePourSeances.Statut.ENREGISTREE, piece.statut());
    }

    @Test
    void les_demandes_inutilisables_et_le_stock_insuffisant_sont_refuses() {
        assertEquals("PIECE_SANS_LIGNE", assertThrows(RefusDuReferentiel.class, () -> sorties.enregistrer(CENTRE, null, null, "u",
                List.of())).code());
        assertEquals("PIECE_LIGNE_INVALIDE", assertThrows(RefusDuReferentiel.class, () -> sorties.enregistrer(CENTRE, null, null, "u",
                List.of(new Demande(a1, BigDecimal.ZERO)))).code());
        assertEquals("PIECE_ARTICLE_INCONNU", assertThrows(RefusDuReferentiel.class, () -> sorties.enregistrer(CENTRE, null, null, "u",
                List.of(new Demande(UUID.randomUUID(), BigDecimal.ONE)))).code());
        assertEquals("STOCK_INSUFFISANT", assertThrows(RefusDuReferentiel.class, () -> enregistrer("57")).code());
        assertTrue(enregistrees.isEmpty());
    }

    @Test
    void la_piece_precedente_du_meme_perimetre_doit_etre_rapprochee_un_autre_perimetre_est_libre() {
        SortiePourSeances premiere = enregistrer("1");
        assertEquals("PIECE_PRECEDENTE_NON_RAPPROCHEE", assertThrows(RefusDuReferentiel.class, () -> enregistrer("1")).code());
        sorties.enregistrer(CENTRE, LocalDate.of(2026, 10, 24), new PerimetreDeSortie("SALLE_B", DemiJournee.MATIN), "u",
                List.of(new Demande(a1, BigDecimal.ONE)));
        parLot.put(lotTot, BigDecimal.ONE);
        sorties.justifierUnEcart(CENTRE, premiere.id(), premiere.lignes().get(0).id(), "ok");
        sorties.valider(CENTRE, premiere.id(), "directeur");
        assertEquals("SPS-00003", enregistrer("1").reference());
    }

    @Test
    void un_ecart_en_moins_revient_au_lot_d_origine_a_la_valeur_de_sortie() {
        SortiePourSeances piece = enregistrer("10");
        parLot.put(lotTot, new BigDecimal("6"));
        parLot.put(lotTard, new BigDecimal("1"));
        SortiePourSeances constatee = sorties.preparerLeRapprochement(CENTRE, piece.id());
        LigneDePiece ligneTard = constatee.lignes().get(1);
        assertEquals(0, new BigDecimal("-3").compareTo(ligneTard.ecart()));
        assertEquals("ECART_SANS_MOTIF", assertThrows(RefusDuReferentiel.class, () -> sorties.valider(CENTRE, piece.id(), "d")).code());
        sorties.justifierUnEcart(CENTRE, piece.id(), ligneTard.id(), "dose non utilisee");
        journal.clear();
        SortiePourSeances rapprochee = sorties.valider(CENTRE, piece.id(), "directeur");
        assertTrue(rapprochee.estRapprochee());
        assertEquals("directeur", rapprochee.valideePar());
        assertTrue(journal.contains("restituer:TARD:3"), journal.toString());
        assertTrue(journal.contains("retour:TARD:3@8.00:2026-10-23"), journal.toString());
        assertEquals(0, new BigDecimal("49").compareTo(lots.get(lotTard).quantiteRestante()));
    }

    @Test
    void un_ecart_en_plus_est_une_sortie_supplementaire_au_pmp_courant_lots_de_la_piece_d_abord() {
        SortiePourSeances piece = enregistrer("10");
        parLot.put(lotTot, new BigDecimal("6"));
        parLot.put(lotTard, new BigDecimal("4"));
        horsLots.put(a1, new BigDecimal("2"));
        SortiePourSeances constatee = sorties.preparerLeRapprochement(CENTRE, piece.id());
        LigneDePiece exces = constatee.lignes().get(2);
        assertNull(exces.lotId());
        assertEquals(0, new BigDecimal("2").compareTo(exces.ecart()));
        sorties.justifierUnEcart(CENTRE, piece.id(), exces.id(), "patient supplementaire");
        journal.clear();
        sorties.valider(CENTRE, piece.id(), "pharmacien");
        assertTrue(journal.contains("sortie:TARD:2@8.00:2026-10-23"), journal.toString());
        assertEquals(0, new BigDecimal("44").compareTo(lots.get(lotTard).quantiteRestante()));
    }

    @Test
    void une_piece_rapprochee_est_figee() {
        SortiePourSeances piece = enregistrer("10");
        parLot.put(lotTot, new BigDecimal("6"));
        parLot.put(lotTard, new BigDecimal("4"));
        sorties.valider(CENTRE, piece.id(), "d");
        assertEquals("PIECE_DEJA_RAPPROCHEE", assertThrows(RefusDuReferentiel.class, () -> sorties.valider(CENTRE, piece.id(), "d")).code());
        assertEquals("PIECE_DEJA_RAPPROCHEE", assertThrows(RefusDuReferentiel.class,
                () -> sorties.preparerLeRapprochement(CENTRE, piece.id())).code());
        assertEquals("PIECE_INTROUVABLE", assertThrows(RefusDuReferentiel.class,
                () -> sorties.consulter(new CentreId(UUID.randomUUID()), piece.id())).code());
    }
}


