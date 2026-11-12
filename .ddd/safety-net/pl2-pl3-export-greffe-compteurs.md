# Filet PL2 (export PDF greffe) et PL3 (compteurs du tableau de bord + JourDuCentre)

Date 2026-11-11. Niveau : risque faible, HTTP intégré (socle Planification/Couverture, H2, `@SpringBootTest` MOCK + MockMvc).

## Constat de départ (grep)
Les 3 routes avaient déjà un filet ; seul le manquant a été ajouté.

| Route | Filet existant | Manque |
|---|---|---|
| `GET /api/v1/patients/{id}/greffe/export-pdf` (PL2) | DM7 `LesEvaluationsKdigoEtLExportPdf...` (PDF riche, patient sans bilan, inconnu, autre centre, formes `%PDF`) ; DM7 `LesDroitsLeCloisonnement...` (14 profils, anonyme, centre B) ; `GreffePdfServiceTest` | rien |
| `GET /api/v1/dashboard/stats`, `POST /stats/search` (PL3) | PC4 `JourDuCentre...` (jour du centre, fuseau), PC4 `EffetsEtLecteurs...#compteurs` (attestations, expirationDays, mois, centre B), `DashboardRestControllerMonthFilterTest` | cas vide, PEC/effectif riches, droits par profil, garde de centre, requêtes mal formées |

## Ajout
`backend/platform/src/test/java/com/hemodialyse/backend/characterization/patientcouverture/pc4/TableauDeBordDesCompteursCharacterizationIntegrationTest.java` (5 scénarios, 5 goldens relisibles, approuvés un par un après lecture du `.received`) dans `src/test/resources/characterization/patientcouverture/pc4/tableau_de_bord/`.

| Scénario | Golden |
|---|---|
| centre sans donnée (GET, GET+mois, POST) | `centre_vide` |
| cas riche : PEC CREE/VALIDEE/CLOTUREE, attestations, effectif, expirationDays 0/15/365, mois, mois illisible, centre B isolé, POST défaut 30 | `compteurs_riches` |
| droits : 11 profils + 3 rôles, GET et POST, anonyme | `droits_par_profil` |
| centre de session différent / absent / centre inconnu (avec données en A) | `centre_de_session` |
| requêtes mal formées | `requetes_mal_formees` |

PL2 : pas de PDF binaire complet ; les goldens DM7 normalisent en-tête `%PDF`, taille et métadonnées.

## Bugs connus
KB-PL3-1 à KB-PL3-5 dans `known-bugs.md` (pas de garde de centre, 500 sur entrées illisibles, `pecExpiring` inclut les échues, mois illisible ignoré, 403 ROLE_SCOPE DIRECTION/SUPERADMIN). Aucun KB-PL2.

## Seams / limites
Aucun seam (`seamAllowed` = []). L'horloge n'est pas injectable : les scénarios dépendant de « aujourd'hui » utilisent `supposerLeJourDeLaJvmEgalAuJourDuCentre` (ignorés si le centre et la JVM ne sont pas le même jour, ~quelques heures/jour) ; le jour du centre est verrouillé par le test PC4 existant (fuseaux extrêmes). Les chiffres du golden riche sont relatifs (`jour(±n)`), et le mois fixe `2026-01` repose sur `created_at` posé en base (2026-01-01).

## Exécution
5 classes (nouveau + JourDuCentre + DM7 export + MonthFilter + GreffePdfServiceTest, 25 tests) x 3 passes `-Dsurefire.runOrder=random` : 3 x vert (~50 s par passe, dominé par le démarrage Spring ~27 s). Contextes Spring démarrés : 2 par passe (profil `characterization` partagé + profil `test` de MonthFilter). Journaux : scratchpad `pass1..3.log`. `check-arch` : 0 violation.

## Fichiers de production inscrits (covered.json)
Déjà présents : `GreffeExportRestController`, `GreffePdfService`, `DashboardRestController`, `JourDuCentre`, `JourDuCentreLegacy`. Aucun ajout : `greffe_patient.jrxml` n'existe pas (PDF construit par `GreffePdfService`).
