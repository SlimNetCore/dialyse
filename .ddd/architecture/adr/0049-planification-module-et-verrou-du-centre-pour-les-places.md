---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions du 2026-10-26 au modèle tactique), architecte]
context-scope: planification
research: [.ddd/research/spring-boot-4-web-data-tx.md]
related: ADR 0005, 0010, 0011, 0012, 0015, 0024, 0043 ; `.ddd/tactical/planification.md` §3-§4, B1/B3/B4
---
# 0049 — Créer le module `contexts/planification` et protéger `PlacesDuCentre` par le verrou de la ligne `planning_parametres`

## Contexte et problème
Planification est un contexte cœur refait sur place (ADR 0024) : mêmes tables, mêmes URLs (`/api/v1/planning/**`,
`/api/v1/infirmiers/**`). Les invariants P3/P4/P22 portent sur **toutes** les places d'un centre (≈ 80-300) ; aujourd'hui
contrôle puis écriture sans verrou (B1), « un seul calcul par centre » en mémoire (B3), roulement sans verrou (B4).

## Options considérées
1. Un agrégat par patient + contrainte SQL — contre : P4 (mélange risque/sans risque sur un générateur, tous jours) n'est
   pas exprimable en contrainte ; exigerait une migration (interdite, ADR 0024).
2. **Agrégat `PlacesDuCentre` (un par centre) chargé en entier, verrou pessimiste de la ligne `planning_parametres` du
   centre** (`SELECT … FOR UPDATE`, ligne créée avec les défauts si absente, même lecture qu'aujourd'hui) — pour : aucun
   schéma nouveau, volume borné, écritures rares.
3. Verrou optimiste — contre : pas de colonne `version` (migration).

## Décision
Option 2. Module Maven `contexts-planification` (`backend/contexts/planification`, package
`com.hemodialyse.backend.contexts.planification`, couches `domain/application/infrastructure/interfaces/publishedlanguage/
configuration`, comme GMAO/Stock/Absences). Le **même verrou du centre** sert : toute écriture de place, le lancement
d'une optimisation (lecture `statut = EN_COURS` sous verrou, corrige B3) et l'application d'une proposition (ADR 0051).
Roulement : verrou de la ligne `infirmier` (B4). Délai : celui du propriétaire de la transaction, 5 s (ADR 0043) ;
refus ⇒ **409 `PLANIFICATION_ECRITURE_CONCURRENTE`** *(code proposed)*, rien n'est écrit.

## Conséquences
+ B1, B3, B4 corrigés sans migration ; une application PATIENTS = un seul agrégat.
− Toutes les écritures de place d'un centre sont sérialisées (quelques par jour : négligeable).
⚠ La fiche patient legacy rejoint ce verrou via `PlacementDesPatients` (ADR 0052) : ordre fixe *centre puis patient*
  à respecter partout ; test de concurrence H2 + PostgreSQL 16 (`*PostgresContractIT`) à P5.

## Révision
Si le nombre de placements d'un centre dépasse ~1 000 ou si un 409 apparaît en production : réexaminer (agrégat par salle).
