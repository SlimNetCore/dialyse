package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.Inventaire;
import com.hemodialyse.backend.domain.stock.model.LigneInventaire;
import com.hemodialyse.backend.domain.stock.model.Lot;
import com.hemodialyse.backend.domain.stock.model.ComptageImporte;
import com.hemodialyse.backend.domain.stock.model.ResultatImportComptage;
import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.port.InventaireEventPublisher;
import com.hemodialyse.backend.domain.stock.port.InventaireRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.InventaireUseCase;
import com.hemodialyse.backend.domain.stock.port.LotRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockMovementRepositoryPort;
import com.hemodialyse.backend.domain.stock.port.StockSequencePort;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain Service — inventaire de stock.
 * <p>
 * Pure domain class (aucune dépendance Spring/JPA — AGENTS.md §3). La transaction (clôture tout-ou-rien) est
 * portée par le service applicatif.
 * <p>
 * Le gel des mouvements pendant l'inventaire et l'interdiction de toucher à une période clôturée sont appliqués
 * par {@link InventoryGuardedMovementRepository}, par lequel passent tous les mouvements de stock.
 */
public class InventaireService implements InventaireUseCase {

    private static final DateTimeFormatter FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String SEQUENCE = "INV";

    private final InventaireRepositoryPort repo;
    private final ArticleRepositoryPort articles;
    private final LotRepositoryPort lots;
    /**
     * Accès direct (non gardé) : seule la clôture d'inventaire peut écrire dans une période clôturée.
     */
    private final StockMovementRepositoryPort rawMovements;
    private final PmpEngine pmpEngine;
    private final PmpRecalculationCoordinator recalcCoordinator;
    private final StockSequencePort sequence;
    private final InventaireEventPublisher events;
    private final Clock clock;

    public InventaireService(InventaireRepositoryPort repo, ArticleRepositoryPort articles, LotRepositoryPort lots,
                             StockMovementRepositoryPort rawMovements, PmpEngine pmpEngine,
                             PmpRecalculationCoordinator recalcCoordinator, StockSequencePort sequence,
                             InventaireEventPublisher events, Clock clock) {
        this.repo = repo;
        this.articles = articles;
        this.lots = lots;
        this.rawMovements = rawMovements;
        this.pmpEngine = pmpEngine;
        this.recalcCoordinator = recalcCoordinator;
        this.sequence = sequence;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Ligne visée : par identifiant (colonne cachée), sinon par code article + n° de lot.
     */
    private static List<LigneInventaire> rapprocher(Inventaire inventaire, ComptageImporte row) {
        if (row.ligneId() != null) {
            Optional<LigneInventaire> parId = inventaire.getLignes().stream()
                    .filter(l -> l.getId().equals(row.ligneId())).findFirst();
            if (parId.isPresent()) return List.of(parId.get());
        }
        String code = norm(row.articleCode());
        if (code.isEmpty()) return List.of();
        String lot = norm(row.numeroLot());
        List<LigneInventaire> memeArticle = inventaire.getLignes().stream()
                .filter(l -> code.equals(norm(l.getArticleCode()))).toList();
        if (lot.isEmpty()) {
            List<LigneInventaire> sansLot = memeArticle.stream().filter(l -> norm(l.getNumeroLot()).isEmpty()).toList();
            return sansLot.isEmpty() ? memeArticle : sansLot;
        }
        return memeArticle.stream().filter(l -> lot.equals(norm(l.getNumeroLot()))).toList();
    }

    /**
     * « 12,000 » saisi dans un tableur = 12 (le contrôle des 3 décimales porte sur la valeur, pas sur l'écriture).
     */
    private static BigDecimal sansZerosInutiles(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static String norm(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nz(String value) {
        return value == null || value.isBlank() ? "?" : value.trim();
    }

    private static String by(String user) {
        return user != null ? user : "system";
    }

    @Override
    public Inventaire ouvrir(CenterId centerId, LocalDate dateInventaire, String commentaire, String user) {
        LocalDate date = dateInventaire != null ? dateInventaire : LocalDate.now(clock);
        repo.findEnCours(centerId).ifPresent(inv -> {
            throw new BusinessException("INVENTORY_ALREADY_IN_PROGRESS",
                    "L'inventaire " + inv.getReference() + " est déjà en cours : clôturez-le ou annulez-le d'abord.");
        });
        Optional<LocalDate> derniere = repo.derniereCloture(centerId);
        if (derniere.isPresent() && !date.isAfter(derniere.get())) {
            throw new BusinessException("INVENTORY_DATE_BEFORE_LAST",
                    "La date doit être postérieure au dernier inventaire clôturé (" + derniere.get().format(FR) + ").");
        }
        if (repo.hasMovementsAfter(centerId, Inventaire.coupure(date))) {
            throw new BusinessException("INVENTORY_MOVEMENTS_AFTER_DATE",
                    "Des mouvements de stock sont datés après le " + date.format(FR)
                            + " : choisissez une date d'inventaire postérieure ou égale au dernier mouvement.");
        }
        if (!recalcCoordinator.lockedArticles(centerId).isEmpty()) {
            throw new BusinessException("INVENTORY_RECALC_RUNNING",
                    "Un recalcul de stock est en cours : attendez sa fin avant d'ouvrir l'inventaire.");
        }

        Inventaire inventaire = Inventaire.ouvrir(centerId.value(), sequence.next(centerId, SEQUENCE), date, commentaire,
                by(user), now(), snapshot(centerId));
        Inventaire saved = repo.save(inventaire);
        events.inventaireChanged(centerId.value(), saved.getStatut().name(), saved.getReference(), date, by(user));
        return saved;
    }

    /**
     * Stock théorique figé : un lot par ligne (lots non épuisés des articles actifs), trié par article puis FEFO.
     */
    private List<LigneInventaire> snapshot(CenterId centerId) {
        List<LigneInventaire> lignes = new ArrayList<>();
        List<Article> actifs = articles.findAllByCenter(centerId).stream()
                .filter(Article::isActive)
                .sorted(Comparator.comparing(a -> a.getLibelle() == null ? "" : a.getLibelle().toLowerCase(Locale.ROOT)))
                .toList();
        for (Article article : actifs) {
            List<Lot> disponibles = lots.findByArticle(article.getId(), centerId).stream()
                    .filter(l -> l.getQuantiteRestante() != null && l.getQuantiteRestante().signum() > 0)
                    .sorted(Comparator.comparing(Lot::getDatePeremption, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            for (Lot lot : disponibles) {
                lignes.add(LigneInventaire.theorique(article.getId(), article.getCode(), article.getLibelle(),
                        article.getUnite(), lot.getId(), lot.getNumeroLot(), lot.getDatePeremption(),
                        lot.getQuantiteRestante(), article.getPmpCourant()));
            }
            BigDecimal stock = article.getStockQuantity() != null ? article.getStockQuantity() : BigDecimal.ZERO;
            if (disponibles.isEmpty() && stock.signum() > 0) {
                // Stock sans lot (données anciennes) : ligne sans lot, un lot sera créé à la clôture.
                lignes.add(LigneInventaire.theorique(article.getId(), article.getCode(), article.getLibelle(),
                        article.getUnite(), null, null, null, stock, article.getPmpCourant()));
            }
        }
        return lignes;
    }

    @Override
    public Inventaire get(CenterId centerId, UUID inventaireId) {
        return repo.findById(centerId, inventaireId).orElseThrow(() -> new BusinessException("INVENTORY_NOT_FOUND",
                "Inventaire introuvable pour ce centre."));
    }

    @Override
    public PagedResult<InventaireRepositoryPort.InventaireResume> list(CenterId centerId, int page, int size) {
        return repo.findPaged(centerId, Math.max(page, 0), size <= 0 ? 20 : Math.min(size, 100));
    }

    @Override
    public EtatInventaire etat(CenterId centerId) {
        return new EtatInventaire(repo.findEnCours(centerId).orElse(null), repo.derniereCloture(centerId).orElse(null));
    }

    @Override
    public Inventaire compter(CenterId centerId, UUID inventaireId, UUID ligneId, BigDecimal quantite, String motif,
                              String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.compter(ligneId, quantite, motif, by(user), now());
        return repo.save(inventaire);
    }

    @Override
    public Inventaire ajouterLigne(CenterId centerId, UUID inventaireId, UUID articleId, String numeroLot,
                                   LocalDate datePeremption, BigDecimal quantite, String motif, String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        Article article = articles.findById(articleId, centerId).orElseThrow(() -> new BusinessException(
                "INVENTORY_ARTICLE_NOT_FOUND", "Article introuvable pour ce centre."));
        String numero = numeroLot == null || numeroLot.isBlank() ? null : numeroLot.trim();
        if (article.isGereParLot() && numero == null) {
            throw new BusinessException("INVENTORY_LOT_REQUIRED",
                    "L'article " + article.getCode() + " est géré par lot : le numéro de lot est obligatoire.");
        }
        // Un lot épuisé retrouvé en rayon est rattaché à son lot existant.
        Lot existant = numero == null ? null : lots.findByArticle(articleId, centerId).stream()
                .filter(l -> numero.equalsIgnoreCase(l.getNumeroLot())).findFirst().orElse(null);
        LigneInventaire ligne = LigneInventaire.theorique(article.getId(), article.getCode(), article.getLibelle(),
                article.getUnite(), existant != null ? existant.getId() : null, numero,
                existant != null && datePeremption == null ? existant.getDatePeremption() : datePeremption,
                BigDecimal.ZERO, article.getPmpCourant());
        inventaire.ajouterLigne(ligne);
        ligne.compter(quantite, motif, by(user), now());
        return repo.save(inventaire);
    }

    @Override
    public Inventaire retirerLigne(CenterId centerId, UUID inventaireId, UUID ligneId) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.retirerLigne(ligneId);
        return repo.save(inventaire);
    }

    @Override
    public ResultatImportComptage importerComptage(CenterId centerId, UUID inventaireId, List<ComptageImporte> lignes,
                                                   String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.ensureEnCours();
        String by = by(user);
        OffsetDateTime now = now();
        List<ResultatImportComptage.Anomalie> anomalies = new ArrayList<>();
        Map<UUID, Integer> dejaVues = new HashMap<>();
        int misesAJour = 0;
        int inchangees = 0;
        int vides = 0;

        for (ComptageImporte row : lignes) {
            if (row.quantite() == null) {
                vides++;
                continue;
            }
            List<LigneInventaire> candidates = rapprocher(inventaire, row);
            if (candidates.isEmpty()) {
                anomalies.add(new ResultatImportComptage.Anomalie(row.ligneFichier(), "Article " + nz(row.articleCode())
                        + (row.numeroLot() != null ? " / lot " + row.numeroLot() : "")
                        + " absent de l'inventaire : ajoutez le lot depuis l'écran de comptage."));
                continue;
            }
            if (candidates.size() > 1) {
                anomalies.add(new ResultatImportComptage.Anomalie(row.ligneFichier(), "Article " + nz(row.articleCode())
                        + " : plusieurs lots correspondent, précisez le n° de lot."));
                continue;
            }
            LigneInventaire ligne = candidates.getFirst();
            Integer premiere = dejaVues.putIfAbsent(ligne.getId(), row.ligneFichier());
            if (premiere != null) {
                anomalies.add(new ResultatImportComptage.Anomalie(row.ligneFichier(),
                        "Lot déjà renseigné à la ligne " + premiere + " du fichier : ligne ignorée."));
                continue;
            }
            // Motif vide dans le fichier : le motif déjà saisi à l'écran est conservé.
            String motif = row.motif() != null ? row.motif() : ligne.getMotifEcart();
            if (ligne.isComptee() && ligne.getQuantiteComptee().compareTo(row.quantite()) == 0
                    && Objects.equals(motif, ligne.getMotifEcart())) {
                inchangees++;
                continue;
            }
            try {
                ligne.compter(sansZerosInutiles(row.quantite()), motif, by, now);
                misesAJour++;
            } catch (BusinessException e) {
                anomalies.add(new ResultatImportComptage.Anomalie(row.ligneFichier(), e.getMessage()));
            }
        }

        Inventaire saved = misesAJour > 0 ? repo.save(inventaire) : inventaire;
        return new ResultatImportComptage(saved, misesAJour, inchangees, vides, anomalies);
    }

    @Override
    public Inventaire reporterTheorique(CenterId centerId, UUID inventaireId, String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.reporterTheorique(by(user), now());
        return repo.save(inventaire);
    }

    @Override
    public Inventaire cloturer(CenterId centerId, UUID inventaireId, String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.cloturer(by(user), now()); // contrôles : tout compté, écarts justifiés
        OffsetDateTime coupure = inventaire.coupure();
        String by = by(user);

        // 1. Les quantités comptées deviennent le stock des lots (lots trouvés créés).
        int nouveau = 0;
        for (LigneInventaire ligne : inventaire.getLignes()) {
            BigDecimal compte = ligne.getQuantiteComptee();
            if (ligne.getLotId() != null) {
                Lot lot = lots.findById(ligne.getLotId(), centerId).orElseThrow(() -> new BusinessException(
                        "INVENTORY_LOT_NOT_FOUND", "Lot introuvable : " + ligne.getNumeroLot()));
                lot.setQuantiteRestante(compte);
                lots.save(lot);
            } else if (compte.signum() > 0) {
                String numero = ligne.getNumeroLot() != null ? ligne.getNumeroLot()
                        : inventaire.getReference() + "-" + (++nouveau);
                Lot lot = Lot.create(centerId.value(), ligne.getArticleId(), null, null, numero,
                        ligne.getDatePeremption(), compte, ligne.getPmp());
                lots.save(lot);
                ligne.setLotId(lot.getId());
                ligne.setNumeroLot(numero);
            }
        }

        // 2. Tous les mouvements jusqu'à la date d'inventaire sont clôturés (ignorés par les recalculs).
        repo.cloturerMouvements(centerId, inventaire.getId(), coupure);

        // 3. Stock de départ : un mouvement INVENTAIRE par lot compté, valorisé au PMP de l'inventaire.
        for (LigneInventaire ligne : inventaire.getLignes()) {
            if (ligne.getLotId() != null && ligne.getQuantiteComptee().signum() > 0) {
                rawMovements.save(StockMovement.inventaire(centerId.value(), ligne.getArticleId(), ligne.getLotId(),
                        ligne.getQuantiteComptee(), ligne.getPmp(), by, coupure));
            }
        }

        // 4. Quantités et PMP des articles recalculés à partir du seul stock de départ.
        for (Article article : articles.findAllByCenter(centerId)) {
            pmpEngine.recalculerArticle(centerId, article.getId());
        }

        Inventaire saved = repo.save(inventaire);
        events.inventaireChanged(centerId.value(), saved.getStatut().name(), saved.getReference(),
                saved.getDateInventaire(), by);
        return saved;
    }

    @Override
    public Inventaire annuler(CenterId centerId, UUID inventaireId, String user) {
        Inventaire inventaire = get(centerId, inventaireId);
        inventaire.annuler(by(user), now());
        Inventaire saved = repo.save(inventaire);
        events.inventaireChanged(centerId.value(), saved.getStatut().name(), saved.getReference(),
                saved.getDateInventaire(), by(user));
        return saved;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}






