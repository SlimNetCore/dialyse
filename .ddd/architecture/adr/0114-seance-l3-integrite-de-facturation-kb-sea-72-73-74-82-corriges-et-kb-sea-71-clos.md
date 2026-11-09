---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (demande explicite de CORRIGER les anomalies reproduites jusqu'ici, 2026-11-09 ; confirmation des métiers Facturation, Comptabilité et Stock attendue au plus tard le 2026-12-15), architecte]
context-scope: seance (infrastructure/persistence, ports publiés pour la Facturation), plateforme (migrations Flyway V54 et V55, goldens), tests de contrat (mémoire, H2, PostgreSQL 16)
related: ADR 0096 et 0097 (SE7c2, reproduction), 0024 et 0075 (refactoring sur place) ; `.ddd/migration/corrections-kb-2026-11-09.md` (lot L3) ; `.ddd/safety-net/known-bugs.md` (KB-SEA-71 à 75, 82) ; `.ddd/migration/verifications-production.md` §62 et §76 ; RG-SEA (annexe 16)
---

# 0114 - Lot L3 (Séance, argent et intégrité de facturation) : KB-SEA-72, 73, 74 et 82 CORRIGÉS, KB-SEA-71 CLOS

Ce n'est PAS une migration à parité : le comportement CHANGE volontairement, à la demande de l'utilisateur. L'ADR 0097 avait REPRODUIT ces anomalies (sauf KB-SEA-71)
pendant le refactoring sur place (mêmes tables, mêmes URL, mêmes rôles). Ni façade, ni flag, ni projection. Aucune donnée n'est modifiée, aucune lecture n'est modifiée.
La **confirmation des métiers reste à obtenir (au plus tard le 2026-12-15)**.

## Décisions
| KB | Règle appliquée | Où |
|---|---|---|
| KB-SEA-72 | Une seule définition de « mémoire d'exclusion présente » : non nulle ET `LENGTH(TRIM(...)) > 0` (au moins un caractère hors espace). La lecture (`trouver`) la calcule par le MÊME SQL (`CASE WHEN ... THEN statut_avant_exclusion END`) ; `lierALAbsence` et `reinclure` la reprennent. Une mémoire blanche est refusée aux deux écritures (« non modifiable pour exclusion » / « non reinclusable »), comme le domaine la refusait déjà | `FacturationDesSeancesJdbc` (constante `MEMOIRE_PRESENTE`), double en mémoire aligné |
| KB-SEA-73 | Garde du port : `exclure` ajoute `AND statut IN ('VALIDEE','SIGNEE')` à `statut = ?` (le port redit `estFacturable()` du domaine). `exclure` avec « son propre statut » ne réussit plus pour `validee`, `ABSENT`, `facturee`, etc. La casse du statut elle-même n'est PAS normalisée (KB-FAC-17 reste reproduit, voir KB-SEA-82) : comparaison déterministe en casse exacte, refus explicite sinon | idem |
| KB-SEA-74 | (1) Garde applicative : `LiaisonDesSeancesJdbc.lier` n'écrit `facture_id` que si la facture EXISTE et appartient au MÊME centre (`EXISTS (SELECT 1 FROM factures ...)` dans l'UPDATE ; la Facturation enregistre la facture avant la liaison, dans la même transaction). (2) Clé étrangère `fk_seances_facture` (migration V54), `NOT VALID`, sans `ON DELETE CASCADE`, PostgreSQL seulement | voir « Migrations » |
| KB-SEA-82 | Contrainte `ck_seances_statut_canonique CHECK (statut = UPPER(TRIM(statut)))` (migration V55), `NOT VALID`, PostgreSQL seulement. Les cinq lecteurs ne sont PAS modifiés (le catalogue l'écarte : divergence avec la Facturation) | voir « Migrations » |
| KB-SEA-71 | CLOS, aucun code modifié : déjà corrigé (ADR 0097, garde `facture_id IS NULL` de `lierALAbsence`, `FacturationDesSeancesJdbc.java`, vérifié dans le code actuel : `facture_id IS NULL AND statut = 'ABSENT'`) | documentation seule |

## Migrations (prudence : jamais de blocage du démarrage)
Les migrations s'appliquent à PostgreSQL par Flyway (`backend/platform/src/main/resources/db/migration`, dernière version avant le lot : V53). La base H2 des tests et du
développement est bâtie par `db/schema.sql` + Hibernate, SANS Flyway : il n'y a donc pas deux scripts, un seul script PostgreSQL ; H2 ne reçoit pas les contraintes (précédent : V43).
| Version | Contenu | NOT VALID |
|---|---|---|
| V54 `seances_facture_id_cle_etrangere` | `ALTER TABLE seances ADD CONSTRAINT fk_seances_facture FOREIGN KEY (facture_id) REFERENCES factures (id) NOT VALID` | oui |
| V55 `seances_statut_canonique` | `ALTER TABLE seances ADD CONSTRAINT ck_seances_statut_canonique CHECK (statut = UPPER(TRIM(statut))) NOT VALID` | oui |

`NOT VALID` : PostgreSQL ajoute la contrainte sans relire les lignes existantes ; une donnée de production sale NE PEUT PAS faire échouer Flyway ni empêcher l'application de
démarrer (le catalogue prévoyait au contraire un démarrage bloqué par un orphelin). La contrainte s'applique aux nouvelles écritures. H2 ne supporte pas `NOT VALID` et son
`ADD CONSTRAINT` validerait les lignes ; poser une clé étrangère dans H2 casserait de surcroît les fixtures qui posent un `facture_id` sans facture : la garde applicative (testée sur
mémoire, H2 et PostgreSQL 16) est donc la protection portable, la contrainte est la défense en profondeur de production.

**Validation manuelle (jamais par Flyway)**, après nettoyage, avec les détections ci-dessous à 0 ligne :
`ALTER TABLE seances VALIDATE CONSTRAINT fk_seances_facture;` puis `ALTER TABLE seances VALIDATE CONSTRAINT ck_seances_statut_canonique;` (verrou `SHARE UPDATE EXCLUSIVE`).

Particularité à connaître : PostgreSQL réévalue un `CHECK ... NOT VALID` sur toute ligne MISE À JOUR (toute colonne). Une ligne déjà non canonique ne pourra plus être mise à jour tant qu'elle
n'est pas normalisée (refus explicite voulu ; elle reste lisible). La clé étrangère `NOT VALID` n'est contrôlée que lorsque `facture_id` change. D'où l'exécution de la détection AVANT le déploiement.

## Détections à exécuter AVANT le déploiement (SELECT seule, production)
1. Orphelins (§62.2) : `SELECT s.center_id, s.id, s.facture_id FROM seances s LEFT JOIN factures f ON f.id = s.facture_id WHERE s.facture_id IS NOT NULL AND f.id IS NULL;`
2. Facture d'un autre centre (§62.2) : `SELECT s.center_id, s.id FROM seances s INNER JOIN factures f ON f.id = s.facture_id WHERE f.center_id <> s.center_id;`
3. Statuts non canoniques, casse et blancs (§62.7 et §76.1) : `SELECT center_id, statut, count(*) FROM seances WHERE statut <> upper(trim(statut)) GROUP BY center_id, statut;`
4. Mémoires d'exclusion blanches (§62.6) : `SELECT center_id, count(*) FROM seances WHERE statut_avant_exclusion IS NOT NULL AND trim(statut_avant_exclusion) = '' GROUP BY center_id;`
Aucune de ces lignes n'empêche le déploiement ; chacune est un préalable à `VALIDATE CONSTRAINT` (1, 2 non bloquante, 3) ou à la bonne lecture du comportement corrigé (4 : ces séances ne sont plus réincluses ni liées à une absence).

## Suppression de factures (prérequis du catalogue)
Recherche `DELETE FROM factures` dans le code de production : un seul chemin, `RepriseDesSoldesJdbc.supprimer` (annulation d'une reprise de solde d'ouverture, appelée par le retour arrière
de la migration de données), qui supprime règlements et lignes puis la facture d'ouverture. Une facture de reprise ne porte aucune séance (`ouvrir` n'écrit aucun `facture_id`) ; la seule
facture liée à des séances est une facture émise, qu'aucun chemin de l'application ne supprime. Les autres occurrences sont des nettoyages de tests. Conséquence : la clé étrangère ne change aucun
flux existant ; si un accès direct ou un futur chemin supprimait une facture encore liée, PostgreSQL le refuse (voulu, à valider par la Facturation). Aucune déliaison n'est ajoutée.

## Tests
- Contrat `FacturationDesSeancesContrat` (double en mémoire, H2, PostgreSQL 16) : refus d'exclure hors validée/signée (casse, blanc, brouillon), refus des écritures sur mémoire blanche et maintien sur mémoire réelle,
  refus de lier à une facture inexistante ou d'un autre centre. Vérifié ROUGE avant la correction (3 tests sur 2 adapters : 6 échecs) puis vert.
- `LiaisonConcurrentePostgresContractIT` : course de deux émissions inchangée (factures réelles), clé étrangère refuse un identifiant inexistant, une facture liée ne se supprime pas, le CHECK refuse un statut
  non canonique sans toucher aux lignes déjà présentes ; script `backend/scripts/seance-se7c2-pg16-rejeu.sh` mis à jour (contraintes V54 et V55 présentes et NOT VALID).
- Les contrats de LECTURE rejoués sur PostgreSQL 16 posent volontairement des statuts non canoniques et des identifiants de facture sans facture : ils retirent les deux contraintes de la base JETABLE (`TableDesSeances`).
- Goldens (`facturation/simulation/se7c2-ecritures`) : `statut_avant_exclusion_blanc` et `statut_en_minuscules_matrice_des_ecritures_et_lectures` deviennent `*_nouveau_code` ; les anciens sont archivés sous
  `_ecarts-legacy/se7c2-ecritures/*_avant_l3.legacy.json`. Seuls changent : `lierALAbsence` et `reinclure` sur mémoire blanche (acceptés puis refusés) et `exclure_avec_le_statut_lu` (accepté puis refusé) pour les six
  statuts non facturables de la matrice. Approuvés un par un, jamais `-Dcharacterization.approve=true`. Les goldens KB-SEA-47, 71 et 75 sont inchangés.

## Questions ouvertes
1. Production : les requêtes 1 à 4 rendent-elles 0 ligne ? Sinon, quelle réparation pour chaque ligne (restaurer la facture, ou `facture_id` à NULL et statut `VALIDEE` ; normaliser le statut statut par statut) ?
2. La Facturation veut-elle conserver la possibilité de supprimer une facture liée (aujourd'hui impossible par l'application) ?
3. Valider `UPPER(TRIM(statut))` comme forme canonique pour les futurs statuts.
4. KB-SEA-75 (deux définitions de « séance facturée ») reste à trancher par la comptabilité ; non traité.

## Conséquences
- Retour arrière : revert du code ; en base, `ALTER TABLE seances DROP CONSTRAINT fk_seances_facture` et `... ck_seances_statut_canonique` (aucune donnée modifiée). Les goldens `_avant_l3.legacy.json` décrivent l'ancien comportement.
- Résiduel : un statut de mémoire composé de tabulations seulement est désormais « présent » pour la lecture comme pour les écritures (TRIM ne retire que les espaces) ; la colonne est un code de statut de 20 caractères, aucune donnée réelle n'est attendue.
