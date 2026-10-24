---
status: proposed
date: 2026-10-24
scope: contexte absences-patients (slices A1, A2, A3, A4, A6 ; A5 soldé par A3/A4)
sources: [check-arch.ts, state.ts show, .ddd/tactical/absences-patients.md, .ddd/strategic/glossaries/absences-patients.md, ADR 0038-0042, .ddd/migration/slices/absences-patients-a*.md, .ddd/safety-net/{known-bugs.md,seams.md,covered.json}, .ddd/migration/{roadmap.md,verifications-production.md}, docs/reference/regles-de-gestion/06-absences-patients.md, contexts/absences-patients (domain/AbsencePatient, MoisAbsences, application/CloturerLeMoisDAbsences, GardeDesMoisDAbsences), platform/architecture/{TableDesAbsencesDePatientsTest,FrontieresDesContextesTest}]
---

# Vérification globale — Absences de patients (2026-10-24)

Lecture seule ; aucun build ni test Maven lancé (les résultats verts cités sont ceux des fiches de slice et des revues A1–A6).

## Contrôles automatiques

| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | **0 violation** (0 en baseline, 0 nouvelle) |
| Imports de `domain/` et `application/` | uniquement `java.util/time/math` et le contexte lui-même ; aucune horloge système dans le domaine (`Clock` injecté dans `application/HorlogeDesCentres.java:16`) ; aucun setter public |
| `TableDesAbsencesDePatientsTest` | garde : aucun source hors du contexte (platform, shared-kernel, `bc-*`, autres contextes) ne cite `absence_patient` ; grep confirmé : en `src/main`, seulement `db/schema.sql` et les migrations V13/V39/V43 |
| `FrontieresDesContextesTest:84-89` | le legacy n'utilise le contexte que par son langage publié (`AbsencesDuPilotage`, `SignalementDesSaisies`, `NotificationsDuCentre`) |
| `ArchitectureHexagonaleTest` du module | présent (`contexts/absences-patients/src/test/.../architecture/`) |
| Revues de slice | A1, A2, A3, A4, A6 : `pass-with-actions` ; la non-conformité majeure de la revue A6 (décision 3 périmée dans le modèle tactique) est corrigée (`tactical/absences-patients.md:316`) |

## Constats

| Sévérité | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy | Les 5 ADR du contexte sont encore `proposed`, alors que la feuille de route et la fiche A6 disent l'ADR 0042 « accepted le 2026-10-23 » (incohérence) | `adr/0038…:2`, `0039…:2`, `0040…:2`, `0041…:2`, `0042…:2` ; `roadmap.md:71` ; `slices/absences-patients-a6-cloture-mensuelle.md:104-107` | L'utilisateur valide (ou amende) chaque ADR, puis on passe les en-têtes à `accepted` |
| major | legacy | Le seam A4 est toujours ouvert : 3 fichiers legacy restent dans `seamAllowed` alors qu'A4 est `built` | `covered.json:279-282` ; `seams.md:11` | Fermer le seam : retirer les 3 entrées de `seamAllowed` (elles restent dans `covered`), ajouter une ligne de clôture dans `seams.md` |
| major | archi/legacy | Contrôles de production à faire avant la mise en production : § 24.1 est **bloquant** (V43 échoue au démarrage s'il y a un doublon) ; le résultat de § 27.1 est « à rapporter » | `verifications-production.md:793-799, 823, 951` | Exécuter les contrôles dans l'ordre ci-dessous |
| minor | stratégique | Glossaire : 21 termes encore `proposed` ; l'en-tête dit « onze », la feuille de route « 4 » | `glossaries/absences-patients.md:2, 30-63` ; `roadmap.md:71` | Faire valider les 21 termes (liste ci-dessous) et corriger l'en-tête |
| minor | stratégique | Le modèle tactique est `approved`, mais garde la bannière « Proposition à valider au gate tactique » | `tactical/absences-patients.md:2, 10` | Retirer la bannière |
| minor | tactique | L'invariant « seul un mois terminé se clôture » (RG-ABS-070) est dans le cas d'usage, pas dans l'agrégat `MoisAbsences` | `application/CloturerLeMoisDAbsences.java:44-47` | `MoisAbsences.cloturer(aujourdhui, …)` lève `MoisDAbsencesNonTermine` |
| minor | tactique | La règle « mois clos » est vérifiée à 3 endroits par le port `MoisDAbsencesOuverts` ; `MoisAbsences.exigerOuvert()` n'est appelée que par les tests (code mort en production) | `domain/DateOuverte.java:35`, `application/GardeDesMoisDAbsences.java:26`, `application/ReconciliationAbsences.java:80`, `domain/MoisAbsences.java:116` | Suggestion : un seul chemin (supprimer `exigerOuvert` ou faire passer la garde par elle) |
| minor | tactique | Une écriture d'absence verrouille la ligne `mois_absences` et peut la créer (`OUVERT`) dans la même transaction : 2 agrégats par transaction. C'est un choix documenté, calqué sur F7 | `MoisAbsencesJdbc.java:33-35` ; `verifications-production.md:940-941` | Accepté par l'ADR 0042 ; le rappeler dans les « Conséquences » de l'ADR |
| minor | legacy | Dette ouverte de known-bugs : KB-ABS-05 (autres routes `SeanceRestController` sans garde de centre), KB-ABS-15 (`patientNom` nul dans les réponses d'écriture), KB-ABS-24 (sortie sans date : partiellement corrigé), KB-ABS-31 (taux exonéré à trancher avec la Facturation), KB-ABS-06/09 (reproduits) ; la section « Questions ouvertes » de A1 n'a pas été mise à jour après A4 (KB-ABS-06, décision 8) | `known-bugs.md:773-777, 798, 810, 817` | Trancher ou reporter explicitement chaque point (Patient et couverture, Séance) ; mettre à jour la section |
| nit | tactique | `annuler(…, boolean issueDUneExclusion, …)` : paramètre drapeau ; `String` pour le commentaire et le motif de réouverture (le VO `Commentaire` existe) | `domain/AbsencePatient.java:230`, `domain/MoisAbsences.java:28,93` | Suggestion : `OrigineDeLAbsence` ou un VO de motif |
| nit | legacy | Rapports surefire périmés de l'ancien domaine (non suivis par git) ; `.idea` est ignoré et pointe vers le nouveau module | `backend/bc-seance/target/surefire-reports/TEST-…AbsencePatientTest.xml` ; `.gitignore:2` | `mvn clean` à l'occasion |

Aucune classe morte d'absence en `src/main` : `bc-seance/.../domain/absence`, `platform/.../application/absence` et `persistence/adapter/absence` sont supprimés. `AbsencePatientIntegrationTest` (platform) est encore utile : il teste le pilotage de la Direction et de la Séance au travers du port publié.

## Qualité du modèle (synthèse)

- `AbsencePatient` : un modèle riche. Fabriques par intention (`declarer`, `detecter`, `reconstituer`), méthodes métier (`qualifier`, `rattraper`, `annuler`, `reconcilier`, `exigerRetirableParLaReinclusion`), exceptions de domaine nommées, événements au passé collectés (`tirerLesEvenements`), références par identifiant typé, preuve `DateOuverte` (période non facturée et mois ouvert). Pas d'anémie.
- `MoisAbsences` : petit agrégat. `cloturer` et `rouvrir` (motif obligatoire) renvoient l'entrée du journal ajouté en fin. Il manque l'invariant « mois terminé » (voir plus haut).
- Ports : `AbsencesRepository` et `MoisAbsencesRepository` (un par racine, dans le domaine) ; ports applicatifs nommés métier (`PatientsAttendus`, `ForfaitsDesPatients`, `FuseauxDesCentres`…) ; ACL rangées dans `infrastructure/acl/legacy*` ; langage publié dans `publishedlanguage/`. Les dépendances vont vers l'intérieur.

## Filets de caractérisation A1–A6 : proposition

24 classes `*Test` sous `platform/src/test/java/.../characterization/absencespatients` ; 271 goldens (86 `*_nouveau_code`, 18 `_ecarts-legacy` non rejoués).
**Proposition : les garder comme filet de non-régression HTTP** (mêmes URLs, refonte sur place), en trois temps :
1. Les renommer ou les étiqueter « regression ».
2. Supprimer les goldens legacy remplacés par un `*_nouveau_code` (l'historique git et `_ecarts-legacy` suffisent).
3. Garder `_ecarts-legacy` comme archive jusqu'à la fin de la période d'observation, puis le supprimer.
Ne pas supprimer avant la mise en production : ils servent de recette.

## Contrôles à exécuter AVANT la mise en production (ordre)

Tout se fait en SELECT seul, sur une copie ou en lecture seule.
0. Prérequis : V39 (F2) et V42 (F7) déjà en production, et § 18 et § 23 traités. Les requêtes § 24.4 et § 27.3 lisent `seances.exclusion_absence_id` (V39).
1. **§ 24.1 BLOQUANT** : doit rendre 0 ligne. Sinon : ne pas déployer, un expert tranche chaque doublon.
2. § 24.2 (attendu 0 ligne), § 24.3 (informatif), **§ 24.4** : liste des absences d'exclusion déjà annulées, à traiter à la main avec validation écrite.
3. § 25.1 (index V31), **§ 25.2** (sorties sans date : saisir les dates), § 25.3 à 25.7 (informatifs), § 25.8 (fuseau du serveur de base) ; vérifier `app.absences.fuseau-par-defaut`.
4. § 26 / 26.1 : absences futures, attendu aucune ligne.
5. Annoncer aux utilisateurs les nouveaux comportements (§ 24 et § 25, puces « Comportements nouveaux » ; 400 de § 26.2 ; rattrapage réservé à ADMIN/MEDECIN).
6. Déployer : Flyway applique V43 puis V44 au démarrage.
7. Après le déploiement : § 24.5 (V43 `success` et index `uq_absence_patient_rattrapage`), § 27.2 (tables V44 vides), première lecture Direction (affichage « < 5 » d'un petit centre, fiche A1).
8. Avant la **première clôture** d'un mois : § 27.1 (rapporter le résultat), § 27.3, § 27.4 (clore les absences, puis la facturation).

## Validations restantes (utilisateur)

- ADR 0038, 0039, 0040, 0041, 0042 : passer à `accepted`.
- 21 termes du glossaire `proposed` : Absence suivie, Suivi de la semaine, Délai de qualification, Séance de rattrapage proposée, Centre masqué (Direction), Droit de correction, Écriture concurrente, Absence d'une exclusion, Intervenant, Période de détection, Fenêtre du contrôle, Fuseau du centre, Notification du jour, Bilan du contrôle, Jour ignoré, Absences du pilotage, Aujourd'hui du pilotage, Mois terminé, Avertissement de clôture, Journal du mois d'absences, Mois d'absences clos (refus).
- Points « à valider » des fiches A1 (1 à 4), A2 (1 à 4, 7), A3 (1 à 8 ; `DomainServiceConfig` `:34`), A4 (1 à 5), A6 (3 à 5 ; relecture native ar/kab, avec un **risque de sens en kabyle** : « tamettant », « inelmaden »).
- Sujets à trancher : KB-ABS-05 (autres routes Séance), 15, 24 (prérequis Patient et couverture), 31.

## Proposition d'état (à poser par l'orchestrateur, non modifié ici)

- `contexts.absences-patients` : `modeled` → `architected` maintenant (comme facturation et gmao), puis `verified` une fois les ADR acceptés et le seam A4 fermé.
- `slices.absences-patients-a{1,2,3,4,6}-*` : `built` → `verified` maintenant ; `done` après les contrôles de production 1 à 7 et la période d'observation.
- `phases.verify` reste `in-progress` (Stock en cours).

## Verdict : **pass-with-actions**

Top 3 actions :
1. Faire accepter les ADR 0038 à 0042 et les 21 termes du glossaire ; corriger les incohérences d'en-tête.
2. Fermer le seam A4 dans `covered.json` (`seamAllowed`) et `seams.md`.
3. Exécuter les contrôles § 24 à 27 dans l'ordre ci-dessus (§ 24.1 bloquant) avant la mise en production.

Prochaine étape du pipeline : finir Stock (`/ddd-slice stock-s8-peremptions-retrait`), puis `/ddd-tactical planification`, puis Patient et couverture et Séance en dernier (`roadmap.md` § 2).
