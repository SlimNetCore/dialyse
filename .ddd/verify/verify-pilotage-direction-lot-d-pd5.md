---
status: proposed
date: 2026-11-09
scope: slice pilotage-direction-pd5-lectures-publiees, lot D (commit 7caa9119, ADR 0105), refactoring sur place (ADR 0024)
sources: [git show --stat HEAD, ADR 0105, fiche PD5, .ddd/safety-net/pilotage-direction.md §10, TableDuPilotageTest, LesAbonnementsDeLaDirectionCharacterizationTest, DirectionSubscriptionTracker, poms, RG-DIR-109, annexe 16, glossaire, covered.json, roadmap, state.json, known-bugs.md KB-DIR-01..08]
reviewer: relecteur independant (lecture seule ; Maven lance un build a la fois)
---

# Revue PD5 (lot D) : cloture du Pilotage direction

## Verdict : **pass-with-actions**
0 blocker, 1 major, 4 minor, 3 nit. Aucun code de production touche, aucun `.approved`, aucun seam. Le major porte sur la conclusion « il ne reste rien du Pilotage dans `application/direction` » : elle est defendable pour 10 fichiers sur 11 mais pas pour `DirectionIndicatorsQueryService`.

## Controles automatiques
| Controle | Resultat |
|---|---|
| `git show --stat HEAD` | 12 fichiers : 2 tests (`TableDuPilotageTest`, `LesAbonnementsDeLaDirectionCharacterizationTest`), docs, ADR, `covered.json`, `seams.md`, `state.json`. 0 fichier `src/main`, 0 `.approved` (`grep -c approved` = 0). Tracker non modifie. |
| Maven (`./mvnw -o -pl contexts/pilotage-direction,platform -am`, PD1 a PD5 caracterisation, `TableDuPilotageTest`, `FrontieresDesContextesTest`, `ReglesDeGestionDocumentationTest`, tests du module) | ordre par defaut : vert (PD1 6, PD2 4, PD3 4, PD4 3, PD5 8, TableDuPilotageTest 11, Frontieres 45, ReglesDeGestion 7). `-Dsurefire.runOrder=random` : 2 483 tests, 0 echec, BUILD SUCCESS. |
| `covered.json` / `state.json` / roadmap | JSON valide ; `seamAllowed: []` (`covered.json:129`) ; tracker ajoute ; `pd1..pd5` = `built` dans `state.json:88-92` et `roadmap.md` ; contexte `pilotage-direction` = `architected` (`state.json:117`), annonce explicitement dans la roadmap. |
| SQL hors module | `direction_snapshot` / `direction_alert_history` : seuls `InstantanesMensuelsJdbc`, `HistoriqueDesAlertesJdbc` en `src/main` ; le reste = migrations Flyway/schema, tests (contrat du module, caracterisation, `Direction*IntegrationTest`), JSON golden. Aucun lecteur de production hors module. |

## Reponses aux points demandes
| # | Reponse |
|---|---|
| 1 | Audit §10 : 10 fichiers sur 11 correctement attribues (rendu PDF, comptes IAM, `DirectionAccessGuard`, controleurs, lectures Absences/Capacite/GMAO/Stock : aucun SQL, aucun n'est du Pilotage). **Reserve** `DirectionIndicatorsQueryService` (voir M1) et `AnonymityPolicy` (voir m2). |
| 2 | Cycle Maven du tracker : **reel mais mal motive**. `WebSocketAuthentication.SOCIETE_TOPIC_PREFIX` est `static final` package-prive de la plateforme (`WebSocketAuthentication.java:37`) et la plateforme depend du module (`platform/pom.xml:85`) : le deplacer creerait un cycle ; mais le vrai obstacle est l'absence de Spring messaging/WebSocket dans le module (`pom.xml` du module : webmvc, jdbc). Une constante dupliquee suffirait pour le cycle, pas pour le transport : decision juste, motif (b) a nuancer. Filet : 8 tests, tracker et `SuivreLeTempsReel` reels (evenements Spring fabriques, ports sortants en memoire), observation par `estSuivie` et recalculs du balayage : pas de mock tautologique. Cas couverts : double signalement, sessions multiples, `s1/` vs `s10/`, destinations/UUID illisibles, desabonnement inconnu. Non couverts : concurrence (`ConcurrentHashMap` check-then-act), message STOMP reel (question ouverte 3 deja posee). |
| 3 | `TableDuPilotageTest` : liste exhaustive des 11 fichiers par egalite d'ensemble ordonne (`:194-199`) : un fichier ajoute dans `application/direction` fait echouer ; garde non vacueuse (`la_garde_detecte...`, `la_garde_balaie...`). Limites : m3, m4. |
| 4 | Tracker non modifie, aucun `.approved`, aucun code de production : **confirme**. |
| 5 | RG-DIR-109 conforme au code (`DirectionSubscriptionTracker.java:36-77` : `putIfAbsent` sur `session/abonnement`, prefixe `session/`, `IllegalArgumentException` ignoree, redemarrage = oubli). Annexe 16 (`:168`) et glossaire (`Abonne`, `glossaires/pilotage-direction.md:27`) alignes. |
| 6 | Residuels PD5-R1..R8 honnetes sauf R5 (raison « dependances sans cycle » non verifiee : M1) et R8 (omet KB-DIR-05, 06, 08 : n1). Proprietaires cibles plausibles. |
| 7 | Module ferme : ArchUnit module + `FrontieresDesContextesTest` verts, regles nominatives pour `DirectionIndicatorsQueryService` et `DirectionCapaciteQueryService` (`FrontieresDesContextesTest.java:56,99`). KB-DIR-01..08 consolides dans `.ddd/safety-net/known-bugs.md:2034-2047` ; pas de liste unique « questions DPO/metiers » datee 2026-12-15 (m4). |

## Constats
| # | Severite | Constat | Preuve | Correction |
|---|---|---|---|---|
| M1 | major | `DirectionIndicatorsQueryService` est attribue a « Dossier medical + Stock » alors qu'il compose le tableau de bord, la politique d'alertes et les alertes du Pilotage (langages publies) avec les indicateurs cliniques et du stock, et qu'il n'est consomme que par la Direction (controleur + 3 traducteurs `acl/pilotagedirection/*` qui alimentent des ports du Pilotage : boucle Pilotage -> port -> plateforme -> service -> langage publie du Pilotage). Il est la vue « indicateurs de la direction » : candidat naturel au Pilotage. PD5-R5 invoque des « dependances sans cycle » : le `pom.xml` du module depend deja de `contexts-dossier-medical` (`:79`), lequel depend de `contexts-stock` (`dossier-medical/pom.xml:97`) ; aucun des deux ne depend du Pilotage : le cycle n'est pas demontre. | `DirectionIndicatorsQueryService.java:3-12,39-48,87-100`, ADR 0105 §Residus PD5-R5, `pilotage-direction.md` §10 | Reformuler PD5-R5 (proprietaire = Pilotage, motif reel a prouver : dependance de compilation directe vers `contexts-stock`, ou transitivite) et ouvrir une slice PD6 ou un ADR de refus motive ; ne pas annoncer « rien a migrer » tant que non tranche. |
| m1 | minor | Motif (b) de la decision 2 (cycle Maven) incomplet : une constante dupliquee supprime le cycle ; la vraie raison est (a) (pas de dependance WebSocket dans le module). | ADR 0105:19, `WebSocketAuthentication.java:37` | Reecrire la decision 2 autour du seul motif (a). |
| m2 | minor | Le seuil d'anonymat 5 existe en deux copies : `AnonymityPolicy.THRESHOLD` (plateforme) et `SeuilDAnonymat.SEUIL` (module). Le DPO (KB-DIR-01) devra les modifier ensemble ; aucun test d'egalite. | `AnonymityPolicy.java:15,23-24`, `SeuilDAnonymat.java:13` | Test de coherence des deux constantes ou residuel PD5-R9 (proprietaire : Pilotage) ; la mention « le Pilotage a son SeuilDAnonymat » dans `TableDuPilotageTest.java:181` le reconnait sans le suivre. |
| m3 | minor | Echeance 2026-12-15 non appliquee : `assertThat(REVUE).isEqualTo(LocalDate.of(2026,12,15))` est tautologique (`:231`), et le butoir des deux lecteurs est teste contre 2099. Passee cette date, rien n'echoue. | `TableDuPilotageTest.java:148-155,228-236` | Ajouter `assertThat(LocalDate.now()).isBeforeOrEqualTo(REVUE)` (ou un avertissement) ; supprimer le test a nombre code en dur. |
| m4 | minor | Questions DPO/metiers non consolidees en un point : K-DIR dans `known-bugs.md`, PD5-R8 (cinq sur huit), RG-DIR-105..108 « a confirmer 2026-12-15 », ADR 0104/0105. | ADR 0105:37, `13-direction.md:181` | Ajouter un tableau unique KB-DIR-01..08 (decision en vigueur, question, interlocuteur, date) dans l'ADR 0105 ou `known-bugs.md`. |
| n1 | nit | PD5-R8 omet KB-DIR-05, 06, 08. | ADR 0105:37 | Les ajouter ou dire qu'ils sont tranches. |
| n2 | nit | `fichiersDeApplicationDirection` utilise `Files.list` (non recursif) : un sous-package `application/direction/x/` echapperait a la garde. Aujourd'hui aucun sous-dossier. | `TableDuPilotageTest.java:187-191` | `Files.walk`. |
| n3 | nit | `SQL_EN_MAJUSCULES` ne voit que `FROM|JOIN` en majuscules suivis d'un nom minuscule : un `from` en minuscules ou un nom a majuscule passe. | `TableDuPilotageTest.java:185` | `Pattern.CASE_INSENSITIVE` + `[A-Za-z_]`. |

## Suites données (2026-11-09)
Faits (tests, docs et etat seulement ; aucun code de production, aucun `.approved`, `seamAllowed` reste `[]`).
| # | Suite donnee |
|---|---|
| M1 | `DirectionIndicatorsQueryService` NON migre. PD5-R5 reformule (propriétaire cible Pilotage pour la composition, lectures cliniques/stock par langages publiés ; « dependances sans cycle » retiree, absence de cycle declaree non demontree) dans ADR 0105, fiche PD5 et `TableDuPilotageTest`. Slice `pilotage-direction-pd6-composition-des-indicateurs` creee (fiche, `state.ts set ... planned`, `roadmap.md`). A confirmer metiers/DPO <= 2026-12-15. |
| m1 | Motif du maintien du tracker corrige (seul motif : pas de Spring WebSocket dans le module ; cycle via la constante evitable par duplication) : ADR 0105 decision 2 et PD5-R7, fiche PD5. |
| m2 | `TableDuPilotageTest.les_deux_seuils_d_anonymat_de_la_plateforme_et_du_pilotage_sont_egaux` ; residuel PD5-R9 (unification, Pilotage). |
| m3 | Aucun garde du depot n'applique sa date (le « 2026-12-15 » des autres gardes est du texte de raison) : test `la_revue_du_2026_12_15_n_est_pas_depassee` (echoue apres la date en UTC tant que `REVUE` n'est pas repoussee) ; test tautologique retire. |
| m4/n1 | Tableau unique KB-DIR-01..08 (decision en vigueur, question, interlocuteur) dans ADR 0105 et fiche PD5 ; PD5-R8 couvre les huit. |
| n2/n3 | `Files.walk` (chemins relatifs) ; motif SQL insensible a la casse. Aucun faux positif sur les 9 fichiers controles. |
Verifications : `check-arch.ts` 0 violation ; `TableDuPilotageTest` (13), `FrontieresDesContextesTest` (45), `ReglesDeGestionDocumentationTest` (7) verts en ordre par defaut et en `-Dsurefire.runOrder=random` (65 tests, BUILD SUCCESS). Rappel du controle de revue : 2 483 tests verts (defaut et random) sur le jeu PD1 a PD5 + plateforme, non rejoue en entier apres ces corrections (tests ajoutes uniquement dans `TableDuPilotageTest`).
