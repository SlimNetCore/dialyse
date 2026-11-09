---
status: accepted
date: 2026-11-09
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-09), architecte]
context-scope: pilotage-direction, plateforme (instantanés mensuels de la Direction)
related: ADR 0024 et 0075 (refactoring sur place), 0102 (lot A, PD1 + PD4) ; `.ddd/migration/slices/pilotage-direction-pd2-instantanes-mensuels.md`, `.ddd/safety-net/pilotage-direction.md`, KB-DIR-03, KB-DIR-06
---

# 0103 - Pilotage direction, lot B (PD2) : les instantanés mensuels passent dans le contexte ; la plateforme garde le jeton, la route et le rendu PDF

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Refactoring SUR PLACE (ADR 0024/0075) : même table `direction_snapshot`, mêmes URL `/api/v1/direction/snapshots*`, mêmes rôles, mêmes codes (422 + code), aucune façade, aucun flag, aucune projection, aucun événement, aucune migration. SQL repris MOT POUR MOT.

## Parité
Les 4 goldens PD2 (`instantanes/*`), les 13 autres goldens PD1/PD3/PD4, `DirectionSnapshotIntegrationTest` (5) et `DirectionReportIntegrationTest` (3) : INCHANGÉS. **Aucun écart** : aucun `.approved` modifié, aucun `_nouveau_code`, aucune archive `_ecarts-legacy`. Seul l'appel de la tâche du filet change de cible (`figerLeMoisEcoule()` du langage publié au lieu de `generateForPreviousMonth()`).

## Comportements reproduits (statu quo, à confirmer)
| Réf. | Comportement reproduit | Confirmation attendue |
|---|---|---|
| KB-DIR-03 | « Mois courant » et mois précédent de la tâche = horloge de la JVM (UTC), jamais le fuseau du centre (la fiche d'origine annonçait `centers.fuseau_horaire` : non reproduit, le legacy ne le lit pas) | Métiers : fuseau de la société, d'un centre de référence ou `Africa/Algiers` ? |
| KB-DIR-06 a | Instantané antérieur aux répartitions servi avec `breakdown: null` | — |
| KB-DIR-06 b | GET d'un mois futur : `SNAPSHOT_INTROUVABLE` (pas `MOIS_NON_TERMINE`) | Métiers : contrôle du futur en lecture ? |
| KB-DIR-06 c | Trois horodatages : `generated_at` de la base (`CURRENT_TIMESTAMP`), `genereLe` des vues, bornes du mois | — |
| KB-DIR-06 d | Société active sans centre : instantané figé vide (totaux à zéro) | — |
| KB-DIR-06 e | Contenu non JSON : listé, mais sa lecture répond 500 | Métiers : 422 métier ? (voir §9.7 de l'audit) |
| Idempotence | `DuplicateKeyException` avalée à l'INSERT ; la capture relit ensuite l'instantané | — |

## Décisions
1. **Domaine** : `MoisDInstantane` (lecture `AAAA-MM`, `exigerCapturable` : écoulé, 60 mois inclus), `InstantaneMensuel` (agrégat à une entité, immuable, fabriqué par `figer`/`figerLeMoisEcoule`), `ContenuFige`. Aucune horloge ni identifiant dans le domaine ; l'horodatage de capture reste celui de la base.
2. **Application** : `CapturerUnInstantaneMensuel`, `ConsulterUnInstantane`, `ListerLesInstantanes`, `CapturerLesInstantanesDuMoisEcoule` ; ports `InstantanesMensuels` (repository), `ContenuDuMoisAFiger`, `SocietesActives`. Le mois est contrôlé AVANT toute lecture ; le contenu n'est constitué que si l'instantané n'existe pas.
3. **Adaptateurs** : `InstantanesMensuelsJdbc` (propriétaire de `direction_snapshot`) ; `ContenuFigeParLeTableauDeBord` (vue d'ensemble et répartitions par le langage publié du lot A, même `ObjectMapper`, même ordre de clés `overview, indicators, breakdown`) ; `SocietesEtCentresJdbc` implémente aussi `SocietesActives`.
4. **Indicateurs** : restent en plateforme (DM9, ADR 0101). Nouveau port FOURNI `IndicateursAFigerFournis` (langage publié) dont la valeur est un objet OPAQUE sérialisé tel quel : c'est le seul moyen de garder le JSON figé identique aux instantanés existants sans que le contexte possède le modèle des indicateurs. Traducteur `IndicateursAFigerParLaPlateforme` (nouveau, ajouté à `covered.json` comme son voisin du lot A). Alternative écartée : un port typé (copie de ~40 champs du modèle des indicateurs, risque de dérive du JSON figé).
5. **Langage publié** `InstantanesMensuelsDeLaDirection` : `lister`, `lire`, `figer`, `figerLeMoisEcoule` ; les enregistrements portent les noms du JSON servi (`mois`, `generatedAt`, `overview`, `indicators`, `breakdown`) ; le contenu relu est un `JsonNode` (nœud manquant = servi `null`).
6. **Contrôleur en plateforme (justifié)** : `DirectionSnapshotRestController` reste dans la plateforme car il porte `@PreAuthorize`, `DirectionAccessGuard` (revérifié à chaque appel) et le rendu PDF (`DirectionReportPdfService`, qui consomme désormais l'`Instantane` publié ; `RapportDUnInstantane` reste son traducteur vers le modèle de rapport publié). Même décision que le lot A (ADR 0102 §8). Seuls les types changent.
7. **Planificateur dans le module** : `CaptureDuMoisEcoulePlanifiee` (`interfaces/planifie`), même cron `0 30 2 1 * *`, lu par un test (le câblage n'est pas couvert par le filet). `@EnableScheduling` est porté par `BackendApplication`.
8. **Retraits** : `DirectionSnapshotService` et `DirectionSnapshotScheduler` (plateforme). `seamAllowed` n'a servi qu'à CRÉER `IndicateursAFigerParLaPlateforme` (remis à `[]`).
9. **Gardes** : `TableDuPilotageTest` (propriétaire nominatif de `direction_snapshot`, plus aucun lecteur de plateforme ; `DirectionAlertHistoryService` reste le lecteur PD3), `TableDeLOrganisationTest` (entrée du service retirée), ArchUnit du module (inchangé, vert), `FrontieresDesContextesTest` (inchangé, vert).

10. **Suites de la revue (n2)** : le langage publié expose `JsonNode` (Jackson 3) parce qu'il reproduit exactement le corps servi ; couplage assumé, à lever avec PD5. `ContenuFigeParLeTableauDeBord` vit dans `infrastructure/persistence` : à déplacer vers `infrastructure/acl/tableaudebord` au prochain passage. La forme du JSON des indicateurs figés est gardée par `IndicateursAFigerParLaPlateformeIntegrationTest`.

## Résidus assumés (revue 2026-12-15, butoir 2027-03-31)
- PD2-R1 : `SocietesEtCentresJdbc` lit `societes` (liste des sociétés actives) en SQL : à remplacer par `organisation.v1` (jalon O4).
- PD2-R2 : port `IndicateursAFigerFournis` à valeur opaque, jusqu'à la reprise des indicateurs par un contexte (PD5).
- PD2-R3 : le contrôleur et le jeton restent en plateforme (comme le lot A).
- PD2-R4 : KB-DIR-01 (§69.1 de `verifications-production.md`) : les instantanés déjà figés portent les totaux bruts ; ce lot n'y change rien.

## Questions ouvertes
1. Fuseau de référence du mois courant (KB-DIR-03). 2. Contrôle du futur en lecture (KB-DIR-06 b). 3. Contenu illisible : 422 plutôt que 500 (KB-DIR-06 e). 4. Mois d'instantané : faut-il documenter ce que « dernier jour du mois » (RG-DIR-090) signifie, la capture se faisant le 1er à 02:30 ?
