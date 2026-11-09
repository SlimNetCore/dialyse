---
status: proposed
date: 2026-11-09
scope: slice pilotage-direction-pd3-alertes, lot C (commit 629c75f7, ADR 0104), refactoring sur place (ADR 0024)
sources: [git show 629c75f7 (79 fichiers, +2 384 / -915), HEAD~1 pour DirectionAlertPolicy, DirectionAlertHistoryService, DirectionRealtimeService, DirectionRealtimePort, DashboardDiff, DirectionRealtimeScheduler et leurs tests, ADR 0024, 0104, fiche PD3, KB-DIR-02/03/04/07, seams.md, covered.json, glossaire pilotage-direction, docs/reference/regles-de-gestion/13, 15, 16, 01, documentation-fonctionnelle.html, verifications-production.md §71, verify-pilotage-direction-lot-b-pd2.md]
reviewer: relecteur independant (lecture seule ; Maven lance par le relecteur, un build a la fois)
---

# Revue PD3 (lot C) : alertes de la direction et temps reel dans le contexte Pilotage direction

## Verdict : **pass-with-actions**

0 blocker, 0 major, 4 minor, 4 nit. La parite est reelle : aucun `*.approved.json` touche (`git show --stat HEAD | grep -c approved` = 0), les modifications des filets (goldens PD3, `DirectionRealtimeIntegrationTest`) se limitent aux types, noms de methodes et au cablage ; tous les tests demandes sont verts en ordre par defaut et en ordre aleatoire. Le code repris est conforme au legacy ligne a ligne. Les actions portent sur le HTML client (RG-DIR-105 a 108 absentes), une phrase imprecise de RG-DIR-106 et des tests de traduction/course manquants.

## Controles automatiques
| Controle | Resultat |
|---|---|
| `check-arch.ts` | 0 violation (0 baseline, 0 nouvelle). |
| `state.ts show` | `pd1`, `pd2`, `pd3`, `pd4` : `built` ; `pd5` : `planned`. |
| `scan.ts --incremental` | NON lance (reecrit `.ddd/scan/*`). Frontieres controlees par ArchUnit et grep. |
| Maven, ordre par defaut (`-pl contexts/pilotage-direction,platform -am`) | PD1 6, PD2 4, PD3 4, PD4 3 ; `DirectionSnapshotIntegrationTest` 5 ; `DirectionReportIntegrationTest` 3 ; `DirectionRealtimeIntegrationTest` 5 ; `TableDuPilotageTest` 7 ; `TableDeLOrganisationTest` 4 ; `ReglesDeGestionDocumentationTest` 7 ; `FrontieresDesContextesTest` 45 ; `NotificationServiceSaisieInfirmierTest` 9 ; `NotificationJournalIntegrationTest` 11 ; `DirectionGmaoCharacterizationIntegrationTest` 11 ; `PatientsSurEquipementIndisponibleTest` 1 ; `IndicateursAFigerParLaPlateformeIntegrationTest` 1 ; tests de peremption/liberation : 0 echec, 0 erreur. Module seul : 153 tests, 0 echec (ArchUnit 10). |
| Maven, `-Dsurefire.runOrder=random` (memes classes + tout le module) | Module 153, plateforme 126 : BUILD SUCCESS, 0 echec, 0 erreur. |
| `seams.md` / `covered.json` / `state.json` | `covered.json` JSON valide, 124 chemins, `seamAllowed: []` ; les 5 fichiers supprimes retires, `IndicateursDuTempsReelParLaPlateforme` ajoute ; tous les fichiers de plateforme edites y figurent sauf `DirectionSubscriptionTracker` (non couvert, seam inscrit puis ferme). |
| `git diff HEAD~1 HEAD -- verifications-production.md` | Ajout du seul §71 (16 lignes `+`, 0 ligne `-`) : rien ne manque. |

## Reponses aux huit points demandes
| # | Question | Reponse | Preuve |
|---|---|---|---|
| 1 | Parite reelle | **Oui.** Aucun golden modifie ; diff des filets = imports, `Alert`->`Alerte`, `Severity`->`Gravite`, `record`->`enregistrer`, `refresh`->`rafraichir`, `watcherAdded`->`abonneAjoute`, `markDirtyForCentre`->`unCentreAChange`, `processDirty`->`traiterLesSignalements`, construction de `SuivreLeTempsReel` ; aucune assertion changee. | `git diff -U0 HEAD~1 HEAD -- .../pd3/`, `.../DirectionRealtimeIntegrationTest.java` |
| 2 | Fidelite | **Conforme.** Seuils 80 / 50 inclusifs, 90 jours, six codes, gravites, valeurs (`null` pour l'observance) et ordre identiques : `PolitiqueDAlertes.java:31-55` = `DirectionAlertPolicy.evaluate:31-55`. Identite (centre, code) sans jour, deux lignes si cle levee deux fois : `RapprochementDesAlertes.java:20-34` = `record:46-63`. Valeur, gravite, nom figes : une ligne ouverte n'est jamais touchee. Reapparition = nouvelle ligne. SQL mot pour mot : `HistoriqueDesAlertesJdbc.java:30-31,39-41,48,53-55` = `DirectionAlertHistoryService:40-41,52-54,61,70-72` (ORDER BY `COALESCE(resolved_at, first_seen_at) DESC, first_seen_at DESC`). Limite : `LimiteDHistorique.bornee` = `Math.max(1, Math.min(limit, 200))` ; `limit=abc` reste 500 (parametre `int` du controleur inchange, golden `06 limit=abc`). Horloge : `HorlogeDuPilotage.maintenant()` = instant JVM (`Timestamp.from(instant)` identique a `OffsetDateTime.now()`). Alimentation seulement si abonne : `signaler` filtre sur `abonnes` ; `rafraichir` ecrit l'historique AVANT de ranger le constat (meme ordre que `snapshot` : lecture, `record`, constat). Cadences : `TempsReelPlanifie.java:14-16,24,29` = 1 000 ms, 10 000 ms + delai initial 10 000 ms (identique a `DirectionRealtimeScheduler:21,26`). Diff : `ConstatDesIndicateurs.changementsDepuis:52-75` = `DashboardDiff.diff:39-61` ; plafond 60 identique ; noms et ordre de construction du constat identiques (`ConstatsDuTableauDeBordParLeTableauDeBord.java:59-111` = `snapshot:153-210`). Noms JSON : `TableauDeBordModifie(type, societeId, at, changes)` et `ChangementDuTableauDeBord(centerId, centre, family, name, before, after)` = `DashboardChanged`/`Change`. | fichiers cites |
| 3 | Tests classiques supprimes | **Equivalence, aucune assertion perdue.** `DirectionAlertPolicyTest` (4) -> `PolitiqueDAlertesTest` (6, dont gravite/valeur par code et 90 jours) ; `DirectionAlertHistoryServiceTest` (4) -> `LesAlertesDeLaDirectionTest` (6) + contrat `HistoriqueDesAlertesContrat` (5, execute sur memoire ET JDBC) + `RapprochementDesAlertesTest` (6) ; `DashboardDiffTest` (5) -> `ConstatDesIndicateursTest` (6). Les cas « isole par societe », « reapparition », « masque jamais rapporte dans aucun sens », « egalite numerique » existent. | listes de tests du module |
| 4 | Seam et editions | **Minimes.** `DirectionSubscriptionTracker` : type injecte + deux noms d'appel (`:21,27,46,68`), logique inchangee. `NotificationService`, `WebSocketStockEventPublisher`, `WebSocketInventaireEventPublisher` : type + un appel. `DirectionGmaoQueryService` : types `Alerte`/`Gravite`, codes et seuils GMAO inchanges. `DirectionIndicatorsQueryService` : politique appelee par le langage publie, memes arguments. Controleur : borne retiree (deleguee au contexte). | `git diff -U1 HEAD~1 HEAD -- ...` |
| 5 | Hexagonal / ACL / cycles | **Conforme.** ArchUnit module 10/10, `FrontieresDesContextesTest` 45/45, `pom.xml` du module sans dependance vers la plateforme (reacteur vert), domaine sans horloge ni UUID genere (`ouvrir` genere l'UUID cote JDBC, comme le legacy). Traducteur `IndicateursDuTempsReelParLaPlateforme` : traduction pure, types explicites (pas d'`Object`), `Alerte` du langage publie reutilisee par les alertes GMAO. Cablage dans la composition root (`PilotageDirectionConfiguration`). Vocabulaire : glossaire a jour (voir m4 pour la double notion `Gravite`). | `PilotageDirectionConfiguration.java` (bloc lot C) |
| 6 | Integrite des artefacts, docs | `seams.md` : ligne des 6 groupes bien formee ; ADR 0104, KB-DIR-04, glossaire, RG-DIR-105 a 108 (`13-direction.md:183-200`), annexe 16, `01-transverse.md`, `15-notifications-alertes.md` coherents entre eux et avec le code, sauf m1 et m2. HTML : aucune reference aux classes supprimees (`grep` : 0), `TempsReelPlanifie` aux 3 endroits attendus. | grep |
| 7 | Residuels PD3-R1 a R5 | **Honnetes.** R1 (tracker sans filet), R2 (aucune unicite, course), R3 (indicateurs fournis), R4 (controleur), R5 (`centers` en SQL) correspondent au code. Voir n2 pour la couverture de R2. | fiche PD3, ADR 0104:40-44 |
| 8 | Points du suivi du lot B | **Fakes vs JDBC** : resolu proprement (un seul contrat execute sur les deux implementations). **Port opaque** : evite (types explicites), mais le traducteur n'a pas de test propre (m3). **INSERT perdu** : sans objet ici (pas d'unicite, donc pas d'INSERT perdu) ; la course est celle de deux lignes ouvertes (PD3-R2) : voir n2. | contrat, `HistoriqueDesAlertesJdbc.java:14-19` |

## Constats
| # | Severite | Domaine | Constat | Preuve | Correction recommandee |
|---|---|---|---|---|---|
| m1 | minor | docs | **Violation (regle « docs toujours a jour »).** `docs/client/documentation-fonctionnelle.html` ne contient pas RG-DIR-105 a 108 ni la section 13.14 (`grep -c "RG-DIR-10[5-8]"` = 0), alors que le Markdown (`13-direction.md:183-200`) les porte et que les lots A et B avaient mis le HTML a jour (RG-DIR-103, 104). Seuls les noms de classes y ont ete remplaces. | `documentation-fonctionnelle.html:4017` (RG-DIR-104, dernier present) | Ajouter les 4 regles dans la structure `div.rule > div.rid + div.rtxt` apres la ligne 4017, et mettre a jour le compte de regles ; regenerer la doc fonctionnelle. |
| m2 | minor | docs | RG-DIR-106 affirme qu'un recalcul qui echoue n'a « ni historique ni diffusion ». Faux si l'echec survient a la diffusion : l'historique est deja ecrit et le constat deja range (`dernier.put`), donc le changement n'est jamais rediffuse. Comportement legacy identique (`refresh:136-150`), mais le texte est inexact. | `SuivreLeTempsReel.java:106-117` ; `13-direction.md:189-194` | Reformuler : « un echec de lecture n'ecrit rien ; un echec de diffusion laisse l'historique ecrit et le changement perdu (statu quo) » ; l'ajouter aux questions ouvertes de l'ADR. |
| m3 | minor | tests | **Suggestion.** `IndicateursDuTempsReelParLaPlateforme` (traducteur ACL de la plateforme) n'a pas de test dedie : seule la traversee de `DirectionRealtimeIntegrationTest` l'exerce. Meme lacune que `IndicateursAFigerParLaPlateforme` au lot B (m2 du lot B, traitee par un test de plateforme). | `platform/src/main/.../IndicateursDuTempsReelParLaPlateforme.java:250-254` | Test de plateforme comparant la sortie de `pour(societe)` a `DirectionIndicatorsQueryService.indicators(societe, null, null)` champ par champ (centres, marqueurs, stock, alertes). |
| m4 | minor | langage | Deux enumerations homonymes pour le meme concept : `GraviteDAlerte` (domaine) et `AlertesDeLaDirection.Gravite` (langage publie), traduites par `valueOf(name())` a trois endroits ; une constante renommee d'un cote casse l'autre a l'execution, pas a la compilation. Le glossaire declare « Gravite » mais pas la raison du doublon. | `AlertesPubliees.java:36,48` ; `ConstatsDuTableauDeBordParLeTableauDeBord.java:56` | Ajouter un test qui verifie l'egalite des constantes des deux enums ; documenter dans l'ADR 0104. |
| n1 | nit | archi | `AlertesDeLaDirection.evaluer` prend 8 parametres primitifs (dont deux `BigDecimal` et trois `long`) : copie de `Inputs`. Acceptable pour garder le contrat plat, mais fragile (ordre des `long`). | `AlertesDeLaDirection.java:160-161` | Introduire un enregistrement `ConstatDuCentre` publie au prochain passage (PD5). |
| n2 | nit | tests | La course PD3-R2 (deux ecrivains ouvrent deux lignes pour la meme cle) est documentee (ADR, RG-DIR-107, §71.1) mais aucun test ne la verrouille : ni au niveau cas d'usage (double dont `ouvertes` est perime), ni au contrat JDBC. | liste des tests `LesAlertesDeLaDirectionTest`, `HistoriqueDesAlertesContrat` | Un test « deux `ouvrir` de meme cle donnent deux lignes ouvertes » dans le contrat, pour que l'ajout futur d'une contrainte d'unicite soit un changement volontaire. |
| n3 | nit | code | `SuivreLeTempsReel.unCentreAChange` remplace `computeIfAbsent` (SQL execute dans le calcul de la map) par `get` puis `put` : deux requetes concurrentes possibles pour le meme centre, sans effet fonctionnel (meme valeur). | `SuivreLeTempsReel.java:68-72` vs `DirectionRealtimeService:93-96` | Rien ; mention dans l'ADR si l'on veut une parite stricte. |
| n4 | nit | docs | Le commentaire de `RepartitionFaceALaCibleTest` dit reprendre « deux cas markers de `DirectionAlertPolicyTest` » : la classe retiree n'avait plus ces cas a `HEAD~1` (4 tests, aucun sur les marqueurs). Reference historique perimee. | `contexts/dossier-medical/.../RepartitionFaceALaCibleTest.java:10` | Retirer la reference ou la dater. |

## Points verifies sans ecart
- Refactoring sur place : aucune table, migration, route, flag, evenement ni projection ; URLs, roles, canal STOMP `/topic/societe/{id}/dashboard` inchanges ; `direction_alert_history` a un seul proprietaire (`HistoriqueDesAlertesJdbc`), garde dans `TableDuPilotageTest` (7 tests verts).
- Plus aucune reference executable aux 6 classes retirees dans `backend/**/src/main` ; seuls des commentaires historiques demeurent (tests, script PG16, docs).
- Le `.ddd/migration/verifications-production.md` ne perd aucune ligne ; le §71 (71.1 a 71.4) est en SELECT seulement.
- Aucune dependance Maven du module vers la plateforme, aucun cycle ; ports fournis (`IndicateursDuTempsReelFournis`, `DiffusionDuTableauDeBordFournie`) implementes cote plateforme.

## Suites données (2026-11-09)
| # | Suite |
|---|---|
| m1 | FAIT : RG-DIR-105 a 108 et section 13.14 ajoutees a `documentation-fonctionnelle.html` (texte du Markdown). |
| m2 | FAIT : RG-DIR-106 reformulee (Markdown + HTML) : echec de lecture = rien d'ecrit ; echec de diffusion = historique ecrit, changement perdu (statu quo). Code inchange. |
| m3 | FAIT : `IndicateursDuTempsReelParLaPlateformeIntegrationTest` (plateforme), comparaison champ par champ a `/indicators`. |
| m4 | FAIT : `GravitesDAlerteConcordentTest` (constantes et ordre identiques) ; residuel PD3-R7. |
| n1 | Non refactore ; residuel PD3-R6 de la fiche. |
| n2 | FAIT : test de contrat « deux ouvertures de meme cle = deux lignes » (memoire + JDBC). |
| n3 | Aucun changement : `get`+`put` non strictement equivalent a `computeIfAbsent`. |
| n4 | FAIT : commentaire de `RepartitionFaceALaCibleTest` corrige. |

## Top 3 actions
1. m1 : mettre a jour `docs/client/documentation-fonctionnelle.html` (RG-DIR-105 a 108, section 13.14).
2. m3 + m4 : test du traducteur des indicateurs du temps reel et test d'egalite des deux enumerations de gravite.
3. m2 + n2 : corriger RG-DIR-106 et verrouiller par un test le comportement de la course PD3-R2.
