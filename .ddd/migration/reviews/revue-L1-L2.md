---
status: proposed
date: 2026-11-10
sources: git show e049b4bd (L1, ADR 0112), git show 8dbc909d (L2, ADR 0113), .ddd/migration/corrections-kb-2026-11-09.md
---
# Revue indépendante des lots L1 (confidentialité) et L2 (clinique)

Lecture seule, au diff. Aucun test, build ni scan lancé : les points « rouge puis vert » sont déduits du diff (assertions inversées, goldens archivés), pas exécutés.

**Verdict : pass-with-actions.** Aucun blocker. Les 3 KB annoncées (KB-DIR-01, KB-MED-104, KB-MED-106) sont réellement corrigées dans le code.

## Vérifications

| # | Point | Résultat |
|---|---|---|
| 1 | KB-DIR-01 vue d'ensemble | OK : `BilanDuCentre.java:48-56,61-67` (drapeaux `...ComposesDeMasques` calculés avant le masque, `patientsPublies()` nul). Publié via `TableauDeBordPublie.java:58`. |
| 1 | KB-DIR-01 totaux par caisse | OK : `RepartitionDeLaSociete.java:90-99,168-178` (`composeDeMasques` par caisse) ; publié `TableauDeBordPublie.java:77`. |
| 1 | KB-MED-104 consolidés | OK : `CalculerLesIndicateursCliniques.java:52-82` (7 effectifs via `EffectifMasque.consolider`, patients évalués et taux des 5 marqueurs supprimés ensemble) ; `EffectifMasque.java:37-45`. |
| 1 | PDF et instantanés figés | OK : `ConstituerLeRapportDeSynthese.java:78-100,145-165,211-217` ; `MasqueDesTotauxFiges.java` appliqué à la lecture, `InstantanesMensuelsPublies.java:56-62` ; le test pd2 vérifie que `payload` n'est pas réécrit. |
| 1 | KB-MED-106 | OK : SQL `PatientsARisqueInfectieuxJdbc.java:25` (`n.resultat IN ('POSITIF','NEGATIF')`), Java `RisqueInfectieux.java:37-46`. SQL et Java sont alignés ; marqueurs indépendants. |
| 2 | Tests | L1 : tests unitaires ajoutés (`EffectifMasqueTest`, `RepartitionDeLaSocieteTest`, `LaPeriodeEtLeSeuilDAnonymatTest`, `MasqueDesTotauxFigesTest`, `ConstituerLeRapportDeSyntheseTest`, `CalculerLesIndicateursCliniquesTest`) et caractérisations renommées `_nouveau_code`. L2 : `RisqueInfectieuxTest` (3 tests), contrat `RisquesInfectieuxContrat` (cas P9-P12, mémoire/H2/PG16). La phase rouge n'est pas prouvée par relecture ; les anciens goldens archivés montrent l'état « avant ». |
| 3 | Goldens | OK : 7 (L1) + 1 (L2) renommés en `_ecarts-legacy/..._avant_l1|l2.legacy.json` (rename 100 %) et nouveaux `..._nouveau_code.approved.json`. Le golden dm4 `derniere_serologie` du catalogue n'est pas concerné (résultats isolés, aucun POSITIF suivi d'un DOUTEUX). |
| 4 | Frontend | OK : `headcount(null)` utilisé pour KPI (`direction-dashboard.component.html:130`), comparateur (`:1033`), tableaux et caisses (`.ts:345-362`, `:463`). |
| 5 | Clôtures | Honnêtes : la divergence `porteursPositifs` et les résiduels DPO sont écrits dans l'ADR et `known-bugs.md`. |

## Constats

| # | Sévérité | Constat | Preuve | Correction proposée |
|---|---|---|---|---|
| 1 | major | Fuite par recoupement de même famille laissée ouverte (documentée) : l'effectif publié d'un centre moins sa ventilation sexe/âge/caisse publiée révèle une cellule masquée (ex. centre 7 = CNAS 5 + CASNOS 2 masqué). L'intitulé « KB-DIR-01 corrigé » et l'idée que l'effectif n'est « plus déductible » doivent être bornés au total. | `RG-DIR-111` (13-direction.md, « Résiduels ») ; `RepartitionDeLaSociete.java:90-99` (la règle ne couvre que les totaux) | Libeller « corrigé (totaux), résiduel ouvert » dans `known-bugs.md` et ADR 0112 ; ouvrir une KB dédiée avec échéance DPO 2026-12-15 ; ne pas lever la condition de mise en production sans réponse. |
| 2 | minor | Divergence assumée mais réelle : un patient peut être isolé (RG-PLN-012) sans être compté « porteur positif » dans la Direction. `porteursPositifs` garde `MAX(date)` toutes valeurs, sans départage par saisie ni filtre conclusif. | `IndicateursCliniquesJdbc.java:67-71` vs `PatientsARisqueInfectieuxJdbc.java:20-27` ; ADR 0113 question ouverte 3 | Trancher avec les médecins (même échéance) ; si alignement, réutiliser la règle `RisqueInfectieux` et ajouter un contrat commun. |
| 3 | minor | La règle de suppression complémentaire est implémentée à trois endroits (drapeaux du domaine, relecture JSON, re-application dans le PDF, même pour des données vives déjà conformes) : risque de dérive. | `BilanDuCentre.java:48-56` ; `MasqueDesTotauxFiges.java:34-96` ; `ConstituerLeRapportDeSynthese.java:140-149,174-189` | Garder un seul point de vérité (`SeuilDAnonymat.totalPublieDepuis`) et un test de parité entre calcul vif et relecture figée sur un même jeu. |
| 4 | minor | Total du PDF par caisse : clé de comparaison asymétrique. Les cellules passent par `.replace(ABSENT,"")` alors que le total garde `ABSENT` (« — ») si `caisseCode` est null ; un total d'ancien instantané à code null n'est jamais supprimé. Sans effet pour le vif (code vide, pas null). | `ConstituerLeRapportDeSynthese.java:211-217` (ABSENT défini `:57`) | Normaliser les deux côtés de la même façon (`code == null ? "" : code`) et ajouter un test à code null. |
| 5 | minor | Hexagonal / style : la logique de consolidation avec masque (`consolider`, `effectif`) est dans la classe d'application et utilise des noms `java.util.*` entièrement qualifiés ; seule la primitive est dans le domaine. | `CalculerLesIndicateursCliniques.java:63-82` | Déplacer la consolidation par marqueur/effectif dans le domaine (`EffectifMasque` ou un objet `ConsolidesCliniques`) ; importer les types. Aucune violation de dépendance constatée (interfaces → domaine, application → domaine). |
| 6 | minor | Relecture masquée heuristique : un `null` au niveau d'un centre est lu comme « masqué », donc un champ absent ou illisible supprime aussi le total (prudent, mais invisible). Le payload figé ne peut plus servir de preuve du total brut. | `SeuilDAnonymat.java:354-357` ; `MasqueDesTotauxFiges.java:93-96` | Documenter ce comportement dans RG-DIR-111 ; sinon accepter (choix sûr). |
| 7 | minor | Détections de production : `§66.1` garde l'intitulé d'avant correction et diffère de `§75.1` (`<` strict sur la date du positif vs `<=`), donc deux listes proches non réconciliées. | `dossier-medical-dm9-pg16-rejeu.sh` (§66.1 / §75.1) ; `verifications-production.md` | Marquer §66.1 « historique, état avant L2 » ou le supprimer ; ne conserver que §75.1 comme liste « entrants en isolement » à valider par les médecins. |
| 8 | minor | Effet opérationnel L2 non chiffré : davantage de patients isolés, réaffectations automatiques au prochain placement, sans liste préalable ni garde-fou. | ADR 0113 (impact) ; catalogue point 5 | Exécuter §75.1 sur une copie de production et faire valider la liste par les médecins avant déploiement (déjà prévu avant 2026-12-15 : en faire une condition de mise en production). |

## Points sans constat
- Langage omniprésent : « suppression complémentaire », `composeDeMasques`, `dernier résultat conclusif` sont dans les glossaires `dossier-medical` et `pilotage-direction` (modifiés dans les commits) ; RG-DIR-111, RG-MED-049g/h et la traçabilité (`16-annexe-tracabilite.md`) sont mis à jour.
- Autres appelants : `IndicateursPublies`, `CliniquesParLeDossierMedical`, `IndicateursDeLaSociete` consomment des `Long` nullables ; `TableauDeBordPublie` publie les valeurs masquées ; aucun appelant lisant `totaux().patients` brut hors rapport n'a été trouvé.
- Séances, CA et finances inchangés comme annoncé.

## Top 3 actions
1. Borner l'annonce « corrigé » de KB-DIR-01 (résiduel par ventilation, constat 1) et en faire une condition de mise en production avec l'avis DPO.
2. Valider la liste §75.1 avec les médecins avant déploiement de L2 et trancher l'alignement de `porteursPositifs` (constats 2, 7, 8).
3. Réduire la duplication de la règle de masquage et corriger l'asymétrie `ABSENT` du PDF (constats 3, 4, 5).
