package com.hemodialyse.backend.infrastructure.seed;

import com.hemodialyse.backend.domain.article.model.Article;
import com.hemodialyse.backend.domain.article.port.ArticleRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.domain.stock.model.*;
import com.hemodialyse.backend.domain.stock.port.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Loads a realistic demo dataset for the stock module (idempotent).
 * Runs after startup so JPA-managed tables already exist. Disable with
 * {@code app.stock.demo-data=false} (already disabled for tests).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.stock", name = "demo-data", havingValue = "true", matchIfMissing = true)
public class StockDemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(StockDemoDataSeeder.class);
    private static final UUID CENTER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String SEED_USER = "seed";

    private static LigneReception ligne(UUID articleId, String qte, String pu, String numeroLot, LocalDate peremption) {
        return new LigneReception(UUID.randomUUID(), articleId, bd(qte), bd(pu), numeroLot, peremption, null, null);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Bean
    ApplicationRunner stockDemoDataRunner(FournisseurRepositoryPort fournisseurRepo,
                                          EmplacementRepositoryPort emplacementRepo,
                                          ArticleRepositoryPort articleRepo,
                                          LotRepositoryPort lotRepositoryPort,
                                          BonCommandeUseCase bonCommandeUseCase,
                                          BonReceptionUseCase bonReceptionUseCase,
                                          BonSortieUseCase bonSortieUseCase) {
        return args -> {
            final CenterId center = CenterId.of(CENTER_1);

            if (!fournisseurRepo.findAllActive(center).isEmpty()) {
                log.info("[STOCK][SEED] Donnees de demonstration deja presentes, seeding ignore.");
                return;
            }

            log.info("[STOCK][SEED] Initialisation du jeu de donnees de demonstration...");

            // --- Fournisseurs ---
            Fournisseur fres = fournisseurRepo.save(Fournisseur.create(CENTER_1, "FRES",
                    "Fresenius Medical Care", "Service commercial", "+213 21 00 00 01", "contact@fresenius.dz"));
            Fournisseur bax = fournisseurRepo.save(Fournisseur.create(CENTER_1, "BAX",
                    "Baxter", "Service achats", "+213 21 00 00 02", "contact@baxter.dz"));
            fournisseurRepo.save(Fournisseur.create(CENTER_1, "BRAUN",
                    "B. Braun", "Support", "+213 21 00 00 03", "contact@bbraun.dz"));

            // --- Emplacements ---
            emplacementRepo.save(Emplacement.create(CENTER_1, "PHARM", "Pharmacie centrale"));
            emplacementRepo.save(Emplacement.create(CENTER_1, "RESA", "Reserve Salle A"));

            // --- Articles ---
            UUID dialyseur = createArticle(articleRepo, "DIA-FX60", "Dialyseur Fresenius FX60", "piece", "20");
            UUID ligneAv = createArticle(articleRepo, "LIGNE-AV", "Ligne arterio-veineuse", "piece", "30");
            UUID aiguille = createArticle(articleRepo, "AIG-FAV", "Aiguille a fistule 15G", "piece", "50");
            UUID heparine = createArticle(articleRepo, "HEP-5000", "Heparine 5000 UI", "flacon", "15");
            UUID serum = createArticle(articleRepo, "SERPH-500", "Serum physiologique 500ml", "poche", "40");
            UUID bicarbonate = createArticle(articleRepo, "BICA-650", "Cartouche bicarbonate 650g", "cartouche", "25");

            // --- Bon de commande (BL) valide ---
            var bl = bonCommandeUseCase.create(center, fres.id(), List.of(
                    new LigneBonCommande(UUID.randomUUID(), dialyseur, bd("100"), bd("350")),
                    new LigneBonCommande(UUID.randomUUID(), ligneAv, bd("200"), bd("45")),
                    new LigneBonCommande(UUID.randomUUID(), aiguille, bd("500"), bd("8"))
            ), SEED_USER);
            bonCommandeUseCase.valider(center, bl.getId());

            // --- Bon de reception #1 (Fresenius) : cree les lots + PMP ---
            LocalDate dans12Mois = LocalDate.now().plusMonths(12);
            var br1 = bonReceptionUseCase.create(center, bl.getId(), fres.id(), LocalDate.now(), List.of(
                    ligne(dialyseur, "100", "350", "LOT-DIA-A", dans12Mois),
                    ligne(ligneAv, "200", "45", "LOT-LAV-A", dans12Mois),
                    ligne(aiguille, "500", "8", "LOT-AIG-A", dans12Mois)
            ), SEED_USER);
            bonReceptionUseCase.valider(center, br1.getId(), SEED_USER);

            // --- Bon de reception #2 (Baxter) : second lot dialyseur a prix different => PMP pondere ---
            LocalDate dans6Mois = LocalDate.now().plusMonths(6);
            LocalDate bientot = LocalDate.now().plusDays(20); // declenche une alerte peremption J-30
            var br2 = bonReceptionUseCase.create(center, null, bax.id(), LocalDate.now(), List.of(
                    ligne(dialyseur, "50", "380", "LOT-DIA-B", dans6Mois),
                    ligne(heparine, "60", "120", "LOT-HEP-A", dans6Mois),
                    ligne(serum, "300", "25", "LOT-SER-A", dans6Mois),
                    ligne(bicarbonate, "80", "90", "LOT-BIC-A", bientot)
            ), SEED_USER);
            bonReceptionUseCase.valider(center, br2.getId(), SEED_USER);

            // --- Bon de sortie (BS) FEFO lie a une seance ---
            UUID lotDialyseur = lotRepositoryPort.findAvailableByArticleFefo(dialyseur, center).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Lot introuvable pour " + dialyseur)).getId();
            UUID lotLigneAv = lotRepositoryPort.findAvailableByArticleFefo(ligneAv, center).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Lot introuvable pour " + ligneAv)).getId();
            UUID lotAiguille = lotRepositoryPort.findAvailableByArticleFefo(aiguille, center).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Lot introuvable pour " + aiguille)).getId();

            bonSortieUseCase.create(center, UUID.randomUUID(), UUID.randomUUID(), "Poste 1", LocalDate.now(), List.of(
                    new SortieRequestItem(dialyseur, lotDialyseur, bd("10")),
                    new SortieRequestItem(ligneAv, lotLigneAv, bd("20")),
                    new SortieRequestItem(aiguille, lotAiguille, bd("30"))
            ), SEED_USER);

            log.info("[STOCK][SEED] Jeu de donnees de demonstration cree (3 fournisseurs, 6 articles, 2 BR, 1 BL, 1 BS).");
        };
    }

    private UUID createArticle(ArticleRepositoryPort repo, String code, String libelle, String unite, String seuil) {
        Article a = new Article();
        a.setId(UUID.randomUUID());
        a.setCenterId(CENTER_1);
        a.setCode(code);
        a.setLibelle(libelle);
        a.setUnite(unite);
        a.setStockQuantity(BigDecimal.ZERO);
        a.setSeuilAlerte(new BigDecimal(seuil));
        a.setActive(true);
        a.setCreatedAt(OffsetDateTime.now());
        return repo.save(a).getId();
    }
}



