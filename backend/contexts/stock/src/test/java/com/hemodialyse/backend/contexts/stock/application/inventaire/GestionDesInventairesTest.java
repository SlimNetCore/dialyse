package com.hemodialyse.backend.contexts.stock.application.inventaire;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.Inventaires;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.LigneDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.PeriodeDeStock;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StatutDInventaire;
import com.hemodialyse.backend.contexts.stock.domain.inventaire.StockInventorie;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cas d'usage de l'inventaire (S7) : ouverture gardée par la période et T43, ajout de ligne, et clôture atomique (stock des lots, lots
 * trouvés, gel des mouvements et rejeu du PMP limités aux articles inventoriés — KB-STK-50).
 */
class GestionDesInventairesTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate HIER = LocalDate.of(2026, 10, 23);

    private final Inventaires inventaires = mock(Inventaires.class);
    private final StockInventorie stock = mock(StockInventorie.class);
    private final Articles articles = mock(Articles.class);
    private final EcrituresDuStock ecritures = mock(EcrituresDuStock.class);
    private final RejeuDuPmp rejeu = mock(RejeuDuPmp.class);
    private final SortiesPourSeances sorties = mock(SortiesPourSeances.class);
    private final NotificationsDInventaire notifications = mock(NotificationsDInventaire.class);
    private final UUID a1 = UUID.randomUUID();
    private final UUID lotTot = UUID.randomUUID();
    private GestionDesInventaires gestion;
    private boolean recalcul;

    @BeforeEach
    void preparer() {
        UniteDeTravail transaction = new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> travail) {
                return travail.get();
            }
        };
        when(sorties.premiereNonRapprochee(CENTRE)).thenReturn(Optional.empty());
        when(inventaires.periode(CENTRE)).thenReturn(new PeriodeDeStock(CENTRE, null, null));
        gestion = new GestionDesInventaires(inventaires, stock, articles, ecritures, rejeu, sorties, centre -> recalcul, transaction,
                notifications, HorlogeDesCentres.uniforme(HORLOGE), UUID::randomUUID);
    }

    private LigneDInventaire ligne(UUID article, UUID lot, String numero, String theorique) {
        return LigneDInventaire.theorique(UUID.randomUUID(), article, "A", "Article", "u", lot, numero, null, new BigDecimal(theorique),
                new BigDecimal("5"));
    }

    private Inventaire ouvert(LigneDInventaire... lignes) {
        return Inventaire.ouvrir(UUID.randomUUID(), CENTRE, "INV-00001", HIER, null, "pharmacien",
                OffsetDateTime.parse("2026-10-24T08:00:00Z"), List.of(lignes));
    }

    private String code(Runnable action) {
        return assertThrows(RefusDuReferentiel.class, action::run).code();
    }

    @SuppressWarnings("unchecked")
    @Test
    void l_ouverture_est_refusee_par_t43_puis_cree_l_inventaire_au_theorique_du_centre() {
        when(sorties.premiereNonRapprochee(CENTRE)).thenReturn(Optional.of("SPS-00003"));
        assertEquals("INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE", code(() -> gestion.ouvrir(CENTRE, HIER, null, "p")));
        verify(inventaires, never()).creer(any(), any());

        when(sorties.premiereNonRapprochee(CENTRE)).thenReturn(Optional.empty());
        when(stock.theorique(CENTRE)).thenReturn(List.of(ligne(a1, lotTot, "L-TOT", "3")));
        when(inventaires.creer(eq(CENTRE), any())).thenAnswer(appel -> ((Function<String, Inventaire>) appel.getArgument(1)).apply("INV-00001"));
        Inventaire ouvert = gestion.ouvrir(CENTRE, null, " annuel ", "pharmacien");
        assertEquals(HIER.plusDays(1), ouvert.dateInventaire(), "sans date : le jour de l'horloge (UTC)");
        assertEquals("annuel", ouvert.commentaire());
        assertEquals(1, ouvert.lignes().size());
        verify(inventaires, times(2)).verrouillerLaPeriode(CENTRE);
        verify(notifications).inventaireModifie(CENTRE.valeur(), "EN_COURS", "INV-00001", ouvert.dateInventaire(), "pharmacien");
    }

    @Test
    void l_ouverture_est_refusee_sur_un_recalcul_ou_des_mouvements_apres_la_date() {
        recalcul = true;
        assertEquals("INVENTORY_RECALC_RUNNING", code(() -> gestion.ouvrir(CENTRE, HIER, null, "p")));
        recalcul = false;
        when(stock.mouvementsApres(eq(CENTRE), any())).thenReturn(true);
        assertEquals("INVENTORY_MOVEMENTS_AFTER_DATE", code(() -> gestion.ouvrir(CENTRE, HIER, null, "p")));
        verify(inventaires, never()).creer(any(), any());
    }

    @Test
    void l_ajout_d_une_ligne_exige_un_article_connu_et_un_lot_pour_un_article_gere_par_lot() {
        Inventaire inv = ouvert(ligne(a1, lotTot, "L-TOT", "3"));
        when(inventaires.verrouiller(CENTRE, inv.id())).thenReturn(Optional.of(inv));
        assertEquals("INVENTORY_ARTICLE_NOT_FOUND", code(() -> gestion.ajouterUneLigne(CENTRE, inv.id(), UUID.randomUUID(), null,
                null, BigDecimal.ONE, null, "inf")));

        UUID gere = UUID.randomUUID();
        FicheArticle fiche = FicheArticle.saisie("G1", "Géré par lot", null, null, null, null, "piece", null, null, null, null, null, null,
                null, null, null, true, false, null, false, false, null, null, null);
        when(articles.parId(CENTRE, gere)).thenReturn(Optional.of(Article.reconstituer(gere, CENTRE, fiche, true, BigDecimal.ZERO,
                new BigDecimal("7"), null)));
        assertEquals("INVENTORY_LOT_REQUIRED", code(() -> gestion.ajouterUneLigne(CENTRE, inv.id(), gere, " ", null, BigDecimal.ONE,
                null, "inf")));

        UUID lotExistant = UUID.randomUUID();
        when(stock.lotDeLArticle(CENTRE, gere, "L9")).thenReturn(Optional.of(new StockInventorie.LotConnu(lotExistant,
                LocalDate.of(2027, 5, 1))));
        Inventaire avec = gestion.ajouterUneLigne(CENTRE, inv.id(), gere, " L9 ", null, new BigDecimal("2"), "RETOUR", "inf");
        LigneDInventaire ajoutee = avec.lignes().get(1);
        assertEquals(lotExistant, ajoutee.lotId(), "un lot épuisé retrouvé est rattaché à son lot");
        assertEquals(LocalDate.of(2027, 5, 1), ajoutee.datePeremption());
        assertEquals(0, new BigDecimal("7").compareTo(ajoutee.pmp()));
        assertTrue(ajoutee.ajoutee());
        verify(inventaires).enregistrer(avec);
    }

    @Test
    void la_cloture_pose_le_stock_cree_les_lots_trouves_fige_et_rejoue_les_seuls_articles_inventories() {
        UUID sansLot = UUID.randomUUID();
        UUID absent = UUID.randomUUID();
        LigneDInventaire l1 = ligne(a1, lotTot, "L-TOT", "3");
        LigneDInventaire l2 = ligne(sansLot, null, null, "2");
        LigneDInventaire l3 = ligne(a1, UUID.randomUUID(), "L-ABSENT", "4");
        Inventaire inv = ouvert(l1, l2, l3).compter(l1.id(), new BigDecimal("2"), "CASSE", "inf", OffsetDateTime.now(HORLOGE))
                .compter(l2.id(), new BigDecimal("3"), "RETOUR", "inf", OffsetDateTime.now(HORLOGE))
                .compter(l3.id(), BigDecimal.ZERO, "PEREMPTION", "inf", OffsetDateTime.now(HORLOGE));
        when(inventaires.verrouiller(CENTRE, inv.id())).thenReturn(Optional.of(inv));
        when(stock.poserLeStockDuLot(eq(CENTRE), any(), any())).thenReturn(true);
        UUID nouveauLot = UUID.randomUUID();
        when(stock.creerLeLot(eq(CENTRE), eq(sansLot), any(), any(), any(), any(), any())).thenReturn(nouveauLot);

        Inventaire clos = gestion.cloturer(CENTRE, inv.id(), "pharmacien");

        assertEquals(StatutDInventaire.CLOTURE, clos.statut());
        verify(stock).poserLeStockDuLot(CENTRE, lotTot, new BigDecimal("2"));
        verify(stock).poserLeStockDuLot(CENTRE, l3.lotId(), BigDecimal.ZERO);
        verify(stock).creerLeLot(eq(CENTRE), eq(sansLot), eq("INV-00001-1"), any(), eq(new BigDecimal("3")), eq(new BigDecimal("5")), any());
        assertEquals(nouveauLot, clos.ligne(l2.id()).lotId());

        ArgumentCaptor<Set<UUID>> figes = ArgumentCaptor.forClass(Set.class);
        verify(stock).figerLesMouvements(eq(CENTRE), eq(inv.id()), eq(inv.coupure()), figes.capture());
        assertEquals(Set.of(a1, sansLot), figes.getValue());
        assertFalse(figes.getValue().contains(absent), "un article sans ligne n'est pas figé");

        verify(stock).poserLeStockDeDepart(eq(CENTRE), eq(a1), eq(lotTot), eq(new BigDecimal("2")), any(), eq("pharmacien"), eq(inv.coupure()));
        verify(stock).poserLeStockDeDepart(eq(CENTRE), eq(sansLot), eq(nouveauLot), eq(new BigDecimal("3")), any(), eq("pharmacien"),
                eq(inv.coupure()));
        verify(stock, times(2)).poserLeStockDeDepart(eq(CENTRE), any(), any(), any(), any(), any(), any());
        verify(rejeu).rejouer(CENTRE, a1);
        verify(rejeu).rejouer(CENTRE, sansLot);
        verify(rejeu, times(2)).rejouer(eq(CENTRE), any());
        verify(ecritures).verrouiller(eq(CENTRE), anyCollection());
        verify(inventaires).enregistrer(clos);
        verify(notifications).inventaireModifie(CENTRE.valeur(), "CLOTURE", "INV-00001", HIER, "pharmacien");
    }

    @Test
    void la_cloture_est_refusee_sans_rien_ecrire_si_un_lot_compte_a_disparu() {
        LigneDInventaire l1 = ligne(a1, lotTot, "L-TOT", "3");
        Inventaire inv = ouvert(l1).reporterLeTheorique("inf", OffsetDateTime.now(HORLOGE));
        when(inventaires.verrouiller(CENTRE, inv.id())).thenReturn(Optional.of(inv));
        when(stock.poserLeStockDuLot(eq(CENTRE), any(), any())).thenReturn(false);

        assertEquals("INVENTORY_LOT_NOT_FOUND", code(() -> gestion.cloturer(CENTRE, inv.id(), "p")));
        verify(stock, never()).figerLesMouvements(any(), any(), any(), any());
        verifyNoInteractions(rejeu);
        verify(inventaires, never()).enregistrer(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void la_cloture_et_l_annulation_refusent_un_inventaire_inconnu_ou_deja_termine() {
        UUID inconnu = UUID.randomUUID();
        when(inventaires.verrouiller(CENTRE, inconnu)).thenReturn(Optional.empty());
        assertEquals("INVENTORY_NOT_FOUND", code(() -> gestion.cloturer(CENTRE, inconnu, "p")));
        assertEquals("INVENTORY_NOT_FOUND", code(() -> gestion.annuler(CENTRE, inconnu, "p")));

        Inventaire annule = ouvert(ligne(a1, lotTot, "L-TOT", "3")).annuler("p", OffsetDateTime.now(HORLOGE));
        when(inventaires.verrouiller(CENTRE, annule.id())).thenReturn(Optional.of(annule));
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> gestion.cloturer(CENTRE, annule.id(), "p")));
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> gestion.annuler(CENTRE, annule.id(), "p")));
    }

    @Test
    void l_annulation_enregistre_l_inventaire_annule_et_le_notifie() {
        Inventaire inv = ouvert(ligne(a1, lotTot, "L-TOT", "3"));
        when(inventaires.verrouiller(CENTRE, inv.id())).thenReturn(Optional.of(inv));
        Inventaire annule = gestion.annuler(CENTRE, inv.id(), "pharmacien");
        assertEquals(StatutDInventaire.ANNULE, annule.statut());
        verify(inventaires).enregistrer(annule);
        verify(notifications).inventaireModifie(CENTRE.valeur(), "ANNULE", "INV-00001", HIER, "pharmacien");
        verifyNoInteractions(rejeu);
    }
}


