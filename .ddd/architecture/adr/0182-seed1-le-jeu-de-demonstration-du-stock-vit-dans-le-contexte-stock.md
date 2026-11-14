---
status: accepted (livrée le 2026-11-14)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: stock
related: ADR 0024 ; `inventaire-platform-restant-v2.md` ligne 18 ; `.ddd/migration/reviews/petites-slices-realisation.md`
---

# 0182 - SEED1 : le jeu de démonstration du Stock vit dans le contexte Stock

Refactoring sur place (ADR 0024) : mêmes données, mêmes conditions d'activation, aucun changement de comportement.

## Décision
`StockDemoDataSeeder` (platform, `infrastructure/seed`) devient `contexts/stock/configuration/JeuDeDemonstrationDuStock` ; le paquet `infrastructure/seed` disparaît de la plateforme. Corps identique (3 fournisseurs, 2 emplacements, 8 articles, 1 bon de commande validé, 2 réceptions, 1 sortie FEFO, écrits par les cas d'usage du Stock), bean `stockDemoDataRunner` (désormais `public`).

## Conditions d'activation (inchangées)
Pas de profil Spring : `@ConditionalOnProperty(app.stock.demo-data = true, matchIfMissing = true)`. `application.yml` : `${STOCK_DEMO_DATA:true}` ; `application-prod.yml` : `${STOCK_DEMO_DATA:false}` ; `application-test.yml` : `false` (verrouillé par `ProductionProfileConfigTest`). Idempotence : rien n'est écrit si le centre de démonstration a déjà un fournisseur.

## Filet
`JeuDeDemonstrationDuStockIntegrationTest` (ex-`StockDemoDataSeederIntegrationTest`, reste en platform car il lit le schéma H2 complet et partage le centre de démonstration) : données créées (compteurs, PMP 360, lot restant 40), idempotence ; ajout : le bean est absent quand `app.stock.demo-data=false`. Aucun golden.

## Gardes
Aucune entrée de `covered.json` ni de garde de tables ne nommait ce fichier (le seeder passe par des cas d'usage, pas par du SQL). `seamAllowed` = [].
