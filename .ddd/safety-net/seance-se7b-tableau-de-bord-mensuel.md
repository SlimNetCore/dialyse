---
slice: seance-se7b-tableau-de-bord-mensuel
status: characterized
date: 2026-11-08
sources: [.ddd/migration/slices/seance-se7b-tableau-de-bord-mensuel.md, .ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/safety-net/known-bugs.md]
---

# Filet SE7b : tableau de bord mensuel des séances (`dashboard`, `dashboard/details`, `dashboard/export`)

## Point d'observation
HTTP (MockMvc, contexte Spring complet, sécurité réelle + `RoleScopeFilter` + `CenterAccessGuard`, H2 `test`) sur les 3 routes de `SeanceRestController` ; exports décodés (CSV texte, XLSX via POI, PDF via PDFBox, en-têtes `Content-Type`, `Content-Disposition`, `Cache-Control`). Aucun mock du code caractérisé, aucun seam (`seamAllowed` reste vide). Harnais `Se7bCharacterizationSupport` (au-dessus de `Se7` à `Se1`), tests : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/seance/se7b/`, goldens : `backend/platform/src/test/resources/characterization/seance/se7b/`.

Indépendance de l'heure : les mois lus sont des mois passés fixes (décembre 2025, janvier et février 2026, février 2024, années 0, 1, 9999) nommés `<D+n>` ; `AbsencesDuPilotage.aujourdhui()` ne borne alors rien. Aucun test ne lit le mois courant (voir limites).

## Ce qui était déjà verrouillé (non repris)
Absences A4 : détails par type, type absent ou inconnu, mois 13 et année illisible (dashboard), centre B lu par l'ADMIN du centre A (3 routes), mois futur 2099-01 (absences), exports XLSX et PDF de mars 2026, format inconnu et casse, matrice 6 profils sur dashboard et details. PC1 : effectif selon l'état des fiches (mois courant, masqué, et 2099-12).

## Scénarios (16 tests, 16 goldens)
| Classe | Tests | Verrouille |
|---|---|---|
| `LeTableauDeBordMensuelDesSeances...` | 7 | formules sur 16 fiches (sexe `M/H/m/F/" f "/X/""`, tranches d'âge aux bornes 17/18, 39/40, 59/60, états `TRANSFERE/DECEDE/GREFFE/GUERRI/OCCASIONNEL/VACANCIER_ETRANGER/null`, sommeil avec ou sans séance, admission en cours de mois et le mois suivant) ; bornes du mois et statuts (`CREE`, `BROUILLON`, `validee` exclus, 1er et dernier jour inclus, centre B séparé) ; jours fériés et fermetures (dédoublonnage, hors mois, autre centre) ; centre vide, février 2024 bissextile, 9999, années 0 et 1 ; 20 cas de paramètres (3 routes) ; droits (14 profils + anonyme × 3 routes) ; cloisonnement |
| `LeDetailDuTableauDeBord...` | 5 | tri (jour, nom, prénom, ordre des caractères), absences hors annulées, patient d'un autre centre écarté, normalisation du `kind` (casse, espaces), bornes, absence de pagination (62 lignes), écart compteur / détail |
| `LExportDuTableauDeBord...` | 4 | CSV complet (par défaut = `csv`), XLSX et PDF (3 feuilles, texte du PDF), noms de fichier (mois sur 2 chiffres), export d'un centre vide, absence de donnée nominative ou de montant |

## Seams
Aucun demandé, aucun appliqué.

## Bugs connus
KB-SEA-62 à 67 (`known-bugs.md`) : clés `expectedByWeekday` françaises mortes, 500 au lieu de 400, compteurs ≠ détail et fuite d'un patient d'un autre centre dans les répartitions, présent et absent le même jour, détail nominatif sans pagination ouvert à la secrétaire, tri sensible à la casse. Pas de montant exposé par ces routes.

## Exécution
Voir le compte rendu : 16 tests verts, 3 exécutions consécutives de la tranche dont une en `-Dsurefire.runOrder=random`, puis suite complète `./mvnw -o test -fae`. Aucun `-Dcharacterization.approve=true` : goldens relus et écrits un par un (`Map.of` d'`absenceDetails` : clés triées dans le test, car l'ordre varie d'une JVM à l'autre).

## Fichiers protégés (`covered.json`)
`SeanceRestController` (déjà présent : journal, calendrier en SE7a, tableau de bord ici), `FinOccupation` (déjà présent). Rien d'ajouté. `AbsencesDuPilotageJdbc` (contexte Absences) est lu mais appartient au filet Absences A4.

## Limites (NON verrouillé)
- Mois courant et mois qui contient « aujourd'hui » : absences bornées au jour du centre (KB-ABS-34/35 côté A4 pour 2099-01) ; non testé ici pour rester indépendant de l'heure.
- `loadBlockedDates` avale les exceptions SQL (tables absentes) : branche non atteignable en test.
- `date_admission` et `sexe` sont `NOT NULL` en base : les branches « admission nulle » et « sexe nul » sont inatteignables par HTTP (sexe vide testé).
- `asBoolean` sur nombre, `asLocalDate` sur `java.sql.Date` : dépendent du pilote ; PostgreSQL non rejoué (H2 seulement).
- Journal d'audit, diffusions, concurrence : non concernés. Le tri à égalité complète (date, nom, prénom) n'est pas verrouillé.
- Mutation testing : aucun outil installé pour la stack ; couverture de branches non mesurée (pas de JaCoCo), évaluée à la main sur la logique de `buildSeanceDashboard`, `buildSeanceDashboardDetails`, `exportDashboard*`.
- Le test PC1 `le_tableau_de_bord_de_la_seance_compte_l_effectif...` lit le mois courant (`aujourdhui()`), masque les dates ; non modifié ici.
