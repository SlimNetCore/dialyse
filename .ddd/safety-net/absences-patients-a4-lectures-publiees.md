---
status: approved
date: 2026-10-23
sources: SeanceRestController, DirectionAbsencesQueryService, filets A1 (lectures-publiees)
---
# Filet A4 - lectures publiees de `absence_patient`

> **2026-11-08, SE7b lot B (ADR 0095)** : `SeanceRestController` est supprime ; les routes `dashboard`, `dashboard/details` et `dashboard/export` sont servies par `contexts/seance`. 4 goldens de ce filet changent, tous KB-SEA-63 (500 devient 400), legacy archive en R100 sous `absences-patients/consultation/_ecarts-legacy/lectures-publiees-a4/*_avant_se7b.legacy.json` et golden recree en `*_nouveau_code` : `tableau_de_bord_mois_13`, `tableau_de_bord_sans_centre`, `tableau_de_bord_annee_illisible`, `details_type_absent`. Tous les autres goldens (details par type, exports csv/pdf/xlsx, format invalide, centre autre, matrice de profils, mois futur) sont inchanges.

## Requetes a remplacer par le port `AbsencesDuPilotage` (hors module, grep exhaustif de `absence_patient` dans les `src/main`)
| # | Fichier:lignes | Requete | Verrouille par |
|---|---|---|---|
| 1 | `DirectionAbsencesQueryService.java:121-137` | `GROUP BY center_id, statut, motif` (COUNT, SUM montant_ht, SUM prix_ttc), `statut <> 'ANNULEE'`, bornes incluses | A1 `direction_absences_*_nouveau_code` + A4 `direction_societe_*`, `direction_controle_de_la_periode` |
| 2 | `DirectionAbsencesQueryService.java:173-186` | mensuel (annee, mois, COUNT, SUM ht), statuts A_QUALIFIER/JUSTIFIEE/NON_JUSTIFIEE, centres publies seulement | A1 `direction_absences_fevrier_avril` (mois Fev/Mars/Avril) |
| 3 | `SeanceRestController.java:801-809` | `COUNT(1)` du mois, `statut <> 'ANNULEE'` (dashboard, exports CSV/PDF/XLSX) | A1 `tableau_de_bord_*`, `export_csv` + A4 `export_xlsx_mars`, `export_pdf_mars`, `export_formats`, `tableau_de_bord_mois_futur` |
| 4 | `SeanceRestController.java:977-989` (source :982-983) | lignes (patient_id, date_seance, statut) du mois, patients inconnus ecartes cote Java | A1 `details_*` + A4 `details_absences_mois_futur` |
`/seances/calendar` (`:697`) ne lit pas la table (jours feries/fermetures) : hors perimetre. Aucun autre lecteur (JPA, rapports) : `PatientsAttendusJdbc` et les adapters sont deja dans le contexte.

## Scenarios ajoutes (7 tests, 9 goldens) - `ExportsEtPilotageDesAbsencesA4CharacterizationIntegrationTest`
XLSX (feuilles lues via POI) et PDF (texte via PDFBox) du dashboard ; formats d'export ; mois futur (2099-01, deterministe) ; isolation entre societes, centre inactif publie, societe sans centre ; controle de periode Direction (inversee, 5 ans pile, 5 ans+1j, date illisible). Goldens : `src/test/resources/characterization/absences-patients/consultation/lectures-publiees-a4/`. Harnais : support A1 (alias de dates nommes, ancre mars 2026, aucun mock).

## Seam (accorde par l'utilisateur le 23/10, inscrit dans `seams.md` et `seamAllowed`)
Les 2 fichiers sont deja dans `covered` (A1) ; l'edition a ete accordee par l'utilisateur.
- `SeanceRestController` : ajouter un parametre de constructeur `AbsencesDuPilotage` ; remplacer :801-809 par `absences.nombreHorsAnnulees(centerId, from, to)` et :982-989 (branche absence) par `absences.horsAnnulees(centerId, from, to)` -> (patientId, dateSeance, statut). Reste du mapping (patients, tri) inchange.
- `DirectionAbsencesQueryService` : parametre de constructeur `AbsencesDuPilotage` ; :121-137 -> `cumulParCentre(ids, from, to)` ; :173-186 -> `mensuel(idsPublies, from, to)` (memes mois/ordre).
- Port publie (`contexts/absences-patients/publishedlanguage`) + adapter JDBC dans le contexte reprenant a l'identique les 4 SQL.

## Limites (non verrouille)
Dashboard du mois en cours (depend de `periodEnd`/jours attendus du jour ; la borne « aujourd'hui » sur les absences est couverte par `BorneDesAbsencesDuTableauDeBordTest` a port simule) ; contenu binaire exact PDF/XLSX (seul le texte/cellules l'est) ; valeurs `expectedSeances` avec programmation patients (hors absences) ; mutation testing non disponible sur la stack (pas de PIT configure) ; couverture de branches non mesuree (pas de JaCoCo configure).

## Stabilite
3 executions vertes (1 ordre par defaut, 2 `MethodOrderer$Random` + `ClassOrderer$Random`), 7/7 + 9/9 (A1 lectures), 0 flaky. KB-ABS-33 a 36.
