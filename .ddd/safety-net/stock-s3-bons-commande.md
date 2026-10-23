---
slice: stock-s3-bons-commande
status: built
date: 2026-10-23
sources: [ .ddd/migration/slices/stock-s3-bons-commande.md, docs/reference/regles-de-gestion/08-stock.md (RG-STK-005, 010 à 012), ADR 0024, 0045 ]
---

# Filet de caractérisation — S3 Bons de commande

## Point d'observation

HTTP (MockMvc sur le contexte Spring complet, sécurité réelle + `RoleScopeFilter`, H2 du profil `test`) et état SQL des
tables `bons_commande`, `bons_commande_lignes` et du compteur `SEQ_BC` d'`app_settings`. Aucun mock du code caractérisé,
aucun seam.

## Tests

`backend/platform/src/test/java/com/hemodialyse/backend/characterization/stock/referentiel/BonsDeCommandeCharacterizationIntegrationTest.java`
(13 scénarios, harnais commun `ReferentielStockCharacterizationSupport`). Goldens :
`backend/platform/src/test/resources/characterization/stock/referentiel/bons-commande/` (100 fichiers). Les lignes lues
en SQL sont triées par quantité puis prix (les identifiants d'article sont aléatoires).

## Comportements verrouillés

- Création : brouillon, référence `BC-nnnnn` séquentielle **par centre** (compteur `SEQ_BC`), auteur `system` par
  défaut,
  fournisseur facultatif, lignes facultatives (200).
- Remplacement total des lignes (identifiants de ligne régénérés), référence et statut conservés.
- Validation `BROUILLON → VALIDE` ; refus 422 `BUSINESS_RULE_VIOLATION` si pas `BROUILLON` ou sans ligne.
- Lecture, liste du plus récent au plus ancien ; bon d'un autre centre que celui demandé : 400 `BAD_REQUEST`.
- Rôles : écriture `ADMIN`/`PHARMACIEN` ; lecture + `INFIRMIER` ; `MEDECIN`, `SECRETAIRE` : 403 `ACCESS_DENIED` ;
  `DIRECTION` : 403 ; anonyme : 403.

## Écarts entre la fiche de slice et le legacy

La fiche S3 annonçait des règles que le legacy **n'applique pas** : « refus hors brouillon » du remplacement
(KB-STK-18), « montants ≥ 0 » et contrôle des articles (KB-STK-19), « autre centre introuvable » (KB-STK-17 : seul le
centre *demandé* est contrôlé). Ces règles sont des **corrections** à décider, pas de la parité.

## Stabilité

Legacy : 13/13 verts, deux passages consécutifs sans approbation après l'enregistrement (après correction d'un ordre
SQL non déterministe). Nouveau code (`BonsDeCommandeControleur`) : 13/13 verts deux fois ; 68 goldens modifiés par les
corrections KB-STK-17 à 22 (legacy conservé dans `_ecarts-legacy/bons-commande/`).

## Comportements suspects consignés

`known-bugs.md` § « Stock S3 » : KB-STK-17 à 22.

## Limites

- La politique `BonCommandeRecuALaReception` (passage à `RECU`) n'est pas déclenchée par HTTP : l'état `RECU` est posé
  en
  SQL ; la réception legacy reste couverte en S5.
- Concurrence sur la séquence `BC` non rejouée ; contraintes PostgreSQL non rejouées (H2 seulement).
- Pas de golden de l'écran Angular.



