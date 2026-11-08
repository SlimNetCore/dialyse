---
slice: seance-se7b-tableau-de-bord-mensuel
status: planned
date: 2026-11-08
order: 7.2
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md]
---

# SE7b : tableau de bord mensuel des séances (dashboard, détails, exports CSV/PDF/XLSX)

`GET /api/v1/seances/dashboard`, `/dashboard/details?kind=`, `/dashboard/export?format=` (ADMIN INFIRMIER MEDECIN SECRETAIRE) : `SeanceRestController` lignes 279 à 740 (4 requêtes SQL sur `seances`, jointures `patients`, `volet_paramedical`, absences via `AbsencesDuPilotage`, consommations Stock). Code le plus volumineux du résidu (environ 460 lignes de logique d'agrégation et de génération de PDF/XLSX).

Filet existant : Absences A4 (`ExportsEtPilotageDesAbsencesA4`, `LecturesDeAbsencesParLaDirectionEtLaSeance`) couvre détails par type, exports csv/pdf/xlsx, format invalide, centre autre ; Patient-couverture PC1 (`LecteursDeLaFichePatient`) couvre deux mois. À auditer avant de caractériser : tranches d'âge, répartition par caisse/situation, mois sans séance, mois futur, statuts (`VALIDEE`, `SIGNEE`, `FACTUREE`), cloisonnement. Risque : moyen (agrégats lus par la Direction), taille : une classe de test par famille de calcul.

Cible : lecteur publié `seance.v1` (read model) ; la partie Absences reste appelée via `AbsencesDuPilotage`.
