package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.model.TypeTraitementAnemie;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.InventaireStatut;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.model.StockMovementType;
import com.hemodialyse.backend.domain.stock.port.InventaireEventPublisher;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockEventPublisher;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventaire : ouverture (gel), comptage, clôture (stock de départ, mouvements clôturés, recalcul) et garde des mouvements.
 */
class InventaireServiceTest {

    private static final CenterId CENTER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private final Articles articles = new Articles();
    private final Lots lots = new Lots();
    private final Movements rawMovements = new Movements();
    private final Inventaires inventaires = new Inventaires(rawMovements);
    private final List<String> events = new ArrayList<>();
    private StockMovementRepositoryPort guarded;
    private InventaireService service;
    private Article dialyseur;
    private Lot lotA;
    private Lot lotB;

    @BeforeEach
    void setUp() {
        guarded = new InventoryGuardedMovementRepository(rawMovements, inventaires);
        PmpEngine engine = new PmpEngine(guarded, articles);
        StockEventPublisher noEvents = new StockEventPublisher() {
            public void stockMovementChanged(UUID c, String m, String r, int n) {
            }

            public void recalcLocksChanged(UUID c, List<UUID> a, String s) {
            }
        };
        PmpRecalculationCoordinator coordinator = new PmpRecalculationCoordinator(engine, noEvents, Runnable::run);
        InventaireEventPublisher publisher = (center, statut, ref, date, by) -> events.add(statut + ":" + ref);
        int[] seq = {0};
        service = new InventaireService(inventaires, articles, lots, rawMovements, engine, coordinator,
                (c, cle) -> "INV-" + String.format("%05d", ++seq[0]), publisher,
                Clock.fixed(Instant.parse("2026-09-30T10:00:00Z"), ZoneOffset.UTC));

        dialyseur = articles.add("DIAL-01", "Dialyseur FX80", true, "10", "1500");
        // Historique : 10 reçus à 1 500 (lot A), 6 reçus à 1 800 (lot B), 4 consommés sur A.
        lotA = lots.add(dialyseur, "A-01", LocalDate.of(2027, 1, 31), "10", "6");
        lotB = lots.add(dialyseur, "B-02", LocalDate.of(2027, 6, 30), "6", "6");
        rawMovements.add(StockMovement.entree(CENTER.value(), dialyseur.getId(), lotA.getId(), new BigDecimal("10"),
                new BigDecimal("1500"), "pharma", LocalDate.of(2026, 9, 1)));
        rawMovements.add(StockMovement.entree(CENTER.value(), dialyseur.getId(), lotB.getId(), new BigDecimal("6"),
                new BigDecimal("1800"), "pharma", LocalDate.of(2026, 9, 10)));
        rawMovements.add(StockMovement.sortieLot(CENTER.value(), dialyseur.getId(), null, lotA.getId(), new BigDecimal("4"),
                new BigDecimal("1612.5"), "infirmier", LocalDate.of(2026, 9, 20)));
    }

    // ---- Ouverture -------------------------------------------------------------------------------------------

    @Test
    void openingSnapshotsEveryLotInStockAndNotifies() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, "Inventaire annuel", "pharma");

        assertEquals(InventaireStatut.EN_COURS, inv.getStatut());
        assertEquals("INV-00001", inv.getReference());
        assertEquals(List.of("A-01:6", "B-02:6"), inv.getLignes().stream()
                .map(l -> l.getNumeroLot() + ":" + l.getQuantiteTheorique().toPlainString()).toList());
        assertEquals(List.of("EN_COURS:INV-00001"), events);
    }

    @Test
    void openingIsRefusedWhenAlreadyOpenInTheFutureOrBeforeLatestMovements() {
        assertEquals("INVENTORY_DATE_IN_FUTURE",
                assertThrows(BusinessException.class, () -> service.ouvrir(CENTER, TODAY.plusDays(1), null, "p")).getCode());
        assertEquals("INVENTORY_MOVEMENTS_AFTER_DATE",
                assertThrows(BusinessException.class, () -> service.ouvrir(CENTER, LocalDate.of(2026, 9, 15), null, "p")).getCode());

        service.ouvrir(CENTER, TODAY, null, "p");
        assertEquals("INVENTORY_ALREADY_IN_PROGRESS",
                assertThrows(BusinessException.class, () -> service.ouvrir(CENTER, TODAY, null, "p")).getCode());
    }

    // ---- Gel des mouvements ------------------------------------------------------------------------------------

    @Test
    void noMovementIsAllowedWhileTheInventoryIsOpen() {
        service.ouvrir(CENTER, TODAY, null, "p");

        StockMovement sortie = StockMovement.sortieLot(CENTER.value(), dialyseur.getId(), null, lotB.getId(),
                BigDecimal.ONE, new BigDecimal("1612.5"), "infirmier", TODAY);
        assertEquals("STOCK_INVENTORY_IN_PROGRESS", assertThrows(BusinessException.class, () -> guarded.save(sortie)).getCode());
        assertEquals("STOCK_INVENTORY_IN_PROGRESS", assertThrows(BusinessException.class,
                () -> guarded.deleteBySeanceAndArticle(CENTER, UUID.randomUUID(), dialyseur.getId())).getCode());
    }

    // ---- Clôture -----------------------------------------------------------------------------------------------

    @Test
    void closingRequiresEveryLineCountedAndEveryGapJustified() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        UUID ligneA = inv.getLignes().get(0).getId();
        UUID ligneB = inv.getLignes().get(1).getId();

        assertEquals("INVENTORY_NOT_COMPLETE",
                assertThrows(BusinessException.class, () -> service.cloturer(CENTER, inv.getId(), "p")).getCode());

        service.compter(CENTER, inv.getId(), ligneA, new BigDecimal("5"), null, "infirmier");
        service.compter(CENTER, inv.getId(), ligneB, new BigDecimal("6"), null, "infirmier");
        assertEquals("INVENTORY_GAP_WITHOUT_REASON",
                assertThrows(BusinessException.class, () -> service.cloturer(CENTER, inv.getId(), "p")).getCode());
        assertEquals(InventaireStatut.EN_COURS, inventaires.findById(CENTER, inv.getId()).orElseThrow().getStatut());
    }

    @Test
    void closingMakesCountedQuantitiesTheStartingStockAndClosesPastMovements() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        service.compter(CENTER, inv.getId(), inv.getLignes().get(0).getId(), new BigDecimal("5"), "Casse", "infirmier");
        service.compter(CENTER, inv.getId(), inv.getLignes().get(1).getId(), new BigDecimal("6"), null, "infirmier");
        service.ajouterLigne(CENTER, inv.getId(), dialyseur.getId(), "C-03", LocalDate.of(2027, 12, 31),
                new BigDecimal("2"), "Lot non réceptionné", "infirmier");

        Inventaire closed = service.cloturer(CENTER, inv.getId(), "pharma");

        assertEquals(InventaireStatut.CLOTURE, closed.getStatut());
        // Lots : quantités comptées ; le lot trouvé est créé.
        assertEquals(0, new BigDecimal("5").compareTo(lots.findById(lotA.getId(), CENTER).orElseThrow().getQuantiteRestante()));
        LigneInventaire nouveau = closed.getLignes().get(2);
        assertNotNull(nouveau.getLotId());
        assertEquals(0, new BigDecimal("2").compareTo(lots.findById(nouveau.getLotId(), CENTER).orElseThrow().getQuantiteRestante()));
        // Mouvements antérieurs clôturés ; stock de départ = mouvements INVENTAIRE.
        assertTrue(rawMovements.all.stream().filter(m -> m.getMovementType() != StockMovementType.INVENTAIRE)
                .allMatch(m -> closed.getId().equals(m.getInventaireId())));
        assertEquals(3, rawMovements.all.stream().filter(m -> m.getMovementType() == StockMovementType.INVENTAIRE).count());
        // Recalcul à partir du seul stock de départ : 13 unités au PMP de l'inventaire.
        Article article = articles.findById(dialyseur.getId(), CENTER).orElseThrow();
        assertEquals(0, new BigDecimal("13").compareTo(article.getStockQuantity()));
        assertEquals(0, new BigDecimal("1500").compareTo(article.getPmpCourant()));
        assertEquals(List.of("EN_COURS:INV-00001", "CLOTURE:INV-00001"), events);
    }

    @Test
    void afterClosingThePeriodIsLockedButLaterMovementsWork() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        service.reporterTheorique(CENTER, inv.getId(), "p");
        service.cloturer(CENTER, inv.getId(), "p");

        StockMovement sameDay = StockMovement.sortieLot(CENTER.value(), dialyseur.getId(), null, lotA.getId(),
                BigDecimal.ONE, new BigDecimal("1500"), "infirmier", TODAY);
        assertEquals("STOCK_PERIOD_CLOSED", assertThrows(BusinessException.class, () -> guarded.save(sameDay)).getCode());

        StockMovement closedOne = rawMovements.all.getFirst();
        assertEquals("STOCK_MOVEMENT_CLOSED", assertThrows(BusinessException.class, () -> guarded.save(closedOne)).getCode());

        StockMovement tomorrow = StockMovement.sortieLot(CENTER.value(), dialyseur.getId(), null, lotA.getId(),
                BigDecimal.ONE, new BigDecimal("1650"), "infirmier", TODAY.plusDays(1));
        guarded.save(tomorrow);
        new PmpEngine(guarded, articles).recalculerArticle(CENTER, dialyseur.getId());
        assertEquals(0, new BigDecimal("11").compareTo(articles.findById(dialyseur.getId(), CENTER).orElseThrow().getStockQuantity()));

        assertEquals("INVENTORY_DATE_BEFORE_LAST",
                assertThrows(BusinessException.class, () -> service.ouvrir(CENTER, TODAY, null, "p")).getCode());
    }

    @Test
    void cancellingReleasesTheFreezeWithoutTouchingStock() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        service.annuler(CENTER, inv.getId(), "p");

        assertNull(service.etat(CENTER).enCours());
        guarded.save(StockMovement.sortieLot(CENTER.value(), dialyseur.getId(), null, lotB.getId(), BigDecimal.ONE,
                new BigDecimal("1612.5"), "infirmier", TODAY));
        assertTrue(rawMovements.all.stream().allMatch(m -> m.getInventaireId() == null));
    }

    @Test
    void onlyAddedLinesCanBeRemovedAndLotNumbersAreRequiredWhenManaged() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        assertEquals("INVENTORY_LINE_NOT_REMOVABLE", assertThrows(BusinessException.class,
                () -> service.retirerLigne(CENTER, inv.getId(), inv.getLignes().getFirst().getId())).getCode());
        assertEquals("INVENTORY_LOT_REQUIRED", assertThrows(BusinessException.class,
                () -> service.ajouterLigne(CENTER, inv.getId(), dialyseur.getId(), " ", null, BigDecimal.ONE, null, "p")).getCode());
        assertEquals("INVENTORY_LINE_DUPLICATE", assertThrows(BusinessException.class,
                () -> service.ajouterLigne(CENTER, inv.getId(), dialyseur.getId(), "a-01", null, BigDecimal.ONE, null, "p")).getCode());
    }

    // ---- Import de la feuille de comptage ---------------------------------------------------------------------

    @Test
    void importAppliesValidRowsAndReportsTheOthers() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        UUID ligneA = inv.getLignes().get(0).getId();

        ResultatImportComptage r = service.importerComptage(CENTER, inv.getId(), List.of(
                new ComptageImporte(5, ligneA, "DIAL-01", "A-01", new BigDecimal("5.000"), "CASSE"),
                new ComptageImporte(6, null, "dial-01", "b-02", new BigDecimal("6"), null),
                new ComptageImporte(7, null, "DIAL-01", "Z-99", BigDecimal.ONE, null),
                new ComptageImporte(8, null, "DIAL-01", "A-01", BigDecimal.TEN, null),
                new ComptageImporte(9, null, "DIAL-01", "B-02", null, null)), "infirmier");

        assertEquals(2, r.lignesMisesAJour());
        assertEquals(1, r.lignesVides());
        assertEquals(List.of(7, 8), r.anomalies().stream().map(ResultatImportComptage.Anomalie::ligneFichier).toList());
        Inventaire saved = inventaires.findById(CENTER, inv.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5").compareTo(saved.getLignes().get(0).getQuantiteComptee()));
        assertEquals("CASSE", saved.getLignes().get(0).getMotifEcart());
        assertEquals(0, new BigDecimal("6").compareTo(saved.getLignes().get(1).getQuantiteComptee()));
        assertEquals("infirmier", saved.getLignes().get(1).getComptePar());
    }

    @Test
    void reimportKeepsExistingReasonAndCountsUnchangedRows() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        UUID ligneA = inv.getLignes().get(0).getId();
        service.compter(CENTER, inv.getId(), ligneA, new BigDecimal("5"), "PERTE", "p");

        ResultatImportComptage r = service.importerComptage(CENTER, inv.getId(), List.of(
                new ComptageImporte(5, ligneA, "DIAL-01", "A-01", new BigDecimal("5"), null)), "p");

        assertEquals(0, r.lignesMisesAJour());
        assertEquals(1, r.lignesInchangees());
        assertEquals("PERTE", r.inventaire().getLignes().get(0).getMotifEcart());
    }

    @Test
    void importIsRefusedOnceTheInventoryIsClosedOrCancelled() {
        Inventaire inv = service.ouvrir(CENTER, TODAY, null, "p");
        service.annuler(CENTER, inv.getId(), "p");
        assertEquals("INVENTORY_NOT_IN_PROGRESS", assertThrows(BusinessException.class,
                () -> service.importerComptage(CENTER, inv.getId(), List.of(), "p")).getCode());
    }

    // ---- Faux adaptateurs --------------------------------------------------------------------------------------

    private static final class Articles implements ArticleRepositoryPort {
        final Map<UUID, Article> rows = new LinkedHashMap<>();

        Article add(String code, String libelle, boolean parLot, String stock, String pmp) {
            Article a = new Article();
            a.setId(UUID.randomUUID());
            a.setCenterId(CENTER.value());
            a.setCode(code);
            a.setLibelle(libelle);
            a.setUnite("U");
            a.setActive(true);
            a.setGereParLot(parLot);
            a.setStockQuantity(new BigDecimal(stock));
            a.setPmpCourant(new BigDecimal(pmp));
            rows.put(a.getId(), a);
            return a;
        }

        public Optional<Article> findById(UUID id, CenterId c) {
            return Optional.ofNullable(rows.get(id));
        }

        public Article save(Article a) {
            rows.put(a.getId(), a);
            return a;
        }

        public List<Article> findAllByCenter(CenterId c) {
            return List.copyOf(rows.values());
        }

        public List<Article> findAllByCenterAndTypeTraitementAnemie(CenterId c, TypeTraitementAnemie t) {
            return List.of();
        }
    }

    private static final class Lots implements LotRepositoryPort {
        final Map<UUID, Lot> rows = new LinkedHashMap<>();

        Lot add(Article article, String numero, LocalDate peremption, String initiale, String restante) {
            Lot lot = Lot.create(CENTER.value(), article.getId(), null, null, numero, peremption, new BigDecimal(initiale), null);
            lot.setQuantiteRestante(new BigDecimal(restante));
            rows.put(lot.getId(), lot);
            return lot;
        }

        public Lot save(Lot lot) {
            rows.put(lot.getId(), lot);
            return lot;
        }

        public Optional<Lot> findById(UUID id, CenterId c) {
            return Optional.ofNullable(rows.get(id));
        }

        public List<Lot> findAvailableByArticleFefo(UUID a, CenterId c) {
            return findByArticle(a, c);
        }

        public List<Lot> findExpiringBefore(CenterId c, LocalDate d) {
            return List.of();
        }

        public List<Lot> findByArticle(UUID a, CenterId c) {
            return rows.values().stream().filter(l -> l.getArticleId().equals(a)).toList();
        }

        public List<Lot> findByBonReception(UUID b, CenterId c) {
            return List.of();
        }
    }

    /**
     * Mouvements « bruts » : les lectures chronologiques ignorent les mouvements clôturés, comme l'adaptateur JPA.
     */
    private static final class Movements implements StockMovementRepositoryPort {
        final List<StockMovement> all = new ArrayList<>();

        void add(StockMovement m) {
            all.add(m);
        }

        public StockMovement save(StockMovement m) {
            all.removeIf(x -> x.getId().equals(m.getId()));
            all.add(m);
            return m;
        }

        public List<StockMovement> findByArticleOrdered(CenterId c, UUID a) {
            return all.stream().filter(m -> m.getArticleId().equals(a) && m.getInventaireId() == null)
                    .sorted(Comparator.comparing(StockMovement::getCreatedAt)).toList();
        }

        public List<StockMovement> findByArticleBefore(CenterId c, UUID a, OffsetDateTime before) {
            return findByArticleOrdered(c, a).stream().filter(m -> m.getCreatedAt().isBefore(before)).toList();
        }

        public void updatePmpApres(UUID id, BigDecimal pmp) {
        }

        public Optional<StockMovement> findFirstEntreeByLot(CenterId c, UUID lot) {
            return Optional.empty();
        }

        public List<StockMovement> findBySeanceAndArticle(CenterId c, UUID s, UUID a) {
            return List.of();
        }

        public void deleteBySeanceAndArticle(CenterId c, UUID s, UUID a) {
        }

        public void applyRecalc(List<MovementRecalc> updates) {
        }
    }

    private static final class Inventaires implements InventaireRepositoryPort {
        final Map<UUID, Inventaire> rows = new LinkedHashMap<>();
        final Movements movements;

        Inventaires(Movements movements) {
            this.movements = movements;
        }

        public Inventaire save(Inventaire inv) {
            rows.put(inv.getId(), inv);
            return inv;
        }

        public Optional<Inventaire> findById(CenterId c, UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        public Optional<Inventaire> findEnCours(CenterId c) {
            return rows.values().stream().filter(i -> i.getStatut() == InventaireStatut.EN_COURS).findFirst();
        }

        public Optional<LocalDate> derniereCloture(CenterId c) {
            return rows.values().stream().filter(i -> i.getStatut() == InventaireStatut.CLOTURE)
                    .map(Inventaire::getDateInventaire).max(Comparator.naturalOrder());
        }

        public PagedResult<InventaireResume> findPaged(CenterId c, int page, int size) {
            return PagedResult.of(List.of(), 0, page, size);
        }

        public boolean hasMovementsAfter(CenterId c, OffsetDateTime instant) {
            return movements.all.stream().anyMatch(m -> m.getCreatedAt().isAfter(instant));
        }

        public int cloturerMouvements(CenterId c, UUID inventaireId, OffsetDateTime jusqua) {
            int n = 0;
            for (StockMovement m : movements.all) {
                if (m.getInventaireId() == null && !m.getCreatedAt().isAfter(jusqua)) {
                    m.setInventaireId(inventaireId);
                    n++;
                }
            }
            return n;
        }
    }
}




