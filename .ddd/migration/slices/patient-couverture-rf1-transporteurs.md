---
slice: patient-couverture-rf1-transporteurs
status: planned
date: 2026-11-10
order: 6
context: patient-couverture
sources: [ADR 0121, ADR 0024, ADR 0079, .ddd/migration/reviews/analyse-referentiels-orphelins.md, .ddd/migration/slices/organisation-o5-retrait-bc-referential.md]
---

# RF1 - Transporteurs et catégories de transport chez Patient & couverture

> Propriété décidée par l'ADR 0121 (décision 4), **à confirmer métiers <= 2026-12-15**. Refactoring SUR PLACE : mêmes tables (`transporteur`, `categorie_transport`), mêmes URLs, comportement INCHANGÉ ; aucun golden `_nouveau_code` attendu. Première slice de la série (aucun cycle, un seul consommateur métier).

## Comportement observé à caractériser (avant tout déplacement)
| # | Scénario | Source |
|---|---|---|
| 1 | CRUD admin `/api/v1/admin/referentials/transporteurs` : création, modification, suppression, rôles ADMIN/SUPERADMIN, garde de centre | `ReferentialAdminRestController` (l.47-49), `ReferentialAdminTables.java:69-73` |
| 2 | Unicité de la clé naturelle `nom` par centre ; refus de doublon | `ReferentialKind.java:74` |
| 3 | `telephone` de type `phone` : formats acceptés et refusés | `ReferentialKind.java:74` |
| 4 | Suppression refusée si utilisé (`patients.transporteur_aller_id` OU `transporteur_retour_id`) : message et code | `ReferentialAdminTables.java:72` |
| 5 | Import CSV/Excel + gabarit : vérification, import, rejeu, refus | admin générique |
| 6 | Lecture `GET /api/v1/referentials/transporteurs` (forme `RefItem`, tri, garde de centre 403) et `/categories-transport` | `ReferentielsLegacyControleur.java:75,80`, `ReferentielsCommunsJdbc.java:74` |
| 7 | Invalidation des caches `ref.*` après écriture | `CacheConfig.java:48-57` |
| 8 | Fiche patient : nom du transporteur affiché, filtres par transporteur | `LectureDesPatientsJdbc.java:83-85,156` |
| 9 | `categorie_transport` : mêmes scénarios 1 à 7 (clé `libelle`) | schema.sql:109 |

## Goldens à prévoir
Environ 20 : CRUD (créer, modifier, doublon, supprimer, supprimer utilisé aller, supprimer utilisé retour) x 2 tables, import (4), lectures (3), cache (2). Approuvés à la main un par un ; un écart découvert va dans `.ddd/safety-net/known-bugs.md` (reproduire ou corriger : décision tracée dans l'ADR de la slice). Rejeu PostgreSQL 16 du contrat.

## Langage publié à créer (`patientcouverture.v1`)
`Transporteurs` : `lister(centre)`, `nom(centre, id)`, `existe(centre, id)` ; `CategoriesDeTransport.lister(centre)`. Aucun lecteur hors PC n'a de SQL direct sauf Organisation (lecture `/referentials`) : le port sert à `ReferentielsLegacyControleur` (ou à sa remplaçante). Contrat sur double, H2 et PG16.
Usage à la suppression : lecture interne (les patients sont à PC) : aucun port d'usage publié.

## Garde de table nominative
`TableDesTransporteursTest` (modèle `TableDeLOrganisationTest`) : tables `transporteur`, `categorie_transport` ; scanne tout le code de production hors PC ; liste blanche nominative et DATÉE, effectif asserté, échec sur entrée morte. Lecteurs attendus à l'ouverture : `ReferentialAdminJdbcAdapter` (SQL dynamique), `ReferentielsCommunsJdbc` (Organisation) ; Planification `ContraintesDesPatientsJdbc` (transporteurs du patient : jalon déjà inscrit à PC5 #14) à relever.

## Conditions de retrait du CRUD générique pour ces types
1. Filet RF1 vert sur le CRUD de PC aux mêmes URLs (parité stricte).
2. `ReferentialKind` ne liste plus `TRANSPORTEUR` et `CATEGORIE_TRANSPORT` ; le routage de l'admin générique délègue à PC ; `ReferentialAdminTables` sans l'entrée transporteur.
3. Lectures `/referentials/transporteurs` et `/categories-transport` servies depuis PC ; code Organisation correspondant retiré ; entrées retirées de `TableDeLOrganisationTest` si présentes.
4. Garde RF1 verte (effectif asserté), build complet vert, `check-arch` 0 violation, confirmation métier ou ADR 0121 toujours `accepted`.
Retour arrière : revert Git.

## Effort
1 slice, environ 1 session (2 tables simples, aucune règle métier, aucun cycle). Risque faible.

## Questions ouvertes
- Terme absent des glossaires : « Transporteur », « Catégorie de transport » ajoutés en `proposed` (glossaire Patient & couverture).
- Faut-il refuser la désactivation plutôt que la suppression d'un transporteur utilisé ? (comportement actuel : refus ; inchangé.)
- `categorie_transport` : lien avec les patients non vérifié ; à confirmer au relevé de la slice.
