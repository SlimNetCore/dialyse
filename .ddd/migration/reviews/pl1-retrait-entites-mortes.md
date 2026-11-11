---
status: draft
date: 2026-11-11
sources:
  - .ddd/migration/reviews/inventaire-platform-restant.md (§3.1 PL1, §4 C1, C2, C6)
  - ADR 0024, 0133, 0134:143, 0135
---

# PL1 : retrait des entités JPA mortes de backend/platform

Aucun commit, aucun push. Hook guard-legacy : aucun blocage. check-arch : 0 violation.

## Fichiers supprimés (git rm) : 37

| Sous-étape | Fichiers |
|---|---|
| (a) 4 | `AgenceJpaEntity`, `CaisseAssuranceJpaEntity`, `CentrePayeurJpaEntity`, `AssurePatientId` |
| (b) 9 | entités `Article`, `Lot`, `StockMovement`, `Emplacement`, `Fournisseur` ; dépôts `Lot`, `StockMovement`, `Emplacement`, `Fournisseur` (il n'y a jamais eu de dépôt Article) |
| (c) 12 | entités `BonCommande`, `BonReception`, `BonSortie`, `LigneBonCommande`, `LigneReception`, `LigneSortie` + 6 dépôts |
| (d) 3 | `AppSettingJpaEntity`, `AppSettingId`, `UserCenterAssignmentJpaEntity` (aucun dépôt) |
| C2 2 | `PointBiologiqueResponse`, `EntityWriteResponse` |

Tout est dans `backend/platform/src/main/java/com/hemodialyse/backend/infrastructure/persistence/{entity,repository}` (C2 : `infrastructure/web/dto/response`).
`grep -w` avant chaque suppression sur tout `backend/` (hors target) : 0 référence hors fichiers supprimés, hors `TableDesPayeursTest`
(adapté) et `backend/scripts/patient-couverture-pg16-rejeu.sh:43` (voir questions).

## DDL ajouté dans `backend/platform/src/main/resources/db/schema.sql`

- Payeurs (`caisse_assurance`, `agence`, `centre_payeur`) : **DDL corrigé**. Constat important : avec `defer-datasource-initialization: true`, Hibernate
  crée les tables AVANT `schema.sql`, donc les `CREATE TABLE IF NOT EXISTS` existants étaient des no-op et divergeaient du schéma réel (50 / 500 / NOT NULL
  contre 255 / nullable). Le DDL reproduit désormais le réel (le même piège que l'adresse 500 vs 255 de S6c).
- (b) `emplacements`, `fournisseurs`, `articles`, `lots`, `stock_movements` + 8 index nommés comme Hibernate. `inventaire_id` / `saisi_le` de
  `stock_movements` restent ajoutés par les ALTER existants (placés plus bas dans le fichier).
- (c) `bons_commande`, `bons_commande_lignes`, `bons_reception`, `bons_reception_lignes`, `bons_sortie`, `bons_sortie_lignes` + 8 index.
- (d) `app_settings` (PK `center_id, cle`), `user_center_assignment` (UNIQUE `user_id, center_id, role_code` ; lue par `seed.sql`).

Preuve : test jetable (supprimé) qui écrit `INFORMATION_SCHEMA` (colonnes, types, longueurs, précision/échelle, nullabilité, défauts, contraintes, index) du
schéma PUBLIC complet (1 761 lignes) avant tout changement puis après (a), (b), (c), (d) : `diff` vide à chaque étape. Seul l'ordre des colonnes
d'une table diffère (non comparé, Hibernate : alphabétique après l'id).

## Autres fichiers modifiés

- `TableDesPayeursTest` : `ENTITES_JPA_CONNUES = Set.of()`, le test d'exclusion nominative devient `aucune_exclusion_toutes_les_entites_jpa_sont_scannees`
  (vérifie aussi l'inexistence des trois entités) ; effectif des lecteurs connus (5) et butoir inchangés.
- `.ddd/safety-net/covered.json` : 5 entrées retirées (`ArticleJpaEntity`, `EmplacementJpaEntity`, `FournisseurJpaEntity`, `EmplacementJpaRepository`,
  `FournisseurJpaRepository`), 0 ajoutée ; `seamAllowed` inchangé.
- ADR `.ddd/architecture/adr/0135-retrait-des-entites-jpa-mortes-de-la-plateforme-pl1.md` (rien « à confirmer » : décision purement technique).

## Tests

- Après chaque étape : compilation + `SchemaDumpJetableTest` (diff vide) ; après (a) aussi `TableDes*Test`, `TableDu*Test`.
- 3 passes ciblées (`Table*Test`, `*Stock*Test`, `*Inventaire*Test`, `*Comptabilite*Test`, `BackendApplicationTests`, `*Reception*`, `*Fournisseur*`,
  `*Article*`, `*Auth*`, `*Setup*` ...) : 317 tests, 0 échec, ordre normal x2 puis `-Dsurefire.runOrder=random`.
- Suite complète `./mvnw -o test -fae` : BUILD SUCCESS, 14 modules ; platform 3 375 tests, 0 échec, 0 erreur, 4 ignorés ; aucun test flaky connu observé
  (P8 optimisations, ControleDesPeremptions en ordre aléatoire : non déclenchés). Les traces « Cannot map null into type boolean » du journal sont des
  cas négatifs attendus des tests, pas des échecs.
- Journaux : `scratchpad/pl1-p1.log`, `pl1-p2.log`, `pl1-p3.log`, `pl1-full.log`.

## Questions ouvertes

1. `backend/scripts/patient-couverture-pg16-rejeu.sh:43` cite encore `AssurePatientId.java` et vérifie « 5 entités patient/assure/PEC » : déjà périmé depuis S6c
   (les entités ont disparu), non touché (hors périmètre). À réécrire ou retirer ?
2. `schema.sql` n'est pas joué en prod : la parité Flyway (V1-V57) de ces tables n'a pas été rejouée en PG16 (non demandé ; aucun changement côté Flyway).
3. L'ancien DDL des payeurs (50 / 500 / NOT NULL) exprimait peut-être l'intention métier ; la prod (Flyway) reste l'autorité. Contrainte à resserrer en H2 ?
4. Un fichier `.ddd/migration/reviews/pl15-decommissionnement-shared-kernel.md` non suivi est apparu pendant le travail (pas le mien).
