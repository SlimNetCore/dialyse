# LEC-PAT : lectures de la table des patients hors Patient et couverture (ligne 14 de l'inventaire v2)

status: sous-slice (i) livrée, (ii) et (iii) non commencées | date: 2026-11-14 | ADR 0176 | refactoring sur place (ADR 0024)

## 1. Inventaire (Grep des SQL citant `patients`, `assure`, `assure_patient` hors patient-couverture, `src/main/java`)

| Consommateur | Fichier | Nature | Sous-slice | État |
|---|---|---|---|---|
| Séance | `PatientsDuJourJdbc` | journal du jour : jointure séances x patients, tri nom/prénom | (i) | SOLDÉ (supprimé) |
| Séance | `PatientsDuTableauDeBordJdbc` | programmation, présents du mois, identités | (i) | SOLDÉ (supprimé) |
| Séance | `PlanningDesPatientsJdbc` | jour de dialyse, sommeil, état, dates d'un patient (scan) | (i) | SOLDÉ (supprimé) |
| Séance | `ForfaitsDeSeanceJdbc` | forfait courant d'une séance (PEC couvrant le jour), ARGENT, butoir 2027-03-31 clock-checked | (ii) | à faire |
| Séance | `FacturationDesSeancesJdbc` | séances à facturer, tri nom/prénom, payeur, ARGENT | (ii) | à faire |
| Séance | `SeancesRealiseesDesCentresJdbc` | séances par caisse, chaîne payeur / agence / caisse dans un GROUP BY, pilotage | (ii) | à faire |
| Séance | `LecturesDesSeancesRealiseesJdbc` | séances réalisées avec nom/prénom de la fiche, publiées à la Planification (KB-SEA-80) | (ii)/(iii) | à faire |
| Séance | `HistoriqueDesSeancesJdbc` | historique : jointure, filtre, tri et pagination en SQL | (iii) | à faire |
| Pilotage | `PatientsDesCentresJdbc` | effectif de période (RG-PAT-032), sexe/naissance, patients par caisse, EPO/fer | (iii) | à faire |
| Facturation | `SituationsDesFacturesJdbc` | jointure à six tables, entrée JOINTURE (KB-FAC-49), hors LEC-PAT | hors ligne 14 | reste |
| Planification | `Places*`, `Sejours*`, `Noms*`, `Contraintes*`, `Patients*`, `Mouvements*`, `LectureDesPreferencesJdbc` (10 fichiers) | PLACE, CYCLE Maven, lecture de fiche : lignes 15 et ADR 0052 | hors ligne 14 | restent |
| Patient et couverture | `DonneesDeLaRepriseJdbc` etc. | propriétaire | - | n/a |

Aucun lecteur SQL de `assure` ou `assure_patient` hors patient-couverture (la reprise passe par `RepriseDesAssures`, ADR 0171).

## 2. Livré : (i) lot de patients / programmation d'un mois (ADR 0176)
Production (9 fichiers modifiés ou créés, 3 supprimés) :
- patient-couverture : `ProgrammationDesPatients`, `LotsDePatients` (publishedlanguage) ; `ProgrammationPublieeJdbc`, `LotsDePatientsJdbc` (persistence) ; `PatientCouvertureConfiguration` (2 beans).
- seance : `PatientsDuJourParLaFiche`, `PatientsDuTableauDeBordParLaFiche`, `PlanningDesPatientsParLaFiche` (acl/patientcouverture) ; `SeanceConfiguration` ; 3 classes `acl/legacypatient/*Jdbc` supprimées.
Tests : contrat des deux ports (5 tests x double en mémoire, H2, PostgreSQL 16) ; tests ACL H2 réécrits (journal +1 homonymes) ; contrat des lectures reprises (planning) conservé ; 2 goldens HTTP ajoutés AVANT le changement.
Gardes : 3 entrées de `TableDuPatientCouvertureTest` supprimées (sans report) + test verrou ; `ENTITES`/seamAllowed inchangés (`seamAllowed` = []).

## 3. Parité et filet
- Goldens existants inchangés : journal (3 tests), tableau, détail, export, scan, scan jour férié, droits : tous verts sans modification.
- Nouveaux goldens approuvés un par un depuis `.received` sur le code d'avant : `seance/se7/journal/le_journal_depart_les_homonymes_par_/homonymes_et_patients_sortis` (homonymes, transféré, patient d'un autre centre, prénom vide) ; `seance/se7b/tableau/les_presents_d_un_autre_centre_compt/presents_d_un_autre_centre` (présent d'un autre centre compté, transféré, endormi, présent compté une fois).
- Écarts de parité : 0. Aucun `_nouveau_code`, aucune archive `_avant_lec_pat`, aucune entrée KB « CORRIGÉ ».

## 4. Décisions
- KB-SEA-61 (journal : patient d'un autre centre rendu) et KB-SEA-64 (tableau : patient d'un autre centre compté présent) : REPRODUITS, à confirmer métiers/DPO <= 2026-12-15 (lectures par lot non bornées au centre, documentées dans le port).
- Tri : rang des couples (nom, prénom) calculé côté Patient et couverture ; départage par identifiant de séance côté Séance (ordre de la base de la Séance), équivalent à `ORDER BY nom, prenom, s.id`.
- Ordre de `duCentre` non garanti (comme le legacy sans `ORDER BY`).

## 5. Reste (non commencé, périmètre dépasserait ~12 fichiers de production)
- (ii) argent : ports `CouvertureDesPatients` existants à étendre pour `ForfaitsDeSeanceJdbc` (forfait courant, déjà proche de `forfaitApplicable`) ; `FacturationDesSeancesJdbc` (tri nom/prénom + payeur : réutiliser `LotsDePatients.parNomPuisPrenom` + lecture du payeur de la fiche) ; `SeancesRealiseesDesCentresJdbc` (caisse de la fiche : lecture publiée « caisse du patient » + chaîne payeur déjà publiée par `PayeursDuCentre`). Goldens byte-à-byte (facturation, pilotage), approbation un par un.
- (iii) `HistoriqueDesSeancesJdbc` : filtre, tri, pagination sur patient (nom, code, état) : exige un port « recherche de patients » rendant un ensemble ordonné d'identifiants, la pagination restant côté Séance ou déléguée selon ADR ; `PatientsDesCentresJdbc` : effectif (prédicat RG-PAT-032 déjà publié par `CompteursDeCouverture.patientsPresentsLe`), sexe/naissance par centre, patients par caisse, prescriptions EPO/fer (jointure avec les données médicales : à scinder avec Dossier médical).
- Butoir 2027-03-31 conservé pour ces entrées ; aucun report décidé.
