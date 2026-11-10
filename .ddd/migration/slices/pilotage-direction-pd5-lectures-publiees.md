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
- Audit nominatif de `application/direction` (11 fichiers restants), de ses consommateurs et du SQL cross-context : `.ddd/safety-net/pilotage-direction.md`, section « Audit PD5 ». Résultat : aucun fichier du Pilotage ne reste, sauf la composition de `DirectionIndicatorsQueryService` (PD5-R5, slice PD6 planifiée) ; aucun SQL cross-context ; `direction_snapshot` et `direction_alert_history` n'ont que leurs deux propriétaires.
- Filet du suivi des abonnements STOMP (PD3-R1) : `platform/src/test/java/com/hemodialyse/backend/characterization/pilotagedirection/pd5/LesAbonnementsDeLaDirectionCharacterizationTest.java` (8 tests, verts sur le code existant avant toute autre action). Règle RG-DIR-109.
- Garde `TableDuPilotageTest` : liste blanche nominative des deux propriétaires + liste nominative de `application/direction` (comptes asserted, sans SQL, revue 2026-12-15).
- Documentation : ADR 0105, RG-DIR-109, annexe 16, glossaire (Abonné), roadmap, `covered.json` (tracker ajouté), `seams.md` (aucun seam).

## Décisions
- `DirectionSubscriptionTracker` reste en plateforme : raison unique, le module n'a pas de dépendance Spring messaging/WebSocket (le cycle Maven via la constante `SOCIETE_TOPIC_PREFIX` est évitable par duplication, ce n'est pas le motif).
- Contrôleurs `DirectionDashboardRestController` / `DirectionSnapshotRestController`, `DirectionAccessGuard`, rendu PDF : restent en plateforme (comme lots A à C).
- `Direction{Absences,Capacite,Gmao,StockGroupes}QueryService` et `AnonymityPolicy` : lectures d'autres contextes par langages publiés, NON migrées (résiduels PD5-R1 à R4).
- `DirectionIndicatorsQueryService` : NON une simple lecture d'un autre contexte ; compose tableau de bord, alertes et politique d'alertes du Pilotage avec les indicateurs cliniques et du stock, et alimente 3 traducteurs de ports du Pilotage. Non migré dans ce lot (pas de filet dédié) ; propriétaire cible Pilotage ; slice `pilotage-direction-pd6-composition-des-indicateurs` (planned), résiduel PD5-R5.
- Comptes (`DirectionAccountService`, `SocieteAdminAccountService`, `PasswordPolicy`) : Organisation / IAM, jamais touchés (PD5-R6).

## Écarts de parité
Aucun : aucun golden modifié ni ajouté (hors le filet du tracker, test plain sans golden), aucun code de production modifié.

## Reste à faire (résiduels, butoir 2027-03-31, revue 2026-12-15)
PD5-R1 à R9 : voir ADR 0105 (propriétaires cibles : Absences, Planification, GMAO, Stock, Pilotage pour la composition des indicateurs (PD6), Organisation / IAM, plateforme pour le transport STOMP).

### Questions DPO / métiers KB-DIR-01..08 (à confirmer métiers / DPO au plus tard le 2026-12-15)
Décisions en vigueur = reproduction à l'identique ; détail dans `.ddd/safety-net/known-bugs.md`.
| KB | Décision en vigueur | Question | Interlocuteur |
|---|---|---|---|
| KB-DIR-01 | Totaux bruts, fuite par différence reproduite | Masquer/arrondir les totaux ? Seuil (5) ? (avec KB-MED-104) | DPO |
| KB-DIR-02 | Erreurs de requête en 500 | Aligner sur 400/405 ? | Métiers |
| KB-DIR-03 | Horloge de la JVM | Fuseau du centre ou de la société ? | Métiers |
| KB-DIR-04 | Historique d'alertes figé à l'ouverture, nouvelle ligne le même jour (R2 option a) | Une alerte par centre et par jour ? | Métiers / DPO |
| KB-DIR-05 | Séance d'un patient d'un autre centre : en vue d'ensemble, hors répartition par caisse | Règle de rattachement ? | Métiers |
| KB-DIR-06 | Instantanés : `breakdown` nul, mois futur, société sans centre, trois horodatages, payload libre | Corriger ou conserver (a à e) ? | Métiers |
| KB-DIR-07 | Alertes de stock à la date de fin de période | Date de référence ? | Métiers |
| KB-DIR-08 | Sexe par initiale, âges, caisse à zéro, en-têtes PDF coupés | Confirmer | Métiers |

## Lot L5b (2026-11-10, ADR 0118)
KB-DIR-05 CORRIGÉ avec KB-SEA-83 et 84 : la répartition par caisse compte les mêmes séances que la vue d'ensemble (la séance sur une fiche d'un autre centre reste comptée sous la caisse vide), le centre payeur (patients, séances) et l'agence figée des factures (chiffre d'affaires) sont bornés au centre, les caisses sont départagées par le code (`RepartitionDeLaSociete`). Goldens `pd1/.../breakdown_nouveau_code`, `pd4/.../rapport_en_direct_nouveau_code` ré-approuvés (archives `*_avant_l5b.legacy.json`). Rejeu PG16 `pilotage-direction-pg16-rejeu.sh` : code 0 ; son ClassNotFound (jar Organisation périmé du dépôt Maven local) corrigé par `-am`. À confirmer par les métiers et le DPO au plus tard le 2026-12-15.

## Plan de bascule
Sans objet (refactoring sur place, pas de façade ni de flag). Retour arrière : revert du commit ; aucune donnée touchée.

## Données
Aucune migration. Détections SELECT seulement : §71 de `.ddd/migration/verifications-production.md` (inchangé).
