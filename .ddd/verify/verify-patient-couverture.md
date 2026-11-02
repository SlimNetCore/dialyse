---
status: proposed
date: 2026-11-02
scope: contexte patient-couverture (PC1–PC5), vérification globale
sources: [commit 2978e639, .ddd/state.json, .ddd/migration/roadmap.md:295-304, .ddd/migration/slices/patient-couverture-pc*.md, ADR 0064, backend/contexts/patient-couverture, backend/platform/pom.xml]
reviewer: relecteur indépendant (lecture seule, aucun build ni test lancé)
---

# Vérification — Patient & couverture (PC1–PC5)

## Contrôles automatiques
| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | 0 violation (0 baseline, 0 nouvelle) — **mais non significatif** : le module n'est consommé par personne |
| `ArchitectureHexagonaleTest` (module) | présent ; non exécuté ici |
| `FrontieresDesContextesTest` règle PC (`FrontieresDesContextesTest.java:176-182`) | **vide** : tourne dans `platform`, qui n'a pas `contexts-patient-couverture` sur son classpath ; `allowEmptyShould(true)` ⇒ passe toujours |
| `TableDuPatientCouvertureTest` | passe par construction : exclut `/persistence/`, `/acl/`, `/entity/`, `/migration/` (`TableDuPatientCouvertureTest.java:42-45`) et **ne scanne pas `platform/src/main`** (`:72-83`) |

## Constat central (vérité)
Le module `backend/contexts/patient-couverture` est **compilé mais non câblé** : il figure dans `backend/pom.xml:43` mais **pas** dans les dépendances de `backend/platform/pom.xml` (17-78 : `bc-patient`, `bc-assure`, `bc-insurance`, `bc-pec` y sont toujours). Aucune classe de production hors du module ne l'importe. Le legacy (`PatientRestController`, `bc-*`, `MouvementPatientJdbcAdapter`, `PlacementDeLaFiche`) sert toujours 100 % du trafic. Les tests de caractérisation `characterization/patientcouverture/*` ciblent les URLs legacy (`/api/v1/patients`, `/api/v1/pec`) et **n'exécutent jamais le nouveau code**. Il n'existe aucun golden `_nouveau_code` ni archive `_ecarts-legacy` pour ce contexte (17 goldens, tous legacy) ⇒ **aucune parité démontrée**.

## Constats
| # | Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|---|
| 1 | blocker | legacy | Slices déclarées `built` alors que le code n'est ni câblé ni testé en parité ; legacy entièrement actif | `platform/pom.xml:17-78` ; `state.json` (PC1–PC5 `built`) | Remettre l'état réel (§ État) ; ne rien déclarer `built` avant câblage + parité verte |
| 2 | blocker | legacy | `PatientsJdbc` lit/écrit `patients.date_sortie` : la colonne **n'existe pas** dans `patients` (seule `date_evenement_etat`) ⇒ toute requête échouerait | `PatientsJdbc.java:37,44,50,61` ; `V1__baseline_schema.sql:1032-1095` | Mapper sur `date_evenement_etat` |
| 3 | blocker | tactique/legacy | `EtatPatient` = `ACTIF, SORTI, DECEDE, GREFFE, GUERI` ; legacy = `PERMANENT, OCCASIONNEL, VACANCIER_*, TRANSFERE, DECEDE, GREFFE, GUERRI` (+`type_patient`). `EtatPatient.valueOf` planterait sur `GUERRI`/`TRANSFERE`, et écrirait `SORTI`/`GUERI`, inconnus des lecteurs Absences/Planification (`ETATS_DE_SORTIE`) | `EtatPatient.java` ; `PatientsJdbc.java:29` ; `bc-patient …Patient.java:37` ; glossaire `patient-couverture.md:13` | Reprendre le vocabulaire du glossaire/legacy ; caractériser avant |
| 4 | blocker | archi/sécurité | Contrôleurs sans garde de centre : `centreId` pris dans `@RequestParam`/body, aucun `UserPrincipal`/`@PreAuthorize` ⇒ s'ils étaient exposés : lecture d'identité patient inter-centres (IDOR) | `PatientsControleur.java:35-43` ; `PecControleur.java:30-43` | Centre issu du principal, comme le legacy (goldens `*_cross_centre_adminB`) |
| 5 | major | legacy | URLs nouvelles `/api/v1/patient-couverture/*` ≠ décision « mêmes URLs » | `PatientsControleur.java:18`, `PecControleur.java:19`, `AssuresControleur.java:18`, `AttestationsControleur.java:17` ; ADR 0064 § Décision 1 | Reprendre les URLs legacy à la bascule |
| 6 | major | tactique | `MouvementsJdbc` est un no-op : les mouvements (admission, sortie, décès…) sont **perdus** alors que la table `mouvement_patient` existe et est lue par Planification/Absences | `MouvementsJdbc.java:11-18` | Adapter sur `mouvement_patient` |
| 7 | major | tactique | `Patient.transferer()` ne change aucun état (seulement 2 gardes) ; le service enregistre pourtant un mouvement TRANSFERT | `Patient.java:68-71` ; `PatientService.java:40-48` | Modéliser le transfert (cf. module Transfert/Archives existant) |
| 8 | major | tactique | Sortie/décès/greffe/guérison : aucune garde d'état (décès après décès, greffe d'un sorti) ; aucun appel à la Planification (`verifierPlacement`/`libererPlacement`, `PLACE_LIBEREE`) | `Patient.java:80-94` ; grep sans résultat dans le module | Port vers Planification ; invariants + tests |
| 9 | major | tactique | Règle R1 (un seul assuré principal) dans le service applicatif, pas dans un agrégat | `AssureService.java:48-52` | Agrégat porteur ou politique de domaine |
| 10 | major | tactique | Écritures multi-tables sans transaction (`patients` + mouvements ; `assure` + `assure_patient`) ; aucune version optimiste | `PatientService.java:33-35` ; `AssureService.java:46-52` ; aucun `@Transactional` dans le module | Transaction au cas d'usage ; verrou optimiste |
| 11 | major | tactique | Horloge non injectée : `OffsetDateTime.now()`/`LocalDate.now()` en application et adapters (le `maintenant` reçu est ignoré) ; date de transfert en **UTC** | `PecService.java:131`(l.32 du fichier) ; `AssureService.java:49,51` ; `AttestationService.java:32` ; `AssurePatientsJdbc.java:57` ; `PatientService.java:46` | Port `Horloge` (prévu ADR 0064 § 4) ; fuseau du centre |
| 12 | major | stratégique | Langage publié non publié : les 5 records d'événements ne sont jamais émis ; aucun port de lecture publié (PC5 « lectures publiées » inexistant) ; `PublicateurDEvenements`, `Horloge`, `GenerateurIdentifiants`, caches absents vs ADR 0064 | `publishedlanguage/*.java` ; grep sans usage | Implémenter ou réduire l'ADR |
| 13 | major | stratégique | 25 fichiers de production hors contexte lisent/écrivent les tables patient/PEC/assuré (dont `seance/infrastructure/persistence/PecValidationJdbc.java:21`, `facturation/…/SituationsDesFacturesJdbc`, `platform/…/DirectionDashboardQueryService`, `PatientSummaryQueryService`) ; la garde est aveugle à tous | grep `FROM|JOIN|INTO|UPDATE (patients|prise_en_charge|…)` ; `TableDuPatientCouvertureTest.java:42-45,72-83` | Durcir la garde (ne pas exclure `/persistence/`, inclure `platform`) + liste blanche datée |
| 14 | major | archi | Contrôleurs exposent les entités de domaine (`Patient`, `Pec`) en JSON | `PatientsControleur.java:35,40` ; `PecControleur.java:36` | DTO de réponse |
| 15 | major | tests | Aucun filet PC1 ni PC4 propre au contexte (les tests cités dans les fiches sont les tests legacy) ; pas de caractérisation des attestations ni des mouvements dans `characterization/patientcouverture` | `.ddd/safety-net/patient-couverture-pc1…md:19-32`, `…pc4…md:19-20` ; dossier test ne contient que Assure + Pec | Caractériser PC1/PC4 avant toute bascule |
| 16 | minor | legacy | Suppression PEC : legacy rejette un id inconnu (`PEC introuvable`), nouveau code supprime en silence | `PecDomainService.java:66-68` vs `PecsJdbc.java:75-77` | Parité |
| 17 | minor | tactique | Primitive obsession : `numeroAssurance` `String`, forfaits `UUID` bruts, exceptions de domaine en `IllegalArgumentException("…")` | `Patient.java:11` ; `Pec.java:111,115` | VO `NumeroAssurance`, `ForfaitId` ; exceptions nommées |
| 18 | minor | stratégique | Écarts glossaire ↔ code : `PriseEnCharge`→`Pec`, `AttestationDeDroits`→`AttestationDroit`, `ForfaitPec` absent, `PatientSorti` absent ; 9 termes encore `proposed` sous un en-tête `validated` | `glossaries/patient-couverture.md:2,11-19` | Aligner ; faire valider les `proposed` |
| 19 | minor | tests | Tests unitaires avec `LocalDate.now()`/`OffsetDateTime.now()` (non bloquants, valeurs non assertées) ; alias `<J>` remplace toute occurrence de la date du jour dans le golden assuré | `AssureTest.java:36,38` ; `PecTest.java:18…54` ; `AssurePrincipalCharacterizationIntegrationTest.java:111-112` | Dates fixes ; alias ciblé sur le champ |
| 20 | minor | tactique | `R5` (attestation liée à une PEC active, ADR 0064) non implémentée | grep `pec` vide dans `AttestationDroit.java`/`AttestationService.java` | Implémenter ou retirer de l'ADR |
| 21 | nit | legacy | ADR 0067 « Strangler Fig » contredit la décision utilisateur « refactoring sur place, pas de Strangler » | `adr/0067…md:7` | Renommer/clarifier |

Suggestion (non vérifiée) : à l'ajout de la dépendance dans `platform`, risque de collision de noms de beans (`patientRepository`, …) avec les adaptateurs legacy.

## État réel proposé (sans modifier l'état)
| Slice | `state.json` | roadmap | fiche | Vérité | État proposé |
|---|---|---|---|---|---|
| PC1 | built | planned | characterized | squelette non câblé, adaptateur faux (#2, #3, #6), pas de filet dédié | **planned** (filet à faire) |
| PC2 | built | planned | built | filet legacy OK (7 goldens) ; code non câblé, R1 hors domaine | **characterized** |
| PC3 | built | planned | characterized | filet legacy OK (10 goldens) ; code non câblé | **characterized** |
| PC4 | built | planned | characterized | filet = 2 tests unitaires legacy, aucune caractérisation HTTP | **planned** |
| PC5 | built | planned | built | aucun port publié, aucun legacy retiré, garde vacuitaire | **planned** |
Faux : les 5 entrées `state.json`, les fiches PC2/PC5 (`built`), `verifications-production.md` (aucune section PC), le prérequis « bloquer la sortie sans date » (roadmap l.395) **non réalisé**, RG-PAT (`04-patients-assures-pec.md:79` « à venir ») cohérent avec la réalité mais pas avec `built`.

## Contrôles de production (ordre, à faire avant toute bascule)
1. Lister les sortis sans `date_evenement_etat` (`verifications-production.md` § 25.2) et les saisir.
2. Recenser les valeurs distinctes de `patients.etat_patient` et `type_patient` (pour #3).
3. Vérifier qu'aucun `assure_patient` n'a deux principaux actifs (R1) avant de l'imposer.
4. Recenser les PEC `VALIDEE` à périodes incohérentes (début > fin) que le nouveau constructeur rejetterait.
5. Vérifier les `assure_*` dupliqués sur `patients` vs table `assure` (double source de l'assuré).
6. Après câblage : relancer les goldens legacy sur le nouveau code (parité) et contrôler la garde de centre (`*_cross_centre_adminB`).

## Validations restantes pour l'utilisateur
- Statut réel des slices (tableau ci-dessus) et correction de `state.json`/fiches.
- Règle « bloquer la sortie sans date » : quand et dans quelle slice (PC1) ?
- Vocabulaire des états patient (legacy vs code) et 9 termes `proposed` du glossaire.
- Portée du langage publié (ADR 0064 § 3-5 : événements, caches, `PECRepository` publié pour Seance) : à implémenter ou à réduire.
- Liste blanche des 25 lectures SQL hors contexte et date de retrait.

## Verdict : **fail**
Top 3 actions :
1. Rétablir l'état réel (PC1/PC4/PC5 `planned`, PC2/PC3 `characterized`) ; le code actuel est un squelette non câblé.
2. Corriger le mapping `patients` (#2, #3, #6) et la garde de centre (#4) avant tout câblage, puis parité sur les goldens existants aux **mêmes URLs**.
3. Durcir `TableDuPatientCouvertureTest` et `FrontieresDesContextesTest` pour qu'ils échouent réellement (#13, vacuité).
