package com.hemodialyse.backend.infrastructure.seed;

import com.hemodialyse.backend.contexts.stock.application.commandes.GestionDesBonsDeCommande;
import com.hemodialyse.backend.contexts.stock.application.reception.GestionDesReceptions;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesFournisseursEtEmplacements;
import com.hemodialyse.backend.contexts.stock.application.sortie.GestionDesSorties;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.TypeTraitementAnemie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Jeu de données de démonstration du stock (idempotent), écrit par les cas d'usage du contexte Stock (référentiel, bon de
 * commande, réceptions avec lots et PMP, sortie FEFO) : mêmes règles et mêmes gardes que l'application. Désactivé en production
 * et en test ({@code app.stock.demo-data=false}).
 */
@Configuration
@ConditionalOnProperty(prefix = "app.stock", name = "demo-data", havingValue = "true", matchIfMissing = true)
public class StockDemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(StockDemoDataSeeder.class);
    private static final CentreId CENTRE = new CentreId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final String SEED_USER = "seed";

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static GestionDesReceptions.LigneSaisie ligne(UUID articleId, String qte, String pu, String numeroLot,
                                                          LocalDate peremption) {
        return new GestionDesReceptions.LigneSaisie(UUID.randomUUID(), articleId, bd(qte), bd(pu), numeroLot, peremption, null);
    }

    private static UUID article(ReferentielDesArticles articles, String code, String libelle, String unite, String seuil,
                                TypeTraitementAnemie type, BigDecimal dosageParUnite, String uniteDosage) {
        FicheArticle fiche = FicheArticle.saisie(code, libelle, null, null, null, null, unite, null, null, dosageParUnite,
                uniteDosage, null, null, null, bd(seuil), null, true, false, null, false, false, type, null, null);
        return articles.referencer(CENTRE, fiche).id();
    }

    private static UUID article(ReferentielDesArticles articles, String code, String libelle, String unite, String seuil) {
        return article(articles, code, libelle, unite, seuil, null, null, null);
    }

    private static UUID premierLot(GestionDesSorties sorties, UUID articleId) {
        return sorties.lotsDisponibles(CENTRE, articleId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Lot introuvable pour " + articleId)).id();
    }

    @Bean
    ApplicationRunner stockDemoDataRunner(ReferentielDesFournisseursEtEmplacements referentiel, ReferentielDesArticles articles,
                                          GestionDesBonsDeCommande commandes, GestionDesReceptions receptions,
                                          GestionDesSorties sorties) {
        return args -> {
            if (!referentiel.chercherDesFournisseurs(CENTRE, null).isEmpty()) {
                log.info("[STOCK][SEED] Donnees de demonstration deja presentes, seeding ignore.");
                return;
            }
            log.info("[STOCK][SEED] Initialisation du jeu de donnees de demonstration...");
            LocalDate aujourdHui = LocalDate.now(ZoneOffset.UTC);

            // --- Fournisseurs et emplacements ---
            Fournisseur fres = referentiel.creerUnFournisseur(CENTRE, "FRES", "Fresenius Medical Care", "Service commercial",
                    "+213 21 00 00 01", "contact@fresenius.dz");
            Fournisseur bax = referentiel.creerUnFournisseur(CENTRE, "BAX", "Baxter", "Service achats", "+213 21 00 00 02",
                    "contact@baxter.dz");
            referentiel.creerUnFournisseur(CENTRE, "BRAUN", "B. Braun", "Support", "+213 21 00 00 03", "contact@bbraun.dz");
            referentiel.creerUnEmplacement(CENTRE, "PHARM", "Pharmacie centrale");
            referentiel.creerUnEmplacement(CENTRE, "RESA", "Reserve Salle A");

            // --- Articles ---
            UUID dialyseur = article(articles, "DIA-FX60", "Dialyseur Fresenius FX60", "piece", "20");
            UUID ligneAv = article(articles, "LIGNE-AV", "Ligne arterio-veineuse", "piece", "30");
            UUID aiguille = article(articles, "AIG-FAV", "Aiguille a fistule 15G", "piece", "50");
            UUID heparine = article(articles, "HEP-5000", "Heparine 5000 UI", "flacon", "15");
            UUID serum = article(articles, "SERPH-500", "Serum physiologique 500ml", "poche", "40");
            UUID bicarbonate = article(articles, "BICA-650", "Cartouche bicarbonate 650g", "cartouche", "25");
            // Dosage par unité de stock = unité de prescription du médecin (UI / mg) : la sortie de stock se déduit de la dose
            // prescrite (ex. 8000 UI = 2 seringues de 4000 UI).
            article(articles, "EPO-ALFA-4000", "Epoetine alfa 4000 UI (seringue)", "seringue", "10", TypeTraitementAnemie.EPO,
                    bd("4000"), "UI");
            article(articles, "FER-SACC-100", "Fer saccharose 100 mg (ampoule)", "ampoule", "10",
                    TypeTraitementAnemie.FER_INJECTABLE, bd("100"), "mg");

            // --- Bon de commande validé ---
            var bl = commandes.creer(CENTRE, fres.id(), SEED_USER, List.of(
                    new GestionDesBonsDeCommande.LigneSaisie(UUID.randomUUID(), dialyseur, bd("100"), bd("350")),
                    new GestionDesBonsDeCommande.LigneSaisie(UUID.randomUUID(), ligneAv, bd("200"), bd("45")),
                    new GestionDesBonsDeCommande.LigneSaisie(UUID.randomUUID(), aiguille, bd("500"), bd("8"))));
            commandes.valider(CENTRE, bl.id());

            // --- Réception n° 1 (Fresenius) : lots et PMP ---
            LocalDate dans12Mois = aujourdHui.plusMonths(12);
            var br1 = receptions.creer(CENTRE, bl.id(), fres.id(), aujourdHui, SEED_USER, List.of(
                    ligne(dialyseur, "100", "350", "LOT-DIA-A", dans12Mois),
                    ligne(ligneAv, "200", "45", "LOT-LAV-A", dans12Mois),
                    ligne(aiguille, "500", "8", "LOT-AIG-A", dans12Mois)));
            receptions.valider(CENTRE, br1.id(), SEED_USER);

            // --- Réception n° 2 (Baxter) : second lot de dialyseurs à un autre prix (PMP pondéré), un lot proche de péremption ---
            LocalDate dans6Mois = aujourdHui.plusMonths(6);
            LocalDate bientot = aujourdHui.plusDays(20);
            var br2 = receptions.creer(CENTRE, null, bax.id(), aujourdHui, SEED_USER, List.of(
                    ligne(dialyseur, "50", "380", "LOT-DIA-B", dans6Mois),
                    ligne(heparine, "60", "120", "LOT-HEP-A", dans6Mois),
                    ligne(serum, "300", "25", "LOT-SER-A", dans6Mois),
                    ligne(bicarbonate, "80", "90", "LOT-BIC-A", bientot)));
            receptions.valider(CENTRE, br2.id(), SEED_USER);

            // --- Sortie manuelle FEFO ---
            sorties.sortir(CENTRE, null, null, "Poste 1", aujourdHui, SEED_USER, List.of(
                    new GestionDesSorties.LigneSaisie(dialyseur, premierLot(sorties, dialyseur), bd("10")),
                    new GestionDesSorties.LigneSaisie(ligneAv, premierLot(sorties, ligneAv), bd("20")),
                    new GestionDesSorties.LigneSaisie(aiguille, premierLot(sorties, aiguille), bd("30"))));

            log.info("[STOCK][SEED] Jeu de donnees de demonstration cree (3 fournisseurs, 8 articles, 1 BC, 2 BR, 1 BS).");
        };
    }
}
