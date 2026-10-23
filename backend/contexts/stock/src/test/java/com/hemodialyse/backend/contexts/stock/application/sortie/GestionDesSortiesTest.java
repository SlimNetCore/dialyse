package com.hemodialyse.backend.contexts.stock.application.sortie;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties.LigneSaisie;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonsDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.DateDeSortieDeSeanceImmuable;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortieInvalide;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bons de sortie manuels (RG-STK-030 à 033) : valorisation au PMP courant, lots choisis, correction par restitution puis
 * nouvelles sorties, gardes de séance facturée et de date, articles verrouillés puis rejoués.
 */
class GestionDesSortiesTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);

    private final List<String> journal = new ArrayList<>();
    private final Map<UUID, BonDeSortie> bonsEnregistres = new LinkedHashMap<>();
    private final Map<UUID, SortiesDuStock.LotDuStock> lots = new LinkedHashMap<>();
    private final List<UUID> seancesFacturees = new ArrayList<>();
    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final BonsDeSortie bons = new BonsDeSortie() {
        @Override
        public BonDeSortie creer(CentreId centre, Function<String, BonDeSortie> fabrique) {
            BonDeSortie bon = fabrique.apply(String.format("BS-%05d", ++compteur));
            bonsEnregistres.put(bon.id(), bon);
            return bon;
        }

        @Override
        public Optional<BonDeSortie> parId(CentreId centre, UUID id) {
            return Optional.ofNullable(bonsEnregistres.get(id)).filter(b -> b.centre().equals(centre));
        }

        @Override
        public List<BonDeSortie> tous(CentreId centre) {
            return bonsEnregistres.values().stream().filter(b -> b.centre().equals(centre)).toList();
        }

        @Override
        public Optional<BonDeSortie> modifier(CentreId centre, UUID id, UnaryOperator<BonDeSortie> modification) {
            return parId(centre, id).map(modification).map(b -> {
                bonsEnregistres.put(id, b);
                return b;
            });
        }
    };
    private final SortiesDuStock stock = new SortiesDuStock() {
        @Override
        public Optional<LotDuStock> lot(CentreId centre, UUID lotId) {
            return Optional.ofNullable(lots.get(lotId));
        }

        @Override
        public List<LotDuStock> lotsDisponibles(CentreId centre, UUID articleId) {
            return lots.values().stream().filter(l -> l.articleId().equals(articleId) && l.quantiteRestante().signum() > 0).toList();
        }

        @Override
        public void consommer(CentreId centre, UUID lotId, BigDecimal quantite) {
            journal.add("consommer:" + quantite.toPlainString());
            LotDuStock l = lots.get(lotId);
            lots.put(lotId, new LotDuStock(l.id(), l.articleId(), l.numero(), l.datePeremption(), l.quantiteRestante().subtract(quantite)));
        }

        @Override
        public void restituer(CentreId centre, UUID lotId, BigDecimal quantite) {
            journal.add("restituer:" + quantite.toPlainString());
            LotDuStock l = lots.get(lotId);
            lots.put(lotId, new LotDuStock(l.id(), l.articleId(), l.numero(), l.datePeremption(), l.quantiteRestante().add(quantite)));
        }

        @Override
        public void enregistrerSortie(CentreId centre, UUID articleId, UUID seanceId, UUID lotId, BigDecimal quantite,
                                      BigDecimal prix, String auteur, LocalDate jour, OffsetDateTime saisiLe) {
            journal.add("sortie:" + quantite.toPlainString() + "@" + prix.toPlainString() + ":" + auteur + ":" + jour + ":" + saisiLe);
        }

        @Override
        public void enregistrerRetour(CentreId centre, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal prix,
                                      String auteur, LocalDate jour, OffsetDateTime saisiLe) {
            journal.add("retour:" + quantite.toPlainString());
        }

        @Override
        public void retirerLaSortie(CentreId centre, UUID seanceId, UUID articleId, UUID lotId, BigDecimal quantite,
                                    OffsetDateTime saisiLe) {
            journal.add("retrait:" + quantite.toPlainString());
        }
    };
    private final EcrituresDuStock ecritures = (EcrituresDuStock) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{EcrituresDuStock.class}, (proxy, methode, arguments) -> {
                switch (methode.getName()) {
                    case "verrouiller" -> journal.add("verrou:" + ((Collection<?>) arguments[1]).size());
                    case "exigerAucunInventaireEnCours", "exigerUneDateOuverte", "appliquerLeRejeu" ->
                            journal.add(methode.getName());
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
            journal.add("notification:" + mouvement + ":" + reference + ":" + nombreArticles);
        }

        @Override
        public void verrousDeRecalculModifies(UUID centre, List<UUID> articles, String etat) {
            throw new UnsupportedOperationException();
        }
    };
    private int compteur;
    private UUID a1;
    private UUID lot1;
    private UUID lot2;
    private GestionDesSorties sorties;

    private static FicheArticle fiche(String code) {
        return FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null, null, null, null, null, null, null,
                null, false, false, null, false, false, null, null, null);
    }

    @BeforeEach
    void preparer() {
        a1 = UUID.randomUUID();
        articles.enregistrer(Article.reconstituer(a1, CENTRE, fiche("A1"), true, new BigDecimal("150"), new BigDecimal("9.00"), null));
        lot1 = lot(a1, "L1", "100");
        lot2 = lot(a1, "L2", "50");
        UniteDeTravail uniteDeTravail = new UniteDeTravail() {
            @Override
            public <T> T executer(java.util.function.Supplier<T> travail) {
                return travail.get();
            }
        };
        sorties = new GestionDesSorties(bons, articles, stock, ecritures, new RejeuDuPmp(lectures, ecritures),
                (centre, seance) -> seancesFacturees.contains(seance), uniteDeTravail, notifications, UUID::randomUUID, HorlogeDesCentres.uniforme(HORLOGE));
    }

    private UUID lot(UUID article, String numero, String quantite) {
        UUID id = UUID.randomUUID();
        lots.put(id, new SortiesDuStock.LotDuStock(id, article, numero, LocalDate.of(2027, 1, 1), new BigDecimal(quantite)));
        return id;
    }

    private BonDeSortie sortirManuel(String quantite) {
        return sorties.sortir(CENTRE, null, null, "SALLE_A", LocalDate.of(2026, 10, 22), "infirmier",
                List.of(new LigneSaisie(a1, lot1, new BigDecimal(quantite))));
    }

    @Test
    void une_sortie_est_valorisee_au_pmp_courant_consomme_le_lot_et_rejoue_l_article() {
        BonDeSortie bon = sortirManuel("30");
        assertEquals("BS-00001", bon.reference());
        assertEquals(BonDeSortie.AUCUNE_SEANCE, bon.seanceId());
        assertEquals(new BigDecimal("9.00"), bon.lignes().get(0).pmpApplique());
        assertEquals(new BigDecimal("70"), lots.get(lot1).quantiteRestante());
        assertTrue(journal.contains("sortie:30@9.00:infirmier:2026-10-22:2026-10-23T08:00Z"), journal.toString());
        assertEquals(List.of("verrou:1", "exigerAucunInventaireEnCours", "exigerUneDateOuverte", "consommer:30",
                journal.get(4), "appliquerLeRejeu", "notification:SORTIE:BS-00001:1"), journal);
    }

    @Test
    void sans_date_la_sortie_est_datee_du_jour_et_sans_auteur_elle_est_du_systeme() {
        BonDeSortie bon = sorties.sortir(CENTRE, null, null, null, null, " ", List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE)));
        assertEquals(LocalDate.of(2026, 10, 23), bon.dateSortie());
        assertEquals("system", bon.creePar());
    }

    @Test
    void les_demandes_inutilisables_sont_refusees_sans_rien_ecrire() {
        UUID autreLot = lot(UUID.randomUUID(), "LX", "10");
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u", List.of()));
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u", null));
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u",
                List.of(new LigneSaisie(a1, lot1, BigDecimal.ZERO))));
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u",
                List.of(new LigneSaisie(a1, null, BigDecimal.ONE))));
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u",
                List.of(new LigneSaisie(UUID.randomUUID(), lot1, BigDecimal.ONE))));
        assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u",
                List.of(new LigneSaisie(a1, UUID.randomUUID(), BigDecimal.ONE))));
        SortieInvalide autreArticle = assertThrows(SortieInvalide.class, () -> sorties.sortir(CENTRE, null, null, null, null, "u",
                List.of(new LigneSaisie(a1, autreLot, BigDecimal.ONE))));
        assertEquals("Le lot choisi n'appartient pas a l'article A1", autreArticle.getMessage());
        assertTrue(journal.stream().noneMatch(e -> e.startsWith("sortie:") || e.startsWith("consommer:")), journal.toString());
        assertTrue(bonsEnregistres.isEmpty());
    }

    @Test
    void un_lot_insuffisant_est_refuse_en_regle_metier_et_le_dernier_stock_du_lot_peut_sortir() {
        RefusDuReferentiel refus = assertThrows(RefusDuReferentiel.class, () -> sortirManuel("100.5"));
        assertEquals("BUSINESS_RULE_VIOLATION", refus.code());
        assertEquals("Stock insuffisant sur le lot L1", refus.getMessage());
        sortirManuel("100");
        assertEquals(BigDecimal.ZERO, lots.get(lot1).quantiteRestante().stripTrailingZeros().max(BigDecimal.ZERO));
        assertThrows(RefusDuReferentiel.class, () -> sortirManuel("0.01"));
    }

    @Test
    void la_correction_restitue_l_ancien_contenu_puis_sort_les_nouvelles_lignes_a_l_heure_du_bon() {
        BonDeSortie bon = sortirManuel("30");
        journal.clear();
        BonDeSortie corrige = sorties.corriger(CENTRE, bon.id(), null, null, null, null, "pharmacien",
                List.of(new LigneSaisie(a1, lot2, new BigDecimal("20")), new LigneSaisie(a1, lot1, new BigDecimal("5"))));
        assertEquals(bon.reference(), corrige.reference());
        assertEquals("SALLE_A", corrige.poste());
        assertEquals(bon.dateSortie(), corrige.dateSortie());
        assertEquals("infirmier", corrige.creePar());
        assertEquals(2, corrige.lignes().size());
        assertEquals(new BigDecimal("95"), lots.get(lot1).quantiteRestante());
        assertEquals(new BigDecimal("30"), lots.get(lot2).quantiteRestante());
        assertEquals("restituer:30", journal.get(2));
        assertEquals("retrait:30", journal.get(3));
        assertTrue(journal.contains("sortie:20@9.00:pharmacien:2026-10-22:" + bon.creeLe()), journal.toString());
        assertEquals("notification:CORRECTION:BS-00001:1", journal.get(journal.size() - 1));
    }

    @Test
    void une_correction_sans_ligne_ou_d_un_bon_inconnu_est_refusee() {
        BonDeSortie bon = sortirManuel("1");
        assertThrows(SortieInvalide.class, () -> sorties.corriger(CENTRE, bon.id(), null, null, null, null, "u", List.of()));
        SortieInvalide inconnu = assertThrows(SortieInvalide.class, () -> sorties.corriger(CENTRE, UUID.randomUUID(), null, null,
                null, null, "u", List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE))));
        assertTrue(inconnu.getMessage().startsWith("Bon de sortie introuvable: "));
        assertThrows(SortieInvalide.class, () -> sorties.corriger(new CentreId(UUID.randomUUID()), bon.id(), null, null, null, null,
                "u", List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE))));
    }

    @Test
    void le_bon_d_une_seance_facturee_est_immuable_et_la_date_d_un_bon_de_seance_ne_change_pas() {
        UUID seance = UUID.randomUUID();
        BonDeSortie bon = sorties.sortir(CENTRE, seance, null, "SEANCE", LocalDate.of(2026, 10, 20), "u",
                List.of(new LigneSaisie(a1, lot1, BigDecimal.TEN)));
        assertThrows(DateDeSortieDeSeanceImmuable.class, () -> sorties.corriger(CENTRE, bon.id(), null, null, null,
                LocalDate.of(2026, 10, 21), "u", List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE))));
        BonDeSortie memeDate = sorties.corriger(CENTRE, bon.id(), null, null, null, LocalDate.of(2026, 10, 20), "u",
                List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE)));
        assertEquals(LocalDate.of(2026, 10, 20), memeDate.dateSortie());
        seancesFacturees.add(seance);
        RefusDuReferentiel refus = assertThrows(RefusDuReferentiel.class, () -> sorties.corriger(CENTRE, bon.id(), null, null, null,
                null, "u", List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE))));
        assertEquals("SEANCE_FACTUREE_NON_MODIFIABLE", refus.code());
        assertEquals(new BigDecimal("99"), lots.get(lot1).quantiteRestante());
    }

    @Test
    void la_date_d_un_bon_manuel_se_corrige() {
        BonDeSortie bon = sortirManuel("1");
        BonDeSortie corrige = sorties.corriger(CENTRE, bon.id(), null, null, null, LocalDate.of(2026, 10, 21), "u",
                List.of(new LigneSaisie(a1, lot1, BigDecimal.ONE)));
        assertEquals(LocalDate.of(2026, 10, 21), corrige.dateSortie());
    }

    @Test
    void les_lots_disponibles_portent_le_pmp_courant_et_un_article_inconnu_vaut_zero() {
        var disponibles = sorties.lotsDisponibles(CENTRE, a1);
        assertEquals(2, disponibles.size());
        assertEquals(new BigDecimal("9.00"), disponibles.get(0).pmp());
        assertEquals(List.of(), sorties.lotsDisponibles(CENTRE, UUID.randomUUID()));
    }

    @Test
    void la_consultation_est_bornee_au_centre() {
        BonDeSortie bon = sortirManuel("1");
        assertEquals(bon.id(), sorties.consulter(CENTRE, bon.id()).id());
        assertThrows(SortieInvalide.class, () -> sorties.consulter(new CentreId(UUID.randomUUID()), bon.id()));
        assertEquals(1, sorties.lister(CENTRE).size());
        assertEquals(0, sorties.lister(new CentreId(UUID.randomUUID())).size());
    }
}


