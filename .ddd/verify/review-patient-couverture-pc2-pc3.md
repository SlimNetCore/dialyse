---
status: proposed
date: 2026-11-04
sources: git 853fd67b..ec197d5a (164 fichiers), check-arch, state.ts, diff des 30 goldens, legacy supprimé (git show 853fd67b)
scope: patient-couverture-pc2-assure-principal, patient-couverture-pc3-pec
reviewer: relecteur indépendant (lecture seule, aucun build ni test lancé)
---

# Revue PC2 (assuré principal) + PC3 (prise en charge) — ec197d5a

## Contrôles automatiques
- `check-arch` : 0 violation (0 en baseline).
- `state.ts` : PC2 `built`, PC3 `built` (posés par l'agent d'implémentation).
- Goldens : entre 853fd67b et HEAD, **0 golden modifié** (`M`) ; 30 `R100` (archives) + 30 `A` (`_nouveau_code`). Aucun golden PC1/P5/P7/Absences/Facturation touché.

## (1) Parité
- **Les 30 archives** `pc2/_ecarts-legacy` (6), `pc3/_ecarts-legacy` (23 + `pec_premier_filet/list_pec_cross_centre_adminB`) sont **identiques octet pour octet** à `git show 853fd67b:<ancien chemin>` (contrôle `diff` un par un : 30/30).
- Les 30 `_nouveau_code` ne diffèrent des archives que par les écarts décidés : (a) 403 ADMIN sans centre / autre centre (8 matrices, `sans_garde_de_centre`, `list_pec_cross_centre_adminB`) ; (b) 422 `ASSURE_D_UN_AUTRE_CENTRE` (`reponse` 200→422, affectation vide, assuré du centre B intact) ; (c) R1 (pas de golden, test de contrat) ; (d) 400/415 formats, 404 `PEC_INTROUVABLE`, 409 `PEC_IDENTIFIANT_DEJA_UTILISE`, message « Statut de prise en charge inconnu » ; (f) audit `PEC_VALIDATION`/`PEC_CLOTURE` + `entity_id`, `patientNom` réel ; (g) ligne `assure_patient` dupliquée disparue ; (h) `PUT` vide → 400, assuré conservé.
- `patients_ecrits` : le second patient est désormais enregistré = conséquence directe de (d) « 409 sans annuler la fiche » (ADR 0077 §7, KB-PAT-35) ; **décidé mais marqué « interprétation à valider »**.
- `etat_final` : une ligne de moins et `forfait_demande` `HD3` : conséquence du changement de cible du scénario (la PEC créée par l'ADMIN B étant refusée, la validation sans centre vise `existante`, `AutorisationsEtCentres…IntegrationTest.java:107-109`). Cohérent mais **l'entrée du scénario a changé**, pas seulement le nom du golden.
- Fixtures modifiées côté nouveau code : `joursOuvertsDuCentre(CENTRE_A, …)` ajouté (PC2 fiche, PC3 cycle) pour que la ligne `planning_parametres` existe ; le chemin « centre sans ligne » n'est donc plus exercé pour les écritures d'assuré/PEC.

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | archi/sécurité | **Refus de capture (KB-PAT-31) non garanti en course** : vérification du centre de l'assuré puis `UPDATE assure … WHERE numero_assurance = ?` **sans filtre `center_id`**, sous le seul verrou du centre A. Si le centre B insère le même numéro entre la lecture et l'UPDATE (READ COMMITTED), A écrase l'identité de l'assuré de B et lui lie une affectation : la capture revient. Violation (règle décidée non tenue) | `PrincipalDeLaFiche.java:37-41`, `AssuresJdbc.java:118-126` | `UPDATE … WHERE numero_assurance = ? AND center_id = ?` ; si 0 ligne : `INSERT … ON CONFLICT (numero_assurance) DO NOTHING` puis relecture → 422 si centre différent. Test de contrat PG16 en course A/B |
| major | legacy/stratégique | **Écrivain hors module de `assure`/`assure_patient`** restant (import de migration, `bc-assure` JPA) : pas de verrou du centre → R1 non tenue pendant un import concurrent à la saisie ; non cité dans l'ADR 0077 (seulement dans le filet PC2 l. 51) | `AffectationMigrator.java:128-136`, `AssureMigrator.java:86-114`, `.ddd/safety-net/patient-couverture-pc2-assure-principal.md:51` | Consigner dans l'ADR 0077 (dette + règle « pas d'import pendant l'exploitation ») ou faire passer l'import par `VerrouDuCentre` |
| minor | legacy | Code PEC mort encore présent et **bean actif** : `PecRepositoryAdapter` (`@Component`, caches `patient.pec.*`), `PecJpaEntity/Repository`, `bc-pec` `PriseEnCharge`/`PecStatus`/`PecRepositoryPort` : référencés seulement entre eux. `seams.md` affirme qu'ils sont « lus par `SeanceBillingEligibilityPort`, `PecValidationJdbc`, lecteurs de forfait » : **faux** (ces lecteurs font du SQL brut) ; l'ADR dit l'inverse (« plus référencés que par eux-mêmes ») | `PecRepositoryAdapter.java:18`, `.ddd/safety-net/seams.md` (jalon PC3), ADR 0077:33 | Supprimer maintenant (PC4 n'en a pas besoin) ou corriger le jalon de `seams.md` |
| minor | tactique | `AssurePrincipal` n'est pas un agrégat (classe utilitaire statique) ; R1 vit dans l'orchestration + SQL (`fermer…`/`ajouter…`, `retirer…`/`lierSansDates`). L'ADR 0077 §1 parle d'« agrégat » | `AssurePrincipal.java:13-16`, `GererLesAssures.java:70-72` | Corriger le libellé ADR/glossaire (« règles de l'assuré principal ») ; à terme agrégat `AffectationsDuPatient` |
| minor | tactique | Règle métier dans le use case : attestation du non-vacancier pour `session-allowed` recodée en `if` (doublon de `PriseEnCharge.verifierAttestation`) ; contrôle fin ≥ début de l'affectation dans le use case | `GererLesPec.java:108`, `GererLesAssures.java:86-88` | Méthode de domaine (`PriseEnCharge.autoriseSeance(vacancier, attestationValide)`) |
| minor | archi | Premier verrou sur un centre sans ligne `planning_parametres` : la ligne est créée par l'écriture d'assuré/PEC ; course initiale → 409. Les filets pré-posent la ligne : chemin non couvert, et absent du §42 | `VerrouDesPlacesJdbc.java:51`, `AssurePrincipalDeLaFiche…IntegrationTest` (`joursOuvertsDuCentre`), `CycleDeLaPriseEnCharge…IntegrationTest` | Ajouter au §42 un SELECT des centres sans ligne ; KB à consigner |
| minor | legacy | Front non adapté aux nouveaux codes (`PEC_IDENTIFIANT_DEJA_UTILISE`, `ASSURE_D_UN_AUTRE_CENTRE`, `PEC_INTROUVABLE`) : 409 sur `POST /patients` alors que la fiche est **enregistrée** → message d'échec, risque de re-saisie (doublon de fiche). Cas rare (le front renvoie le `pecId` du patient) | aucun fichier `frontend/` dans le diff ; `patient-wizard.store.ts:223` | Gérer 409/422 dans le wizard (lire `id` du ProblemDetail) ou documenter |
| minor | tests | Golden trompeur : `"… 403 … 5 PEC"` et `"liste des PEC du patient A …" : "5"` = nombre de **champs du ProblemDetail**, pas de PEC (pas de fuite, mais lecture fausse) | `AutorisationsEtCentres…IntegrationTest.java:96-97`, `sans_garde_de_centre_nouveau_code.approved.json` | Mesurer `size()` seulement si 200 |
| minor | docs | En-têtes des fiches de slice restés `status: characterized` alors que le tableau et `state.json` disent `built` ; décompte « 24 + pec_premier_filet » (KB) vs 23 + 1 réels | `slices/patient-couverture-pc3-pec.md:3`, `…pc2-assure-principal.md:3`, `known-bugs.md:1389` | Aligner |
| minor | sécurité | `patientNom` réel (prénom + nom) diffusé sur le canal temps réel **du centre** (tous rôles connectés au centre) ; décidé (KB-PAT-27) mais minimisation des données de santé non discutée ; `Map.of` lèvera NPE après commit si `nom` nul | `GererLesPec.java:120-125`, `NotificationService.java:66-80` | Suggestion : confirmer avec le DPO/métier ; `Objects.toString(nom, "")` |
| nit | archi | Messages anglais conservés (`CenterId cannot be null`) dans des 400 nouveaux | `PecControleur.java:108,127,139,172` | Libellé français |
| nit | méthode | 27 `*.received.json` ignorés par git restent sur disque (3 en `pc2/`, anciens noms, 2026-11-03 17:28-17:30, identiques aux `_nouveau_code`) ; aucun `.bak` trouvé | `characterization/patientcouverture/pc2/**/*.received.json` | Supprimer les `.received.json` |

## Points vérifiés sans constat
- **Argent (2)** : `PriseEnCharge.valider/cloturer/autoriseSeance` = legacy (`bc-pec PriseEnCharge`, 853fd67b l. 62-75) ; création/`canCreateSession`/suppression = `PecDomainService` supprimé (sauf jour du centre, décidé) ; pas de contrôle de période/forfait/chevauchement, suppression d'une PEC validée : **reproduits** ; colonnes et valeurs de `statut` écrites identiques (`PrisesEnChargeJdbc.java:55-63`) ; `SeanceBillingEligibilityJdbcAdapter`, `PecValidationJdbc`, `ForfaitsDesPatientsJdbc`, `SeancesFacturablesJdbc` **non modifiés** (hors diff). Mapping `vacancier` = `type_patient = 'VACANCIER'`, colonne NOT NULL : équivalent à `!= NON_VACANCIER` pour les valeurs de l'enum.
- **Rôles (3)** : `@PreAuthorize("hasRole('ADMIN')")` sur `validate` seul, comme le legacy ; garde de centre (`session.centre`) avant toute lecture/écriture ; corps lu après autorisation ; routes `/patients/assures/**` : droits inchangés (diff `PatientsControleur`).
- **Concurrence (4)** : verrou du centre en tête de chaque écriture PEC/assuré (`GererLesPec.java:57,70,81,93`, `GererLesAssures.java:62,80,98`) ; `lock_timeout` posé à l'entrée de l'unité (`UniteDeTravailSpring.java:54`) ; tests de course réels R1 (`PatientsJdbcContrat.java:289`) et double validation (`:442`) exécutés par `patient-couverture-pc2-pc3-pg16-rejeu.sh` (non rejoués ici). Pas de deadlock attendu : les lecteurs voisins font des SELECT sans verrou.
- **Retrait (5)** : `covered.json` sans entrée morte ; `seamAllowed: []` ; `excludes` du pom réduits à Attestation/PC1-gelés ; lecteurs SQL hors module de `prise_en_charge` : `SeanceRestController:351,383`, `DashboardRestController:102-116`, `SeanceBillingEligibilityJdbcAdapter:22`, `PecValidationJdbc:21`, `ForfaitsDesPatientsJdbc`, `SeancesFacturablesJdbc` ; ACL d'attestation temporaire `AttestationsDuPatientJdbc` (retrait PC4).
- **Dates (7)** : tests unitaires/contrat à horloge fixe (`Doubles.AUJOURDHUI`, `Fiches.AUJOURDHUI`) ; caractérisations PEC sur patients VACANCIER (pas de dépendance au jour) ou alias relatifs.
- **Docs (8)** : ADR 0077 `proposed` ; KB-PAT-20..36 statués ; RG-ASS-009..012, RG-PEC-011..013 présents ; §42 = SELECT uniquement ; glossaire `proposed` ; documentation client régénérée.
- **Incidents (9)** : les 30 goldens approuvés correspondent un à un à des écarts décidés ; pas de trace d'approbation en lot hors liste.

## Verdict : **pass-with-actions**

État proposé : **PC2 `built` confirmé, PC3 `built` confirmé** (parité et retrait vérifiés sur pièces ; tests non rejoués par le relecteur). Pas de passage à `verified` tant que : ADR 0077 `proposed` (décisions « déléguées à l'assistant ») et KB-PAT-24/25/26/35 non validés par les métiers, et le major « capture en course » non corrigé.

### Top 3 actions
1. Fermer la course de capture inter-centres (`AssuresJdbc.declarer` filtré par centre + `ON CONFLICT`) avec test PG16 A/B.
2. Faire valider par l'utilisateur/les métiers l'ADR 0077 (§7 409 fiche enregistrée, §10 argent) puis passer l'ADR en `approved`.
3. Supprimer le code PEC mort (`PecRepositoryAdapter`, JPA, `bc-pec`) ou corriger le jalon de `seams.md` ; documenter l'import (`AffectationMigrator`) comme écrivain hors verrou.

### Contrôles de production (ordre)
1. §42-1 (R1 doublons) et §42-2 (assurés partagés entre centres) : doivent être à 0 / connus avant bascule.
2. §42-5 (assurés sans nom) : prévenir les utilisateurs (PUT exigera le nom).
3. §42-6 et §42-7 (PEC orphelines/inter-centres, statuts hors cycle).
4. **À ajouter** : centres sans ligne `planning_parametres` ; comptes ADMIN sans centre de rattachement qui utilisent `/api/v1/pec` (désormais 403) (SELECT seuls).
5. §42-8, -9, -10 (argent, informatif, pour la décision métier).
6. Après bascule : surveiller 403 `ACCESS_DENIED` sur `/api/v1/pec`, 409 `PATIENT_ECRITURE_CONCURRENTE`/`PEC_IDENTIFIANT_DEJA_UTILISE`, 422 `ASSURE_D_UN_AUTRE_CENTRE` ; vérifier l'audit `PEC_VALIDATION`/`PEC_CLOTURE`.
