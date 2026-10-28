package com.hemodialyse.backend.contexts.stock.application.consommations;

import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationDeSeance;
import com.hemodialyse.backend.contexts.stock.domain.consommations.ConsommationsImputees;
import com.hemodialyse.backend.contexts.stock.domain.consommations.Imputation;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.DemiJournee;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.LigneDePiece;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.PerimetreDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiePourSeances;
import com.hemodialyse.backend.contexts.stock.domain.sortieseances.SortiesPourSeances;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.ConsommationRefusee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
 * Consommations de séance (T37, T38, T39) : imputation sur la pièce du jour et du périmètre, lots en FEFO, aucun mouvement de stock,
 * dépassement accepté avec alerte, séance facturée ou pièce rapprochée refusées.
 */
class GestionDesConsommationsTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final LocalDate JOUR = LocalDate.of(2026, 10, 24);

    private final Map<UUID, ConsommationDeSeance> consommations = new HashMap<>();
    private final Map<UUID, SortiePourSeances> enregistrees = new LinkedHashMap<>();
    private final List<UUID> seancesFacturees = new ArrayList<>();
    private final ArticlesEnMemoire articles = new ArticlesEnMemoire();
    private final ConsommationsImputees imputees = new ConsommationsImputees() {
        @Override
        public Optional<ConsommationDeSeance> parSeance(CentreId centre, UUID seance) {
            return Optional.ofNullable(consommations.get(seance));
        }

        @Override
        public void enregistrer(ConsommationDeSeance consommation) {
            consommations.put(consommation.seanceId(), consommation);
        }

        @Override
        public void supprimer(CentreId centre, UUID seance) {
            consommations.remove(seance);
        }

        @Override
        public Map<UUID, BigDecimal> totauxParLotSaufSeance(CentreId centre, UUID sortie, UUID seance) {
            Map<UUID, BigDecimal> totaux = new HashMap<>();
            consommations.values().stream().filter(c -> sortie.equals(c.sortieId()) && !c.seanceId().equals(seance))
                    .flatMap(c -> c.imputations().stream()).filter(i -> i.lotId() != null)
                    .forEach(i -> totaux.merge(i.lotId(), i.quantite(), BigDecimal::add));
            return totaux;
        }
    };
    private final SortiesPourSeances pieces = new SortiesPourSeances() {
        @Override
        public SortiePourSeances creer(CentreId centre, Function<String, SortiePourSeances> fabrique) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SortiePourSeances> parId(CentreId centre, UUID id) {
            return Optional.ofNullable(enregistrees.get(id));
        }

        @Override
        public List<SortiePourSeances> toutes(CentreId centre) {
            return List.copyOf(enregistrees.values());
        }

        @Override
        public Optional<SortiePourSeances> dernieresDuPerimetre(CentreId centre, PerimetreDeSortie perimetre) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public Consommations consommations(CentreId centre, UUID sortieId) {
            throw new UnsupportedOperationException();
        }
    };
    private final SortiesDuStock stock = new SortiesDuStock() {
        @Override
        public Optional<LotDuStock> lot(CentreId centre, UUID lotId) {
            LocalDate peremption = lotId.equals(lotTot) ? LocalDate.of(2027, 1, 1) : LocalDate.of(2027, 6, 1);
            return Optional.of(new LotDuStock(lotId, a1, "L", peremption, BigDecimal.TEN));
        }

        @Override
        public List<LotDuStock> lotsDisponibles(CentreId centre, UUID articleId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void consommer(CentreId centre, UUID lotId, BigDecimal quantite) {
            throw new AssertionError("aucun mouvement de stock pour une consommation de séance");
        }

        @Override
        public void restituer(CentreId centre, UUID lotId, BigDecimal quantite) {
            throw new AssertionError("aucun mouvement de stock pour une consommation de séance");
        }

        @Override
        public void enregistrerSortie(CentreId centre, UUID articleId, UUID seanceId, UUID lotId, BigDecimal quantite, BigDecimal prix,
                                      String auteur, LocalDate jour, OffsetDateTime saisiLe) {
            throw new AssertionError("aucun mouvement de stock pour une consommation de séance");
        }

        @Override
        public void enregistrerRetour(CentreId centre, UUID articleId, UUID lotId, BigDecimal quantite, BigDecimal prix, String auteur,
                                      LocalDate jour, OffsetDateTime saisiLe) {
            throw new AssertionError("aucun mouvement de stock pour une consommation de séance");
        }

        @Override
        public void retirerLaSortie(CentreId centre, UUID seanceId, UUID articleId, UUID lotId, BigDecimal quantite,
                                    OffsetDateTime saisiLe) {
            throw new AssertionError("aucun mouvement de stock pour une consommation de séance");
        }
    };
    private PerimetreDeSortie perimetreDeLaSeance = PerimetreDeSortie.journee();
    private UUID a1;
    private UUID lotTot;
    private UUID lotTard;
    private GestionDesConsommations gestion;

    private static FicheArticle fiche(String code) {
        return FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null, null, null, null, null, null, null,
                null, false, false, null, false, false, null, null, null);
    }

    @BeforeEach
    void preparer() {
        a1 = UUID.randomUUID();
        articles.enregistrer(Article.reconstituer(a1, CENTRE, fiche("A1"), true, BigDecimal.ZERO, new BigDecimal("8"), null));
        lotTard = UUID.randomUUID();
        lotTot = UUID.randomUUID();
        UniteDeTravail unite = new UniteDeTravail() {
            @Override
            public <T> T executer(java.util.function.Supplier<T> travail) {
                return travail.get();
            }
        };
        gestion = new GestionDesConsommations(imputees, pieces, stock, articles, (centre, seance) -> perimetreDeLaSeance,
                (centre, seance) -> seancesFacturees.contains(seance), unite, UUID::randomUUID);
    }

    private SortiePourSeances piece(PerimetreDeSortie perimetre, boolean rapprochee) {
        SortiePourSeances piece = SortiePourSeances.enregistrer(UUID.randomUUID(), CENTRE, "SPS-" + (enregistrees.size() + 1), JOUR,
                perimetre, OffsetDateTime.parse("2026-10-23T08:00:00Z"), "g", List.of(
                        LigneDePiece.sortie(UUID.randomUUID(), a1, lotTot, new BigDecimal("3"), new BigDecimal("8")),
                        LigneDePiece.sortie(UUID.randomUUID(), a1, lotTard, new BigDecimal("5"), new BigDecimal("8"))));
        if (rapprochee) {
            piece = SortiePourSeances.reconstituer(piece.id(), CENTRE, piece.reference(), JOUR, perimetre,
                    SortiePourSeances.Statut.RAPPROCHEE, piece.enregistreeLe(), "g", piece.enregistreeLe(), "d", piece.lignes());
        }
        enregistrees.put(piece.id(), piece);
        return piece;
    }

    private ConsommationDeSeance consommation(UUID seance) {
        return consommations.get(seance);
    }

    @Test
    void la_consommation_est_imputee_sur_les_lots_de_la_piece_en_fefo_sans_mouvement_de_stock() {
        SortiePourSeances piece = piece(PerimetreDeSortie.journee(), false);
        UUID seance = UUID.randomUUID();
        gestion.ajouter(CENTRE, seance, UUID.randomUUID(), JOUR, a1, new BigDecimal("4"));
        ConsommationDeSeance c = consommation(seance);
        assertEquals(piece.id(), c.sortieId());
        assertEquals(2, c.imputations().size());
        assertEquals(lotTot, c.imputations().get(0).lotId());
        assertEquals(0, new BigDecimal("3").compareTo(c.imputations().get(0).quantite()));
        assertEquals(lotTard, c.imputations().get(1).lotId());
        assertEquals(0, new BigDecimal("1").compareTo(c.imputations().get(1).quantite()));
        assertTrue(c.imputations().stream().allMatch(i -> i.alerte() == null));
    }

    @Test
    void le_depassement_et_l_article_hors_piece_sont_acceptes_avec_alerte() {
        piece(PerimetreDeSortie.journee(), false);
        UUID seance = UUID.randomUUID();
        gestion.ajouter(CENTRE, seance, null, JOUR, a1, new BigDecimal("10"));
        Imputation exces = consommation(seance).imputations().get(2);
        assertNull(exces.lotId());
        assertEquals(ConsommationDeSeance.DEPASSEMENT, exces.alerte());
        assertEquals(0, new BigDecimal("2").compareTo(exces.quantite()));

        UUID autre = UUID.randomUUID();
        articles.enregistrer(Article.reconstituer(autre, CENTRE, fiche("A2"), true, BigDecimal.ZERO, BigDecimal.ONE, null));
        UUID seance2 = UUID.randomUUID();
        gestion.ajouter(CENTRE, seance2, null, JOUR, autre, BigDecimal.ONE);
        assertEquals(ConsommationDeSeance.HORS_PIECE, consommation(seance2).imputations().get(0).alerte());
    }

    @Test
    void sans_piece_la_consommation_est_acceptee_avec_l_alerte_aucune_piece() {
        UUID seance = UUID.randomUUID();
        gestion.ajouter(CENTRE, seance, null, JOUR, a1, BigDecimal.TWO);
        assertNull(consommation(seance).sortieId());
        assertEquals(ConsommationDeSeance.AUCUNE_PIECE, consommation(seance).imputations().get(0).alerte());
    }

    @Test
    void le_reste_d_une_piece_tient_compte_des_autres_seances_et_fixer_remplace_la_quantite() {
        piece(PerimetreDeSortie.journee(), false);
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        gestion.ajouter(CENTRE, s1, null, JOUR, a1, new BigDecimal("6"));
        gestion.ajouter(CENTRE, s2, null, JOUR, a1, new BigDecimal("3"));
        assertEquals(ConsommationDeSeance.DEPASSEMENT, consommation(s2).imputations().get(consommation(s2).imputations().size() - 1).alerte());
        gestion.fixer(CENTRE, s1, null, JOUR, a1, new BigDecimal("2"));
        assertEquals(0, new BigDecimal("2").compareTo(consommation(s1).totalDe(a1)));
        gestion.fixer(CENTRE, s1, null, JOUR, a1, BigDecimal.ZERO);
        assertFalse(consommations.containsKey(s1));
    }

    @Test
    void les_favoris_d_une_seance_suivent_le_perimetre_de_la_seance_et_montrent_le_reste() {
        piece(PerimetreDeSortie.journee(), false);
        SortiePourSeances salleMatin = piece(new PerimetreDeSortie("S1", DemiJournee.MATIN), false);
        UUID s1 = UUID.randomUUID();
        perimetreDeLaSeance = new PerimetreDeSortie("S1", DemiJournee.MATIN);
        gestion.ajouter(CENTRE, s1, null, JOUR, a1, new BigDecimal("2"));

        List<GestionDesConsommations.Favori> favoris = gestion.favorisDeLaSeance(CENTRE, UUID.randomUUID(), JOUR);

        assertEquals(1, favoris.size());
        assertEquals(a1, favoris.get(0).articleId());
        assertEquals(0, new BigDecimal("8").compareTo(favoris.get(0).quantiteSortie()));
        assertEquals(0, new BigDecimal("6").compareTo(favoris.get(0).reste()));
        assertEquals(salleMatin.id(), consommation(s1).sortieId());

        perimetreDeLaSeance = new PerimetreDeSortie("S9", DemiJournee.APRES_MIDI);
        assertEquals(1, gestion.favorisDeLaSeance(CENTRE, UUID.randomUUID(), JOUR).size(),
                "à défaut de pièce du périmètre, la pièce de la journée");
        assertTrue(gestion.favorisDeLaSeance(CENTRE, UUID.randomUUID(), JOUR.plusDays(3)).isEmpty());
    }

    @Test
    void la_piece_du_perimetre_le_plus_precis_est_retenue_sinon_la_piece_journee() {
        SortiePourSeances journee = piece(PerimetreDeSortie.journee(), false);
        SortiePourSeances salleMatin = piece(new PerimetreDeSortie("S1", DemiJournee.MATIN), false);
        perimetreDeLaSeance = new PerimetreDeSortie("S1", DemiJournee.MATIN);
        UUID s1 = UUID.randomUUID();
        gestion.ajouter(CENTRE, s1, null, JOUR, a1, BigDecimal.ONE);
        assertEquals(salleMatin.id(), consommation(s1).sortieId());
        perimetreDeLaSeance = new PerimetreDeSortie("S2", DemiJournee.APRES_MIDI);
        UUID s2 = UUID.randomUUID();
        gestion.ajouter(CENTRE, s2, null, JOUR, a1, BigDecimal.ONE);
        assertEquals(journee.id(), consommation(s2).sortieId());
    }

    @Test
    void une_seance_facturee_ou_une_piece_rapprochee_refusent_la_consommation() {
        piece(PerimetreDeSortie.journee(), true);
        UUID facturee = UUID.randomUUID();
        seancesFacturees.add(facturee);
        assertEquals("SEANCE_FACTUREE_NON_MODIFIABLE", assertThrows(ConsommationRefusee.class,
                () -> gestion.ajouter(CENTRE, facturee, null, JOUR, a1, BigDecimal.ONE)).code());
        assertEquals("SEANCE_FACTUREE_NON_MODIFIABLE", assertThrows(ConsommationRefusee.class,
                () -> gestion.annuler(CENTRE, facturee)).code());
        assertEquals("PIECE_DEJA_RAPPROCHEE", assertThrows(ConsommationRefusee.class,
                () -> gestion.ajouter(CENTRE, UUID.randomUUID(), null, JOUR, a1, BigDecimal.ONE)).code());
    }

    @Test
    void les_quantites_et_articles_invalides_sont_refuses_et_l_annulation_supprime() {
        assertEquals("QUANTITE_INVALIDE", assertThrows(ConsommationRefusee.class,
                () -> gestion.ajouter(CENTRE, UUID.randomUUID(), null, JOUR, a1, BigDecimal.ZERO)).code());
        assertEquals("QUANTITE_INVALIDE", assertThrows(ConsommationRefusee.class,
                () -> gestion.fixer(CENTRE, UUID.randomUUID(), null, JOUR, a1, BigDecimal.ONE.negate())).code());
        assertEquals("ARTICLE_INTROUVABLE", assertThrows(ConsommationRefusee.class,
                () -> gestion.ajouter(CENTRE, UUID.randomUUID(), null, JOUR, UUID.randomUUID(), BigDecimal.ONE)).code());
        UUID seance = UUID.randomUUID();
        gestion.ajouter(CENTRE, seance, null, JOUR, a1, BigDecimal.ONE);
        gestion.annuler(CENTRE, seance);
        assertTrue(consommations.isEmpty());
        gestion.annuler(CENTRE, seance);
    }

    @Test
    void les_favoris_sont_les_articles_de_la_piece_avec_leur_reste() {
        piece(PerimetreDeSortie.journee(), false);
        gestion.ajouter(CENTRE, UUID.randomUUID(), null, JOUR, a1, new BigDecimal("2"));
        var favoris = gestion.favoris(CENTRE, JOUR, PerimetreDeSortie.journee());
        assertEquals(1, favoris.size());
        assertEquals(0, new BigDecimal("8").compareTo(favoris.get(0).quantiteSortie()));
        assertEquals(0, new BigDecimal("6").compareTo(favoris.get(0).reste()));
        assertTrue(gestion.favoris(CENTRE, JOUR.plusDays(1), PerimetreDeSortie.journee()).isEmpty());
    }
}

