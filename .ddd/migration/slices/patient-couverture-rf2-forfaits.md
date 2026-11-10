---
slice: patient-couverture-rf2-forfaits
status: planned
date: 2026-11-10
order: 7
context: patient-couverture
sources: [ADR 0121, ADR 0024, ADR 0079, ADR 0097, .ddd/migration/reviews/analyse-referentiels-orphelins.md]
---

# RF2 - Catalogue de forfaits chez Patient & couverture

> Propriété décidée par l'ADR 0121 (décision 1 : le tarif relève de l'accord de prise en charge), **à confirmer métiers <= 2026-12-15**. Refactoring SUR PLACE : table `forfait`, URLs et comportement inchangés. À faire APRÈS RF1.

## Comportement observé à caractériser
| # | Scénario | Source |
|---|---|---|
| 1 | CRUD admin `/api/v1/admin/referentials/forfaits` : rôles, garde de centre, clé naturelle `code` unique par centre | `ReferentialKind.java:27`, `ReferentialAdminTables.java:19-26` |
| 2 | `prix` DECIMAL(10,2) NULLABLE : création et modification avec prix nul, négatif, à 3 décimales | schema.sql:115-121 |
| 3 | Suppression refusée si utilisé : PEC (`forfait_demande_id`, `forfait_effectif_id`), `facture_lignes.forfait_id`, séance (forfait imposé, `UsagePublie` SE7e, ligne 88) : messages | `ReferentialAdminTables.java:24-25` |
| 4 | Import CSV/Excel + gabarit : vérification, import, rejeu, refus | admin générique |
| 5 | Lecture `GET /api/v1/referentials/forfaits` (garde de centre 403, forme `RefItem`) | `ReferentielsLegacyControleur.java:85` |
| 6 | Lectures des consommateurs : Séance `ForfaitsDuCentreJdbc.java:27` (ACL `legacyreferentiel`) et `ForfaitsDeSeanceJdbc.java:27` ; Facturation `ForfaitsDuCentreJdbc.java:25` (ACL `legacyseance`) ; PC `CouvertureDesPatientsJdbc.java:23,90` (jointure du prix de PEC) | idem |
| 7 | Forfait imposé à une séance = instantané non rafraîchi (modifier le catalogue ne change pas l'historique) | `seance.md:42` |
| 8 | Forfait sans prix : « Forfait non defini » en simulation, 422 à la validation (KB-FAC-32, KB-SEA-45/47) | `facturation.md:40` |
| 9 | Invalidation des caches `ref.*` | `CacheConfig.java:48-57` |

## Goldens à prévoir
Environ 25 : CRUD (créer, doublon, prix nul, modifier, supprimer libre / utilisé par PEC demandée / PEC effective / ligne de facture / séance), import (4), lectures (4), parité des deux ACL dupliquées (mêmes sorties avant et après remplacement), cache (2). Goldens des filets Séance, Facturation, Absences et PC5 inchangés (preuve de parité).

## Langage publié à créer (`patientcouverture.v1`)
UN SEUL `ForfaitsDuCentre` (`lister(centre)`, `parCode(centre, code)`, `prix(centre, id)`, `existe(centre, id)`), remplaçant les DEUX ACL JDBC dupliquées de Séance (`ForfaitsDuCentreJdbc`, `ForfaitsDeSeanceJdbc`) et de Facturation (`ForfaitsDuCentreJdbc`). Séance et Facturation gardent leur port requis (`ForfaitsDuCentre` côté Séance) avec un traducteur mince (comme `ForfaitsDesPatientsDeLaCouverture`, PC5 S2). Usage à la suppression : `UsageDuForfait` publié par chaque propriétaire de référence (PC : PEC ; Facturation : lignes de facture ; Séance : forfait imposé, déjà `UsagePublie`), agrégé par PC, messages identiques.

## Garde de table nominative
`TableDesForfaitsTest` : table `forfait` ; scanne tout le code de production hors PC ; liste blanche nominative et DATÉE, effectif asserté, échec sur entrée morte ; cible 0 lecteur sauf jalons datés (jointures `GROUP BY` Facturation sur `facture_lignes`, lecture Organisation retirée).

## Conditions de retrait du CRUD générique pour ce type
1. Filet RF2 vert (CRUD et import à parité), les trois lecteurs Séance/Facturation migrés sur `ForfaitsDuCentre` sans modifier un seul golden existant.
2. `ReferentialKind` sans `FORFAIT` ; `ReferentialAdminTables` sans l'entrée forfait ; les `COUNT` d'usage remplacés par `UsageDuForfait`.
3. Les deux ACL JDBC dupliquées supprimées ; lecture `/referentials/forfaits` servie par PC ; code Organisation retiré.
4. Garde RF2 verte, build complet vert, `check-arch` 0 violation, PG16 rejoué.
Retour arrière : revert Git.

## Effort
2 sessions : (a) CRUD + import + usages publiés ; (b) langage publié unique + remplacement des deux ACL + retrait Organisation. Risque moyen (argent reproduit, trois lecteurs).

## Questions ouvertes
1. **`forfait.prix` nul** : sens métier d'un forfait sans prix (gratuit, non défini, à négocier) ? Aujourd'hui toléré (KB-SEA-45/47), refusé à l'émission. Interdire à la saisie ?
2. **« Forfait » a trois sens** : de PEC (PC), appliqué (Facturation), du centre (Séance) ; terme « Catalogue de forfaits » proposé pour le catalogue ; à valider.
3. Pas de validité temporelle du prix : hypothèse à vérifier (un changement de prix s'applique-t-il aux PEC en cours ?).
4. Sens commercial (« tarif », synonyme interdit en PC) dans un contexte clinique : à trancher avec les experts.
