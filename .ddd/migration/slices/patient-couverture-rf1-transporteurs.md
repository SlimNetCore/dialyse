---
slice: patient-couverture-rf1-transporteurs
status: built
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
- `categorie_transport` : lien avec les patients non vérifié ; à confirmer au relevé de la slice. Relevé RF1 : aucun écrivain, `patients.categorie_transport_id` n'est validée par aucun code (KB-RF1-02).
- ~~Corriger KB-RF1-01 et KB-RF1-03 ?~~ Corrigés le 2026-11-10 (RF1c, ADR 0123, à confirmer métiers <= 2026-12-15) ; reste RF1-R4 : étendre le 400 aux 8 autres référentiels et à `categories-transport`.
- Doublons historiques de `lower(trim(nom))` par centre en production ? (KB-RF1-04.)

## Réalisation (2026-11-10, ADR 0122)
| Étape | Résultat |
|---|---|
| Filet legacy (avant tout changement de production) | 16 tests HTTP MockMvc/H2, 27 goldens approuvés un par un : `backend/platform/src/test/java/.../characterization/patientcouverture/rf1/` (saisie, lecture, import, lectures communes et caches) ; verts sur le legacy |
| Tests du nouveau code d'abord | rouge vérifié (compilation : types absents) puis vert : `TransporteurTest` (10), `GererLesTransporteursTest` (11), contrat double/H2 (10 + 11), PG16 (11), `ReferentialAdminDelegationTest` (5), `TableDesTransporteursTest` (5) |
| Code | PC : domaine `Transporteur`, application `GererLesTransporteurs`/`ConsulterLesTransports`, langage publié `Transporteurs`/`CategoriesDeTransport`/`GestionDesTransporteurs`, `TransporteursJdbc`/`CategoriesDeTransportJdbc`, `TransportsControleur`, cache et invalidation ; plateforme : délégué + ACL ; Organisation : lectures retirées |
| Parité | 27/27 goldens RF1 inchangés ; goldens Organisation et gardes d'architecture verts ; aucun `_nouveau_code` |
| Rejeu PostgreSQL 16 | `backend/scripts/patient-couverture-rf1-pg16-rejeu.sh` : vert (colonnes, 4 requêtes, contrat 11 tests) |

## RF1c (2026-11-10, ADR 0123) : corrections KB-RF1-01 et KB-RF1-03
| Étape | Résultat |
|---|---|
| Tests d'abord, ROUGE vérifié | contrat du carnet (double + H2) : 4 échecs (4 attendu / 5 obtenu, 0 / 1) ; filet RF1 : 6 échecs sur 16 tests (500 au lieu de 400, 422 au lieu de 204) |
| Code | `DatesIllisiblesDeLaDirectionAdvice` (route des transporteurs de l'administration) ; `TransportsControleur` (`centerId` absent) ; `CarnetDeTransporteurs.nombreDeReferences(centre, id)` + `TransporteursJdbc` |
| Goldens | 7 re-approuvés à la main en `*_nouveau_code.approved.json` (saisie : creations, creations-gardees [+ scénario « secretaire, corps illisible »], modifications, suppressions, etat-apres-suppressions ; lecture/liste ; lectures-communes/transporteurs) ; anciens dans `_ecarts-legacy/**/*_avant_rf1c.legacy.json` ; 20 autres inchangés |
| Contrat | double mémoire + H2 + PostgreSQL 16 (12 tests, `patient-couverture-rf1-pg16-rejeu.sh` port 55791, vert) |
| Résiduel | RF1-R4 : 400 non étendu aux 8 autres référentiels ni à `categories-transport` (500 conservé) |

Résiduels nominatifs : RF1-R1 (descripteur `ReferentialKind.TRANSPORTEUR`), RF1-R2 (chemin générique des autres référentiels) : voir l'ADR 0122. Condition 2 ci-dessus adaptée : `ReferentialKind.CATEGORIE_TRANSPORT` n'a jamais existé ; `TRANSPORTEUR` reste comme descripteur (lecture de fichier et modèles communs). Façade, flag, plan de bascule : sans objet (refactoring sur place).
