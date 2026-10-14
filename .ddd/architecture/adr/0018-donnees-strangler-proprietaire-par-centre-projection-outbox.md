---
status: superseded by 0024
date: 2026-10-09
decision-makers: [architecte, administrateur base de données, direction technique]
context-scope: global
research: [.ddd/research/spring-modulith-events-outbox.md]
---
# 0018 — Données : schéma propre par contexte, un propriétaire par centre et par slice, projection vers le legacy par outbox, aucun dual write

## Contexte et problème
Schéma legacy partagé (tables `gmao_*`, V5-V8). Les tables GMAO sont lues en SQL direct par d'autres contextes legacy : `SalleVueJdbcAdapter:68`, `PlanningDonneesJdbcAdapter:147`, `OptimisationDonneesJdbcAdapter:170`, `MouvementPatientJdbcAdapter:28`, `ReferentialRepositoryAdapter:112`, `DirectionCapaciteQueryService:42`, `SeanceRestController:456`, `PatientRestController:335`. ADR 0014 : le nouveau code ne touche pas aux tables legacy. Il faut un propriétaire d'écriture unique à tout instant et un retour arrière.

## Options considérées
1. Le nouveau code écrit dans les tables `public.gmao_*` — pour : zéro synchro ; contre : modèle cible contraint, viole 0014, ownership flou.
2. Dual write applicatif — interdit (incohérences silencieuses).
3. CDC logique PostgreSQL (Debezium) — pour : fiable ; contre : outillage absent, trop lourd pour du supporting.
4. **Schéma propre + sync legacy→nouveau (rattrapage incrémental idempotent) avant bascule, projection nouveau→legacy par outbox après bascule** — pour : propriété claire, rollback possible ; contre : deux traducteurs ACL.

## Décision
Option 4, par slice, avec l'ordre de vie suivant (par centre) : `legacy-propriétaire` → (migration initiale + rattrapage actif) → `shadow` → **bascule de propriété** (pilote) → `nouveau-propriétaire` + projection vers `public.gmao_*` (colonnes legacy complètes, y compris historique) → retrait. Règles : migration initiale `INSERT … SELECT … ON CONFLICT (id) DO UPDATE` rejouable, jouée d'abord sur copie ; vérification = comptages + checksum par table/centre + 50 lignes échantillonnées ; réconciliation nocturne centre par centre (écart = alerte, > 0 ligne financière = rollback) ; bascule inverse = gel d'écriture du centre (503 sur les routes d'écriture), attendre `lag_projection = 0`, retirer la ligne de routage, puis resynchroniser legacy→nouveau avant toute réactivation. Aucun `DROP`/`DELETE`/`TRUNCATE` sur les tables legacy : conservées ≥ 90 jours après retrait du code, suppression sur validation écrite uniquement.

## Conséquences
+ un seul écrivain par centre ; legacy lecteur inchangé (SQL direct continue de fonctionner) ; pas de dual write.
− projection et rattrapage à écrire et tester ; latence de projection (visée ≤ 5 s p99).
⚠ Préalable : vérifier la présence d'`updated_at` fiable sur chaque table (sinon rattrapage par checksum) ; `ddl-auto: update` → `validate` en prod (accord) pour que Hibernate ne crée rien hors Flyway. Surveillé : `lag_projection`, `ecarts_reconciliation`.

## Révision
La projection d'une slice est retirée quand plus aucun lecteur legacy ne lit la table (les 8 lecteurs ci-dessus disparaissent avec les slices Planification, Séance, Patient, Organisation). Échéance GMAO : 2028-06-30, point d'étape 2027-12-15.
