# LEC-PAT : lectures de la table des patients hors Patient et couverture (ligne 14 de l'inventaire v2)

status: sous-slices (i), (ii) et (iii) livrées ; reste hors ligne 14 : SituationsDesFacturesJdbc (non isolable, § 7) | date: 2026-11-14 | ADR 0176 (i), 0177 (ii), 0178 (iii) | refactoring sur place (ADR 0024)

## 1. Inventaire (Grep des SQL citant `patients`, `assure`, `assure_patient` hors patient-couverture, `src/main/java`)

| Consommateur | Fichier | Nature | Sous-slice | État |
|---|---|---|---|---|
| Séance | `PatientsDuJourJdbc` | journal du jour : jointure séances x patients, tri nom/prénom | (i) | SOLDÉ (supprimé) |
| Séance | `PatientsDuTableauDeBordJdbc` | programmation, présents du mois, identités | (i) | SOLDÉ (supprimé) |
| Séance | `PlanningDesPatientsJdbc` | jour de dialyse, sommeil, état, dates d'un patient (scan) | (i) | SOLDÉ (supprimé) |
| Séance | `ForfaitsDeSeanceJdbc` | forfait courant d'une séance (PEC couvrant le jour), ARGENT, butoir 2027-03-31 clock-checked | (ii) | SOLDÉ (ADR 0177, entrées des deux gardes supprimées) |
| Séance | `FacturationDesSeancesJdbc` | séances à facturer, tri nom/prénom, payeur, ARGENT | (ii) | SOLDÉ (ADR 0177) |
| Séance | `SeancesRealiseesDesCentresJdbc` | séances par caisse, chaîne payeur / agence / caisse dans un GROUP BY, pilotage | (ii) | SOLDÉ (ADR 0177) |
| Séance | `LecturesDesSeancesRealiseesJdbc` | séances réalisées avec nom/prénom de la fiche, publiées à la Planification (KB-SEA-80) | (ii) | SOLDÉ (ADR 0177) |
| Séance | `HistoriqueDesSeancesJdbc` | historique : jointure, filtre, tri et pagination en SQL | (iii) | SOLDÉ (ADR 0178, ne lit plus que `seances`) |
| Pilotage | `PatientsDesCentresJdbc` | effectif de période (RG-PAT-032), sexe/naissance, patients par caisse, EPO/fer | (iii) | SOLDÉ (supprimé, ADR 0178) |
| Facturation | `SituationsDesFacturesJdbc` | jointure à six tables, entrée JOINTURE (KB-FAC-49), hors LEC-PAT | hors ligne 14 | reste, NON isolable (§ 7) |
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

## 5. Livré : (ii) argent (ADR 0177, 2026-11-14)
Production : patient-couverture : `CouvertureDesPatients.ForfaitApplicable` + `code`, `LotsDePatients.aFacturerDuCentre` / `centresPayeursDuCentre` (+ `LotsDePatientsJdbc`) ; seance : port `FichesDesPatientsDuCentre` (domaine), `ForfaitDeLaPriseEnCharge` (application), ACL `FichesDesPatientsDuCentreParLaFiche` et `ForfaitDeLaPriseEnChargeParLaCouverture`, les quatre lecteurs modifiés (plus aucun SQL sur `patients`, `prise_en_charge`, `forfait`), `SeanceConfiguration`.
Gardes : 4 entrées de `TableDuPatientCouvertureTest` et l'entrée de `TableDesForfaitsTest` (butoir 2027-03-31) supprimées, sans report ; tests verrou ; `seamAllowed` = [].
Filet ajouté AVANT (approuvé un par un depuis `.received`, sur le code d'avant) : `facturation/simulation/apercu/ordre_nom_prenom_homonymes_fiche_autre_centre_{regroupement,par_forfait}_200`. Parité : goldens existants (forfait courant, aperçu, émission, exclusion/forfait, pilotage Direction, séances lues par la Planification) inchangés, verts. Écarts : 0 ; aucun `_nouveau_code`, aucune archive, aucune entrée KB « CORRIGÉ ». Reproduits : KB-SEA-80, KB-SEA-83, KB-PAT-25, à confirmer métiers/DPO <= 2026-12-15.
`SituationsDesFacturesJdbc` (jointure à six tables dans un GROUP BY, KB-FAC-49) n'est pas touchée : entrée JOINTURE conservée.

## 6. Livré : (iii) historique des séances et effectif du Pilotage (ADR 0178, 2026-11-14)
Production : patient-couverture : `EffectifDesPatients` (port publié : `parCentre`, `profils`, `parCentrePayeur`, `prescriptionsDeLAnemie`), `EffectifDesPatientsJdbc`, `LotsDePatients.rechercherDuCentre` (+ `OrdreDeRecherche`, `PatientDeLaRecherche`) et `LotsDePatientsJdbc`, `PatientCouvertureConfiguration` (1 bean) ; seance : port `PatientsDeLHistorique` (domaine), ACL `PatientsDeLHistoriqueParLaFiche`, `HistoriqueDesSeancesJdbc` réécrit (ne lit plus que `seances`), `SeanceConfiguration` ; pilotage-direction : ACL `EffectifsParLaFiche`, `PilotageDirectionConfiguration`, `PatientsDesCentresJdbc` et `PredicatDEffectif` SUPPRIMÉS.
Gardes : 2 entrées de `TableDuPatientCouvertureTest` supprimées (sans report) + test verrou ; `seamAllowed` = [] ; aucun fichier legacy de platform modifié (seuls des tests de platform : goldens, garde de dérive de l'effectif, garde `TableDuPatientCouvertureTest`).
Filet ajouté AVANT (approuvé un par un depuis `.received`, sur le code d'avant) : `seance/se1/lecture/l_historique_departage_les_homonymes/homonymes_et_patients_hors_centre` (homonymes, transféré, décédé, code nul, prénom accentué, patient d'un autre centre, séance sans patient, pagination) ; `pilotagedirection/pd1/tableau_de_bord/l_effectif_applique_la_regle_des_eta/effectif_aux_bornes` (greffe/guérison/décès/transfert aux bornes, séjours limités, effectif nul, autre société, centre payeur d'un autre centre).
Tests : contrat `LecturesPublieesContrat` +5 tests (32 sur le double en mémoire et H2 ; PostgreSQL 16 : voir ADR), `LecturesReprisesDeLaPlateformeContrat` +2 (homonymes de création différente, texte et autre centre), `LecturesDuPilotageContrat` (11) inchangé sur l'ACL.
Parité : goldens existants inchangés, verts, y compris `SeanceHistoriqueIntegrationTest`, se1 (liste, filtres, tris, pagination), pd1, pl9, pd4 ; écarts d'effectif ou de montant : 0 ; aucun `_nouveau_code`, aucune archive `_avant_lec_pat_iii`, aucune entrée KB « CORRIGÉ ». Décision : REPRODUIT (aucun changement de comportement ; l'historique reste borné au centre comme le legacy).
Coût connu : l'historique charge les identifiants des séances retenues du centre avant de paginer (le SQL paginait en base) ; l'ordre des valeurs nulles du code est celui de la base.

## 7. Reste : `SituationsDesFacturesJdbc` (KB-FAC-49), NON isolable
La jointure externe `factures` x `patients` (numéro d'assurance, nom, prénom, centre payeur courant) est dans un `GROUP BY` ; le tri `date_facturation DESC, numero_facture DESC` n'a pas de départage (KB-FAC-49 reproduit). Retirer la jointure change le plan d'exécution et donc l'ordre des factures de même date et de même numéro : le résultat peut changer, la consigne l'interdit. Entrée conservée dans `TableDuPatientCouvertureTest` (butoir 2027-03-31, aucun report). Ce qu'il faudrait : (a) décision métiers de départager par identifiant de facture (corrige KB-FAC-49 : `*_nouveau_code`, archive, KB « CORRIGÉ »), (b) lecture publiée `LotsDePatients` bornée au centre (numéro d'assurance, nom, prénom, centre payeur) appliquée en mémoire à la page, comme l'annuaire des payeurs l'est déjà, (c) golden d'homonymes et de fiche d'un autre centre avant le changement.
