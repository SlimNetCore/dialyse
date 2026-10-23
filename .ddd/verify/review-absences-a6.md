---
status: proposed
date: 2026-10-23
scope: slice absences-patients-a6-cloture-mensuelle (non commité, au-dessus de 04d49037)
sources: git status/diff, check-arch.ts, state.ts show, code et artefacts listés ci-dessous
---

# Revue indépendante — A6 Clôture mensuelle des absences

Contrôles automatiques : `check-arch` **0 violation** (0 en baseline, 0 nouvelle). `scan --incremental` non relancé
(revue en lecture seule ; il réécrit `.ddd/scan/`). Aucun build ni test lancé (consigne).

## Vérifications passées (sans constat)

- **Garde exhaustive** : `absence_patient` n'a qu'un seul écrivain (`AbsencesJdbc`). Ses 7 appels d'écriture passent
  tous par une vérification du mois sous verrou : Declarer:73, Qualifier:46, Annuler:47, Rattraper:64-67 (deux mois,
  ordre croissant ; une date antérieure est déjà refusée par `AbsencePatient` `ABSENCE_RATTRAPAGE_ANTERIEUR`),
  RetirerLAbsenceDeLExclusion:34, Detection via `DateOuverte.siOuverte` (DateOuverte.java:46), Reconciliation:80. L'ACL
  Facturation (`ExclusionDeLaFacturation`) passe par Declarer/Retirer, et `ExclureUneSeance` ne traite qu'une séance par
  transaction.
- **Interblocage** : l'ordre des verrous est toujours absence (s) → mois (s) dans l'ordre croissant ; la clôture ne
  verrouille que le mois puis lit sans verrou ; la Facturation verrouille son mois → mois d'absences, jamais l'inverse
  (sa clôture lit l'état des absences sans verrou). Aucun cycle trouvé.
- **Course clôture/écriture** : `FOR UPDATE` sur une ligne créée si elle manque (MoisAbsencesJdbc:203-230),
  `lock_timeout` de 5 s (UniteDeTravailSpring:45, configuration:136), puis 55P03 → `EcritureConcurrente` (RefusDeVerrou:
  29-35). L'avertissement est compté sous verrou, donc exact.
- **Atomicité** : verrou + compte + état + journal + événement dans la même `uniteDeTravail`
  (CloturerLeMoisDAbsences.java:55-68 ; RouvrirLeMoisDAbsences.java:103-109) ; l'événement est publié dans la
  transaction (PublicationDesMoisDAbsencesSpring).
- **Calcul du mois** : fuseau du centre, sans jours de grâce (CloturerLeMoisDAbsences.java:51-53) ; le premier jour du
  mois suivant est testé (MoisDAbsencesCloTest:111). Le retard est calculé comme dans la notification.
- **Hexagonal / Facturation** : changement minimal et additif (port `AbsencesDuMois` + ACL `AbsencesDuMoisPubliees` +
  champ `absencesDuMoisNonCloses`) ; avertissement seulement, la clôture n'est jamais refusée.
- **Goldens** : 13 `_nouveau_code` A6 (9 cycle-de-vie + 4 détection) + `mois-ouvert-a6/..._nouveau_code` ; l'ancien
  golden est conservé dans `_ecarts-legacy/…legacy.json`. Tests d'intégration relatifs au mois avec garde de minuit
  (Support:245-250) ; tests unitaires sans horloge réelle.
- **i18n** : les 30 clés A6 sont présentes en fr/en/ar/kab. Les manques ar/kab plus anciens sont hors périmètre.
- **Docs** : ADR 0042, RG-ABS-070..078, KB-ABS-37..40, verifications-production §27, roadmap et glossaire sont cohérents
  avec les décisions du 23/10.
- **Index git** : `TousLesMoisSontOuverts.java` **n'a pas** été retiré par `git rm --cached`. Il est encore dans l'index
  (`100644 e0cb21e…`) et supprimé seulement dans l'arbre de travail (` D`). C'est une suppression ordinaire, non
  indexée.

## Constats

| Sév.  | Domaine          | Constat                                                                                                                                                                                                                                                                                                                                                                                                                                                       | Preuve                                                                                                                                                        | Correction recommandée                                                                                                                                              |
|-------|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| major | stratégique/docs | Le modèle tactique (`status: approved`) contient encore l'ancienne **décision 3** (« la réouverture d'un mois d'absences est refusée si le mois de facturation correspondant est clos »), contraire à l'ADR 0042 et au code. Il garde aussi « + `MoisDeFacturation` publié si Q3 = oui », « Rejets proposés … à trancher (Q3) », « aucun golden (nouveauté) » et Q1-Q4 ouvertes. La fiche le signale seulement comme « à mettre à jour au prochain passage ». | `.ddd/tactical/absences-patients.md:313`, `:198`, `:112-113`, `:248`, `:327-333` ; fiche `.ddd/migration/slices/absences-patients-a6-cloture-mensuelle.md:93` | Mettre à jour le modèle tactique maintenant (décision 3 remplacée par ADR 0042, canvas `MoisAbsences` avec `enRetard`, Q1-Q4 tranchées) avant de commiter la slice. |
| minor | legacy/git       | L'index est incohérent : le golden `mois-ouvert-a6/cloture_de_facturation_avec_absence_a_qualifier.approved.json` est indexé (`A`) alors qu'il est supprimé de l'arbre (`AD`), et plus aucun test ne le référence. Le test `MoisOuvert…A6…IntegrationTest` est `AM` : sa version indexée cite encore l'ancien golden. Un `git commit` sans `-a`/`add -A` livrerait un état incohérent.                                                                        | `git status` (`AD`, `AM`) ; `MoisOuvertDesEcrituresDAbsenceA6CharacterizationIntegrationTest.java:133`                                                        | `git rm --cached` sur le golden obsolète, puis `git add -A` sur le périmètre A6 (ce qui indexe aussi la suppression de `TousLesMoisSontOuverts.java`).              |
| minor | tactique         | Dans la détection, un `EcritureConcurrente` venant maintenant **aussi** d'un verrou de mois impossible à prendre (clôture en cours > 5 s) est pris pour « déclaration concurrente » et avalé : ni absence créée, ni `echecs`, ni `joursIgnores`. Si la clôture échoue ensuite, l'absence n'est pas détectée et rien ne le signale.                                                                                                                            | `DetectionAbsences.java:91-94`                                                                                                                                | Distinguer les deux causes (relire `absences.existe` après l'exception ; sinon compter un `Echec`).                                                                 |
| minor | archi/sécurité   | `GET /mois` est ouvert aux 4 profils (décision ADR 0042:21,58 : c'est voulu). Mais la réponse expose aussi le **motif de réouverture** (texte libre ≤ 500) et les UUID des administrateurs à SECRETAIRE/MEDECIN/INFIRMIER, alors que l'écran n'a besoin que du statut. En F7, la même structure est réservée à ADMIN.                                                                                                                                         | `MoisDAbsencesControleur.java:47,89,144-151` ; `PeriodesDeFacturationControleur` `@PreAuthorize("hasRole('ADMIN')")`                                          | Suggestion : ne renvoyer que `mois`/`statut` aux non-ADMIN, ou confirmer explicitement la règle dans l'ADR.                                                         |
| minor | perf             | La notification de nuit fait 1 à 2 requêtes `COUNT` **par mois clos depuis l'origine** pour chaque centre et chaque nuit : le coût croît sans borne maintenant que des mois peuvent être clos (avant, c'était une no-op).                                                                                                                                                                                                                                     | `NotificationDesAbsencesAQualifier.java:48-56`                                                                                                                | Une seule requête qui exclut les mois clos (jointure `mois_absences`), ou se limiter à la fenêtre utile.                                                            |
| nit   | archi            | Le refus de verrou sur `/mois/*/cloturer                                                                                                                                                                                                                                                                                                                                                                                                                      | rouvrir` renvoie le code `ABSENCE_ECRITURE_CONCURRENTE`, nom propre aux absences.                                                                             | `RefusDeVerrou.java:27` ; `MoisAbsencesJdbc.java:204`                                                                                                               | Code dédié ou documenté dans RG-ABS-077. |
| nit   | archi            | `FacturationContexteConfiguration` utilise des noms entièrement qualifiés au lieu d'imports.                                                                                                                                                                                                                                                                                                                                                                  | `FacturationContexteConfiguration.java:287-301`                                                                                                               | Imports.                                                                                                                                                            |
| nit   | tactique         | Le domaine connaît la sémantique de verrou : `DateOuverte.siOuverte` appelle `estOuvertSousVerrou`. Le motif de réouverture est un `String` (primitive obsession légère).                                                                                                                                                                                                                                                                                     | `DateOuverte.java:46` ; `MoisAbsences.java:28,83-100`                                                                                                         | Garder le verrou côté application ; VO `MotifDeReouverture`.                                                                                                        |
| nit   | frontend         | « Mois en cours » est calculé dans le fuseau du navigateur, pas celui du centre. Seul le bouton est concerné : le serveur reste la référence (422 `NON_TERMINE`).                                                                                                                                                                                                                                                                                             | `mois-d-absences.util.ts:12-13`                                                                                                                               | Accepter ou se baser sur `MOIS_D_ABSENCES_NON_TERMINE`.                                                                                                             |
| nit   | perf             | `estPostgreSql()` lit les métadonnées de la connexion à chaque mois créé.                                                                                                                                                                                                                                                                                                                                                                                     | `MoisAbsencesJdbc.java:269-272`                                                                                                                               | Mémoriser le résultat.                                                                                                                                              |

## Verdict : **pass-with-actions**

0 blocker · 1 major · 4 minor · 5 nit

Top 3 actions :

1. Mettre à jour `.ddd/tactical/absences-patients.md` (décision 3 remplacée, Q1-Q4 tranchées, canvas `MoisAbsences`).
2. Assainir l'index git (golden `AD` obsolète, test `AM`, suppression de `TousLesMoisSontOuverts.java` à indexer) avant
   le commit.
3. Ne plus confondre « verrou de mois impossible » et « déclaration concurrente » dans `DetectionAbsences:91`.
