---
slice: dossier-medical-dm7-pre-greffe
status: characterized
date: 2026-11-07
sources: [.ddd/migration/slices/dossier-medical-dm7-bilan-pregreffe.md, ADR 0024, 0072, 0080, known-bugs.md KB-MED-85 à 97]
---

# Filet DM7 : bilan pré-greffe (legacy, refactoring sur place)

## Périmètre vérifié par lecture du code
Cinq contrôleurs : `BilanPreGreffeRestController` (GET `greffe/bilan`, PUT `statut`, `immunologique`, `notes`, POST `decisions-rcp`), `EtapeBilanPreGreffeRestController` (GET, POST, POST `generer-standard`, PUT, DELETE `greffe/etapes`), `DonneurVivantRestController` (GET, POST, PUT, DELETE `greffe/donneurs`), `KdigoGreffeRestController` (GET `greffe/kdigo`), `GreffeExportRestController` (GET `greffe/export-pdf`), soit 16 routes. Tables réelles : `bilans_pre_greffe`, `decisions_rcp` (entité du bilan, réécrite avec lui), `etapes_bilan_pre_greffe`, `donneurs_vivants` (jamais les tables parallèles `bilans_pregreffe` du module gelé, ADR 0072). Aucune clé étrangère ni contrainte d'unicité. Autres lecteurs des tables, non observés : `DirectionIndicatorsQueryService` (comptages de statuts sur `bilans_pre_greffe`) et le rollback d'historique.

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`), état SQL des quatre tables, audit (`AuditRequestFilter`), notifications durables, et lecteurs tels quels : sérologies (`serologies_patient`), antécédents actifs (`antecedents_medicaux`), analyses (`resultats_analyses`, créatinine) via `greffe/kdigo` et `greffe/export-pdf` (texte du PDF extrait avec PDFBox). **Aucun seam, aucun code de production modifié** (`seamAllowed` vide, voir `seams.md`).

## Scénarios (38 tests, 38 goldens `src/test/resources/characterization/dossiermedical/dm7/<dossier>/<scénario>/*.approved.json`)
| Dossier | Classe | Tests | Contenu |
|---|---|---|---|
| bilan/etat | `LeBilanDuReceveurEtLaMachineAEtats…` | 7 | ouverture par la lecture (get-or-create), matrice 7x7 des transitions, dates de début / inscription / greffe, statuts et corps illisibles, bilan immunologique (PRA, bornes de colonnes), notes, lignes illisibles ou en double |
| bilan/rcp | `LesDecisionsDeRcpDuBilan…` | 4 | ajout et ordre (POST puis GET), doublons, champs obligatoires et date future, bornes |
| etapes | `LaChecklistDuBilanPreGreffe…` | 7 | génération de la liste standard (23 étapes, idempotente, partielle), création, mise à jour (remplacement, FAIT exige une date), tri et suppression silencieuse, lignes illisibles, générations simultanées |
| donneurs | `LesDonneursVivantsCandidats…` | 6 | enregistrement (CANDIDAT, NON_FAIT), refus et bornes de colonnes, mise à jour (remplacement complet), refus sans écriture, tri par création décroissante et suppression silencieuse, ligne illisible |
| droits | `LesDroitsLeCloisonnementEtLesEffets…` | 7 | 14 profils + anonyme x 16 routes, refus sans écriture, cloisonnement (session A vers B, session B vers un patient de A, sessions particulières), audit, lectures simultanées, décisions simultanées, dernière écriture gagnante |
| lecteurs | `LesEvaluationsKdigoEtLExportPdf…` | 7 | forme KDIGO, PRA (10 cas, seuils 20 et 80), fonction rénale (13 cas), sérologies (13 marqueurs, résultats, ancien contre récent), PDF complet, PDF vide ou patient inconnu, PDF à ligne illisible |

## Déterminisme
Dates explicites fixes `<D+n>` ; le domaine lit le jour de la JVM pour les dates de statut et la limite « pas dans le futur » d'une décision de RCP (`<J>`, `<J+1>`), avec garde de minuit (attente si moins de 2 min). Âge KDIGO calculé depuis `LocalDate.now()` (né il y a 50 ans et 30 jours : stable). Identifiants et horodatages masqués (alias nommés ou `<UUID_GENERE_n>`) ; journal d'audit trié sur l'alias de l'identifiant ; alertes sérologiques triées (ordre de table de hachage). Les lignes créées par l'application ne disent pas si elles ont été modifiées (écart de microsecondes entre `created_at` et `updated_at`, flakiness détectée et écartée avant clôture). Les scénarios de concurrence ne verrouillent que des invariants (nombre de lignes dans un intervalle, cohérence lecture suivante / nombre de bilans). Caches de patients vidés à chaque fixture. Données fictives (« Patient <alias> », « Donneur <alias> », valeurs rondes). Goldens approuvés un par un (lecture du `received`, puis écriture de l'`approved`), jamais en lot.

Exécutions : suite DM7 3 fois consécutives dont une en ordre aléatoire (`-Dsurefire.runOrder=random`), 0 flaky ; puis suite complète `./mvnw -o test -fae` (voir le rapport de clôture).

## Limites (non verrouillé)
Couverture de branches non mesurée et aucune mutation exécutée (aucun outil configuré dans le module, PIT documenté dans `.ddd/research/mutation-testing-pit.md`, tests Spring trop lents). Schéma PostgreSQL non rejoué (H2 en mode de test ; le tri des étapes par collation, les messages d'erreur de colonne et l'arrondi diffèrent peut-être ; aucun test de contrat à deux connexions : la concurrence n'est observée que par invariants). `DirectionIndicatorsQueryService` (indicateurs de statuts), rollback et import d'historique, purge : non observés. Un doublon de bilan (deux lignes pour un patient) n'est rejoué que sur GET et PUT notes, pas sur KDIGO ni PDF. Ordre des alertes KDIGO (table de hachage) et lignes du PDF coupées par la largeur (polices de la machine) : le texte du PDF est un golden lié à l'environnement. Ordre des décisions de RCP à la même date, des étapes à même libellé : non spécifié. Les valeurs de PRA, groupes sanguins et typages HLA ne sont pas validés médicalement. Lectures non tracées en audit (KB-MED-96), cache du patient par identifiant (une modification de patient peut être masquée pour KDIGO / PDF, non rejoué).

## Questions ouvertes (à confirmer avec les métiers et le DPO)
Reproduire ou corriger KB-MED-85 à 97, en particulier : `GET greffe/bilan` qui écrit (85), les trous de sécurité sans garde de patient (86), l'unicité du bilan et la perte de décisions simultanées (90, 91), le 500 sur crossmatch absent et sur corps illisible (87, 89), l'alerte VIH conservée malgré un résultat négatif plus récent (94), l'export PDF interdit à l'ADMIN (95) et les lectures non journalisées (96). Rattachement de `greffe/kdigo` à DM7 (confirmé par le découpage du 2026-11-06, ADR 0080) ; évolution du modèle (`DemarrerBilan`, `ValiderEtape`, `CompleterBilan`, événement `BilanPreGreffeComplete`) hors filet.
