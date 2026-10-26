---
slice: stock-s8-peremptions-retrait
status: built
date: 2026-10-26
order: 9
context: stock
sources: [.ddd/tactical/stock.md (§ 7.2, ControleDesPeremptions), RG-STK-060, 061, ADR 0013, 0024, 0045 (décision 8), 0046, known-bugs.md KB-STK-53 à 56, .ddd/safety-net/stock-s8-peremptions-retrait.md]
---

# S8 — Péremptions notifiées, événement `LotProcheExpiration` et retrait de `bc-article`

Refonte sur place (ADR 0024) : pas de façade, de routage, de projection ni de métrique ; mêmes tables, mêmes URLs. Décisions de
l'utilisateur du 2026-10-24, consignées dans l'ADR 0046 (`proposed`).

## Correction de la fiche (elle était en retard sur le code)

`bc-stock` n'existait plus (61 fichiers retirés par S5 à S7, relais temps réel déjà portés par le contexte) ; restaient
`bc-article` (5 fichiers + 1 test), l'adaptateur `ArticleRepositoryAdapter`, le contrôle `ExpirationAlertScheduler` et le verrou
ArchUnit `la_seance_n_appelle_plus_le_bon_de_sortie_legacy`. Les six événements d'intégration prévus : un seul créé.

## Fait

| Couche | Livré |
|---|---|
| Domaine (`contexts/stock/domain/peremptions`) | `RegleDesPeremptions` : J+30 inclus, périmé le lendemain puis chaque semaine (sans état) |
| Langage publié | `LotProcheExpiration` (contrat v1, version dans le contenu, identifiant déterministe) ; `NotificationsDesPeremptions` (besoin fourni par la plateforme) |
| Application (`application/peremptions`) | `ControlerLesPeremptions` (centres actifs, articles actifs, jour du centre, publication en unité de travail puis notification, échec d'un centre isolé) ; ports `CentresActifs`, `EvenementsDePeremption` |
| Adapters sortants | `CentresActifsLegacy` (ACL), `EvenementsDePeremptionSpring` (registre Modulith) ; plateforme : `NotificationsDesPeremptionsLegacy` + `NotificationService.notifyLotsProchesPeremption` (alerte durable `LOTS_PROCHES_PEREMPTION`, ADMIN + PHARMACIEN) |
| Adapter entrant | `ControleDesPeremptionsPlanifie` (07:00, `app.stock.fuseau-par-defaut` puis `app.absences.fuseau-par-defaut`, `Africa/Algiers`) ; `ExpirationAlertScheduler` supprimé |
| Retrait | module `bc-article` (poms, Dockerfile, imports) ; `ArticleRepositoryPort` et `ArticleRepositoryAdapter` remplacés par `CatalogueDArticles` dans `SeanceDomainService`, `SeanceRaccourciDomainService` et leurs services d'application (seam minimal, voir ci-dessous) ; `ArticleJpaRepository` supprimé, `ArticleJpaEntity` conservée (crée `articles` en H2 de test) |
| Codes d'erreur | article inactif des raccourcis et des consommables de la Séance : 400 (était 422) ; date illisible de `/direction/stock-groupes` : 400 (était 500) via `DatesIllisiblesDeLaDirectionAdvice` |
| Frontend | présentation de la notification `LOTS_PROCHES_PEREMPTION` (+ spec) ; i18n fr/en/ar/kab |
| Architecture | `FrontieresDesContextesTest` : plus aucun import de `domain.stock` / `domain.article` ; la Séance n'utilise le Stock que par son langage publié (le verrou `BonSortieUseCase` est retiré) |

## Mécanique des périmés (sans nouvelle table)

Jours écoulés depuis la péremption `d` : `d = 1` première fois, puis `d ≡ 1 (mod 7)` (J+1, J+8, J+15…). Un lot **reçu déjà périmé** est signalé
au premier contrôle qui suit sa réception (lendemain du jour de création du lot, une fois). Rejouable, sûr en multi-instance (alerte à
identifiant déterministe, insertion concurrente absorbée par l'ACL). **Limite documentée** : si le contrôle de J+1 est manqué (panne,
redéploiement), un lot qui vient de périmer reste silencieux jusqu'au rappel de J+8. Aucune migration.

## Événements

`LotProcheExpiration` v1 seul (un par lot signalé et par jour). **Différés jusqu'à l'existence de consommateurs** :
`ReceptionValidee`, `SortiePourSeancesEnregistree`, `RapprochementValide`, `PmpRecalcule`, `InventaireCloture`. Les trois relais
temps réel (`MouvementsDeStockModifies`, `VerrousDeRecalculModifies`, `InventaireModifie`) sont inchangés. La Comptabilité
continue de lire `OperationsDeStock`. Aucun consommateur de production pour `LotProcheExpiration` : la notification est directe ;
un écouteur de test (`@ApplicationModuleListener`) prouve la livraison et l'idempotence (14 livraisons, 7 identifiants).

## Parité et écarts (filet S8 : 23 scénarios)

| Classe de filet | Scénarios | Résultat |
|---|---|---|
| `ControleDesPeremptionsCharacterizationIntegrationTest` | 7 | 7 nouveaux goldens `*_nouveau_code` ; 7 anciens en `_ecarts-legacy/peremptions` (KB-STK-53) |
| `ArticlesDeLaSeanceCharacterizationIntegrationTest` | 2 (18 goldens) | 16 inchangés ; 2 écarts (`raccourcis_article_inactif`, `consommable_article_inactif`, KB-STK-56) |
| `ValorisationDeLaDirectionCharacterizationIntegrationTest` | 6 (18 goldens) | 17 inchangés ; 1 écart (`date_illisible`, KB-STK-55) |
| `EvenementsPublieDuStockCharacterizationIntegrationTest` | 8 | inchangés (relais conservés, KB-STK-54) |

Parité : 21 scénarios sur 23 sans écart ; les 2 restants sont les écarts décidés (péremptions : notification ; erreurs : 400). Ajout du 2026-10-26 : 2 scénarios de validation de séance (article inactif → 400, KB-STK-57 ; inconnu → 400 inchangé).

## Seam (fermé)

Fichiers legacy édités avec accord de l'utilisateur (24/10) : `ArticleJpaEntity` (commentaire seul), `SeanceDomainService`, `SeanceRaccourciDomainService`,
`SeanceApplicationService`, `SeanceRaccourciApplicationService` (constructeur/port `ArticleRepositoryPort` → `CatalogueDArticles`,
codes d'erreur), `DatesIllisiblesDeLaDirectionAdvice` (route), `NotificationService` (méthode `notifyLotsProchesPeremption` et type
durable), `PrescriptionMedicaleResponse` (commentaire). Inscrit dans `.ddd/safety-net/seams.md` ; `seamAllowed` vide.

## Preuves

- Domaine et application (doubles en mémoire) : `RegleDesPeremptionsTest` (6), `ControlerLesPeremptionsTest` (10), `CentresActifsLegacyTest`,
  `ControleDesPeremptionsPlanifieTest` ; plateforme : `NotificationsDesPeremptionsLegacyTest` (4).
- PIT (code nouveau : `domain.peremptions`, `application.peremptions`) : 32 mutants, 32 tués (100 %), `backend/scripts/pit-s8-peremptions.sh`.
- Contrat PostgreSQL 16 jetable (port 55444) : `PeremptionsPostgresContractIT` + constat SQL sur le schéma Flyway pur,
  `backend/scripts/stock-s8-pg16-rejeu.sh`, code de sortie 0.
- ArchUnit (`ArchitectureHexagonaleTest` du contexte, `FrontieresDesContextesTest`) et `check-arch.ts` : 0 violation.
- Build complet `./mvnw -o test` : voir la section « Build complet » ci-dessous.

## Bascule, retour arrière, retrait

Sans flag (ADR 0024). Retour arrière = version précédente (aucune migration ; les alertes déjà créées restent dans le journal des
notifications, purgé selon la rétention habituelle). Avant la livraison : `verifications-production.md` point 30 (volume de la
première alerte par centre, destinataires). Le legacy de la slice est déjà retiré.

## Points à valider

**Restent à l'utilisateur**

1. Texte et destinataires de la notification (ADMIN, PHARMACIEN) ; traduction kabyle (fr/en/ar à relire aussi).
2. Contrôle SELECT du point 30 de `verifications-production.md` (volume de la première alerte par centre) avant la livraison.

**Traités le 2026-10-26** (revue `.ddd/verify/review-stock-s8.md` et points du rapport)

- Article inactif à la validation d'une séance : 400 conservé, tracé par KB-STK-57, test unitaire, golden `validation_article_inactif_nouveau_code` (ancien 422 reconstitué en `_ecarts-legacy`).
- ACL tolérante à l'insertion concurrente de l'identifiant déterministe (test).
- Lot reçu déjà périmé signalé dès le contrôle suivant sa réception ; limite « J+1 manqué → rappel J+8 » documentée (ADR 0046).
- Horloge fixe (24/10/2026) dans l'IT PostgreSQL et le filet des péremptions.
- Fuseau : UTC par défaut sans écart avec 07:00 Alger (06:00 UTC, même date) ; code inchangé, documenté (ADR 0046).
- `LotProcheExpiration` sans consommateur de production : dit dans `tactical/stock.md` et l'ADR.
- Détail `lots` retiré de la notification (l'écran n'utilise que les comptes et la date) ; commentaire daté de retrait sur `ArticleJpaEntity` (avant le 2026-12-31).

## Build complet

`./mvnw -o test` (2026-10-26, après correction des constats de revue) : BUILD SUCCESS, 16 modules ; plateforme 0 échec ; PIT 32/32 ; PostgreSQL 16 vert ; contexte Stock vert. Frontend : `notification-presentation.util.spec.ts` 14 tests verts (ni ng serve ni Playwright). Le contexte Stock est prêt pour `/ddd-verify`.
