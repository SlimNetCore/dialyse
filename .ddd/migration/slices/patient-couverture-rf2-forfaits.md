---
slice: patient-couverture-rf2-forfaits
status: built
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
5. ~~Corriger KB-RF2-01 et KB-RF2-02 comme RF1c ?~~ Corrigés le 2026-11-10 (RF2c, ADR 0125, à confirmer métiers <= 2026-12-15) ; le prix nul / omis est GARDÉ tel quel (décision de l'utilisateur) ; RF1-R4 (étendre le 400 aux 7 autres référentiels) SOLDÉ le 2026-11-10 par RF3d (ADR 0129).
6. Doublons historiques de `lower(trim(code))` par centre en production ? (KB-RF2-03.)

## Réalisation (2026-11-10, ADR 0124)
| Étape | Résultat |
|---|---|
| Filet legacy (avant tout changement de production) | 16 tests HTTP MockMvc/H2, 28 goldens approuvés un par un (lecture du `.received`) : `backend/platform/src/test/java/.../characterization/patientcouverture/rf2/` (saisie 4, import 5, lecture et droits 4, lectures communes et caches 3) ; verts sur le legacy |
| Tests du nouveau code d'abord | rouge vérifié (compilation : types absents, ~190 symboles) puis vert : `ForfaitTest`, `GererLeCatalogueDeForfaitsTest`, contrat double mémoire + H2 (`ForfaitsContrat`), PG16 (12 tests), `ForfaitsParPatientCouvertureTest` (6), `ReferentialAdminDelegationTest` (+1), `TableDesForfaitsTest` (5) |
| Code | PC : domaine `Forfait`, application `GererLeCatalogueDeForfaits`/`ConsulterLesForfaits`, langage publié `ForfaitsDuCentre`/`GestionDuCatalogueDeForfaits`/`RefusDeForfait`/`UsageDuForfait`, `ForfaitsJdbc`/`UsageDuForfaitParLesPec`, `ForfaitsControleur`, cache et invalidation ; plateforme : ACL `ForfaitsParPatientCouverture`, assembleur `ReferentialAdminDelegates`, `HistoryRules` via le langage publié ; Séance et Facturation : traducteurs `ForfaitsDuCentreParLeCatalogue` + `UsageDuForfaitParLes...`, les deux `ForfaitsDuCentreJdbc` supprimés ; Organisation : lectures retirées |
| Parité | 16/16 tests, goldens RF2 inchangés sur le nouveau code ; goldens Séance, Facturation, Absences, pilotage, reporting inchangés ; aucun `_nouveau_code`, aucune archive `_ecarts-legacy` |
| Rejeu PostgreSQL 16 | `backend/scripts/patient-couverture-rf2-pg16-rejeu.sh` (port 55792) : vert (colonnes, type `numeric(10,2)`, 6 requêtes, contrat 12 tests) |
| Condition de retrait 1 à 4 | 1 oui ; 2 partiel (RF2-R1 : le descripteur `ReferentialKind.FORFAIT` reste, comme RF1-R1) ; 3 oui ; 4 voir l'ADR 0124 |

## RF2c (2026-11-10, ADR 0125) : corrections KB-RF2-01 et KB-RF2-02
| Étape | Résultat |
|---|---|
| Tests d'abord, ROUGE vérifié | contrat JDBC (H2) : 2 échecs (attendu 4 et 0 pour les PEC du centre, obtenu 5 et 1) ; filet RF2 : 6 échecs sur 16 tests (500 au lieu de 400, 422 au lieu de 204, scénario « secretaire, corps illisible » absent) |
| Code | `DatesIllisiblesDeLaDirectionAdvice` (route `admin/referentials/(transporteurs\|forfaits)`) ; `ForfaitsControleur` (`centerId` absent) ; port publié `UsageDuForfait.nombreDeReferences(centre, forfait)` : `UsageDuForfaitParLesPec` borne `prise_en_charge.center_id` ; Facturation et Séance acceptent le centre et l'ignorent (compte inchangé, KB-SEA-91) |
| Goldens | 7 re-approuvés à la main en `*_nouveau_code.approved.json` (saisie : creations, creations-gardees [+ « secretaire, corps illisible »], modifications, suppressions, etat-apres-suppressions ; lecture/liste ; lectures-communes/forfaits) ; anciens dans `_ecarts-legacy/**/*_avant_rf2c.legacy.json` ; 21 autres inchangés ; goldens Séance, Facturation, Absences, Pilotage, reporting inchangés |
| Contrat | double mémoire + H2 + PostgreSQL 16 (`patient-couverture-rf2-pg16-rejeu.sh`, port 55793) |
| Hors périmètre | prix nul / omis (KB-RF2-04) et KB-RF2-03, 05, 06 : reproduits ; RF1-R4 (400 étendu aux 7 autres référentiels) SOLDÉ par RF3d (ADR 0129) |

Résiduels nominatifs : RF2-R1 à RF2-R5 (voir l'ADR 0124), dont RF2-R2 (jointure `prise_en_charge` -> `forfait` de la Séance, gardée par `TableDesForfaitsTest`, effectif 1). Façade, flag, plan de bascule : sans objet (refactoring sur place).
