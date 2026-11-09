---
slice: pilotage-direction-pd5-lectures-publiees
status: built
date: 2026-11-09
order: 5
context: pilotage-direction
sources: [.ddd/tactical/pilotage-direction.md, ADR 0024, 0075, 0102, 0103, 0104, 0105, .ddd/safety-net/pilotage-direction.md (Audit PD5)]
---

# PD5 — Clôture du Pilotage direction (lot D)

Refactoring SUR PLACE (ADR 0024) : aucune façade, aucun flag, aucune projection, aucune migration ; mêmes tables, URL, rôles, codes, canal STOMP. Décisions : `.ddd/architecture/adr/0105-…`.

## Fait
- Audit nominatif de `application/direction` (11 fichiers restants), de ses consommateurs et du SQL cross-context : `.ddd/safety-net/pilotage-direction.md`, section « Audit PD5 ». Résultat : aucun fichier du Pilotage ne reste ; aucun SQL cross-context ; `direction_snapshot` et `direction_alert_history` n'ont que leurs deux propriétaires.
- Filet du suivi des abonnements STOMP (PD3-R1) : `platform/src/test/java/com/hemodialyse/backend/characterization/pilotagedirection/pd5/LesAbonnementsDeLaDirectionCharacterizationTest.java` (8 tests, verts sur le code existant avant toute autre action). Règle RG-DIR-109.
- Garde `TableDuPilotageTest` : liste blanche nominative des deux propriétaires + liste nominative de `application/direction` (comptes asserted, sans SQL, revue 2026-12-15).
- Documentation : ADR 0105, RG-DIR-109, annexe 16, glossaire (Abonné), roadmap, `covered.json` (tracker ajouté), `seams.md` (aucun seam).

## Décisions
- `DirectionSubscriptionTracker` reste en plateforme (événements Spring WebSocket + `WebSocketAuthentication`/IAM : cycle Maven sinon).
- Contrôleurs `DirectionDashboardRestController` / `DirectionSnapshotRestController`, `DirectionAccessGuard`, rendu PDF : restent en plateforme (comme lots A à C).
- `Direction{Absences,Capacite,Gmao,Indicators,StockGroupes}QueryService` et `AnonymityPolicy` : lectures d'autres contextes par langages publiés, NON migrées (résiduels PD5-R1 à R5).
- Comptes (`DirectionAccountService`, `SocieteAdminAccountService`, `PasswordPolicy`) : Organisation / IAM, jamais touchés (PD5-R6).

## Écarts de parité
Aucun : aucun golden modifié ni ajouté (hors le filet du tracker, test plain sans golden), aucun code de production modifié.

## Reste à faire (résiduels, butoir 2027-03-31, revue 2026-12-15)
PD5-R1 à R8 : voir ADR 0105 (propriétaires cibles : Absences, Planification, GMAO, Stock, Dossier médical, Organisation / IAM, plateforme pour le transport STOMP ; décisions métier KB-DIR-01..07).

## Plan de bascule
Sans objet (refactoring sur place, pas de façade ni de flag). Retour arrière : revert du commit ; aucune donnée touchée.

## Données
Aucune migration. Détections SELECT seulement : §71 de `.ddd/migration/verifications-production.md` (inchangé).
