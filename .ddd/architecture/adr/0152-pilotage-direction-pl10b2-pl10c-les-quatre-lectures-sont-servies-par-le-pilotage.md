---
status: accepted
date: 2026-11-12
sources: pl10b-filet.md, pl10b1-realisation.md, ADR 0105, ADR 0150, ADR 0151, known-bugs.md (KB-PL10b-1..6 ; PD5-R1 à R4 et R9)
---

# ADR 0152 - PL10b2 + PL10c : les quatre lectures de la Direction (gmao, stock-groupes, capacite, absences) sont servies par le Pilotage

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, tables, codes. Après PL10b1, `DirectionDashboardRestController` (platform) ne servait plus que `/gmao`, `/stock-groupes`, `/capacite`, `/absences`, avec quatre `Direction*QueryService` qui lisaient déjà les langages publiés de GMAO, Stock, Planification et Absences (aucun SQL), `AnonymityPolicy` (seuil 5, copie de `SeuilDAnonymat`) et `ValorisationArticlesService` (cache de la valorisation).

## Décisions
1. Le Pilotage compose les quatre lectures : cas d'usage `ConsulterLaGmaoDesCentres`, `ConsulterLeStockDesGroupes`, `ConsulterLaCapaciteDesCentres`, `ConsulterLesAbsencesDesCentres` (application, sans framework) et adapter entrant `LecturesDeLaDirectionControleur`. Les JSON sont identiques (mêmes noms de champs).
2. Les données restent fournies par leurs propriétaires, consommées par une ACL du Pilotage par langage publié (ports `GmaoDesCentres`, `StockDesGroupes`, `CapaciteDesCentres`, `AbsencesDesCentres` ; ACL `GmaoParLaGmao`, `StockDesGroupesParLeStock`, `CapaciteParLaPlanification`, `AbsencesParLesAbsencesDesPatients`). Le chiffre d'affaires et les séances de `/absences` réutilisent les ports existants `FacturationDesCentres` et `SeancesRealiseesDesCentres`. Aucun SQL du Pilotage vers les tables des autres contextes. Le cache de valorisation passe dans l'ACL du Stock (même nom de cache `stock.valorisation.articles`, vidé par `StockEvenementsListener` de la plateforme).
3. `AnonymityPolicy` est supprimée : le seuil unique est `SeuilDAnonymat` (valeur 5, inchangée ; donc aucun chiffre ne change). KB-PL10b-5 CORRIGÉ ; valeur à confirmer métiers/DPO.
4. Écarts décidés par l'utilisateur (2026-11-12), CORRIGÉS sur les quatre routes comme en PL10b1 : KB-PL10b-1 (403 avant 400, société inactive comprise), KB-PL10b-2 (consultation journalisée `DIRECTION_TABLEAU_DE_BORD_CONSULTATION`), KB-PL10b-3 (404 chemins voisins, 406 `Accept` hors JSON, XML refusé), KB-PL10b-4 (fin par défaut = jour d'Alger, fin des 4 routes), KB-PL10b-6 (dates répétées = 400, vides ignorées). Cumul de rôles : REPRODUIT (à confirmer).
5. Les erreurs de période (`PERIODE_INVALIDE`, `PERIODE_TROP_LONGUE`) sont portées par `Periode` du Pilotage (422, même corps) ; les quatre services levaient `BusinessException`, même réponse.
6. Suivi STOMP (`WebSocketAuthentication`) : le Pilotage publie `AccesALaDirection` (`estHabilite(utilisateur, société)`, implémenté par `SessionLegacy`, même requête). `WebSocketAuthentication` (inscrit à `covered.json` avec l'accord de l'utilisateur) l'utilise à la place de la garde ; `DirectionAccessGuard` est SUPPRIMÉE (règle ArchUnit anti-retour, entrée retirée de `TableDeLOrganisationTest` : 8 -> 7).
7. Gardes datées du 2026-12-15 : `TableDuPilotageTest` devient une règle permanente (liste du reste réduite aux 3 comptes de l'Organisation, plus aucune date) ; `TableDesSeancesTest` perd l'entrée `DirectionAbsencesQueryService` ; les trois autres (`EcrivainsDeLaFactureDesSeancesTest`, `TableDuDossierMedicalTest`, `TableDesAnalysesTest`) ne contenaient aucune entrée liée à ces services : inchangées, aucune date repoussée.
8. Règles ArchUnit (`FrontieresDesContextesTest`) : les classes supprimées ne reviennent pas dans la plateforme.

## Questions ouvertes
- Seuil unique (KB-PL10b-5) et cumul de rôles : à confirmer métiers/DPO au plus tard le 2026-12-15.
