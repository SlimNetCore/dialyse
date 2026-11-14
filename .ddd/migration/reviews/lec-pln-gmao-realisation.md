# LEC-PLN-GMAO : lectures de la Séance sur la Planification, la GMAO et le Dossier médical (ligne 15 de l'inventaire v2)

status: sous-slices (a), (b) et (c) livrées ; lecteurs `gmao_equipements` de Patient et couverture RESTENT (§ 5) | date: 2026-11-14 | ADR 0179, 0180 | refactoring sur place (ADR 0024)

## 1. Inventaire (Grep des SQL de `platform` et des ACL sur les tables de planification, GMAO et dossier médical)

| Lecteur | Table | Garde (entrée) | État |
|---|---|---|---|
| Séance `PlaceDuPatientJdbc` | `deplacement_temporaire` | `TableDeLaPlanificationTest` (seule entrée, butoir 2027-03-31) | SOLDÉ (supprimé, liste blanche vide) |
| Séance `GenerateursDesPatientsJdbc` | `gmao_equipements` | `TableDesEquipementsDeLaGmaoTest` (butoir 2027-03-31) | SOLDÉ (supprimé) |
| Séance `StatistiquesDesSeancesJdbc` | `prescriptions_medicales` (poids sec) | `TableDuDossierMedicalTest` (butoir 2027-03-31) | SOLDÉ par (c) (ADR 0180, § 4) |
| Patient et couverture `GenerateursDuCentreJdbc`, `LectureDesMouvementsJdbc` | `gmao_equipements` | `TableDesEquipementsDeLaGmaoTest` (entrées datées 2026-11-07, revue 2026-12-15) | RESTENT, hors périmètre demandé (ACL de Patient et couverture : `GenerateursDuCentre` publié existe déjà) |
| `documents` `cahier_dialyse.jrxml`, `ordonnance.jrxml` ; `schema.sql`, `seed.sql`, démo | tables du Dossier médical | `TableDuDossierMedicalTest` | RESTENT (états Jasper : ligne DOC ; DDL/seed : S-DDL) |
| `platform` | aucune lecture SQL de ces trois contextes | - | néant |

## 2. Livré (11 fichiers de production touchés, 2 supprimés)
- (a) planification : `DeplacementsDuJourDesPatients` (publishedlanguage), `ConsulterLesDeplacementsDuJour` (application, au-dessus du dépôt `DeplacementsTemporairesRepository`), `PlanificationConfiguration` (1 bean) ; seance : ACL `acl/planification/PlaceDuPatientParLaPlanification`, `PlaceDuPatientJdbc` SUPPRIMÉ.
- (b) gmao : `FichesDesGenerateurs` (publishedlanguage), `FichesDesGenerateursJpa`, requête `ficheDuCentre` de `EquipementSpringData`, `GmaoConfiguration` (1 bean) ; seance : ACL `acl/gmao/GenerateursDesPatientsParLaGmao`, `GenerateursDesPatientsJdbc` SUPPRIMÉ ; `SeanceConfiguration` ; `pom.xml` (dépendance explicite `contexts-gmao`).
- Gardes : entrée unique de `TableDeLaPlanificationTest` supprimée (liste blanche VIDE + test verrou) ; entrée de `TableDesEquipementsDeLaGmaoTest` supprimée + test verrou. `seamAllowed` = [].
- Tests : gmao `PilotageContrat` +1 cas (double en mémoire, JPA sur H2, PostgreSQL 16 IT : ignoré sans `-D` d'URL jetable) ; `ConsulterLesDeplacementsDuJourTest` (2) ; Séance `LecturesReprisesDeLaPlateformeContrat` : le déplacement passe par l'adapter JDBC réel de la Planification (H2 ; PG16 IT idem), schéma aligné sur la table réelle (NOT NULL), +1 test ACL (déplacement incomplet), générateur = test de traduction de l'ACL (l'adapter réel est couvert côté GMAO).

## 3. Parité
Goldens existants du consommateur inchangés et verts (pas de `.received`) : `LecturesDePlaceConsommeesParLaSeance` (6), validation SE2 (16), déplacements et séances réalisées, `PatientGenerateurIntegrationTest`, `FrontieresDesContextesTest` (89), ArchUnit des 3 contextes. Aucun golden ajouté (comportement observable identique, aucun nouveau scénario). Écarts : 0 ; aucun `_nouveau_code`, aucune archive `_avant_lec_pln_gmao`, aucune entrée KB « CORRIGÉ ». Reproduit : générateur non filtré par type d'équipement ; ordre non défini si plusieurs déplacements (la base garantit l'unicité centre/patient/date).
Échec connu non touché : `InventairePl7...feuille_de_comptage` (date).

## 4. (c) poids sec de `StatistiquesDesSeancesJdbc` : LIVRÉE (ADR 0180)
Production (7 fichiers) : dossier-medical `PoidsSecsPrescritsPublies` (dossier-medical.v1), `PoidsSecsPrescritsJdbc`, ACL `PoidsSecsPourLaSeance`, `IndicateursEtStatistiquesConfiguration` (2 beans) ; seance `PoidsSecDesPrescriptions` (seance.v1, dans `publishedlanguage` et non `application` : l'ACL du Dossier médical doit le voir), `StatistiquesDesSeancesJdbc` (sans sous-requête : liste lue une fois, première prescription <= jour appliquée en mémoire, ordre des clés et des séances conservé), `SeanceConfiguration`. Pas d'usage de `poidsSecCibleEnVigueur`.
Tests : golden AJOUTÉ AVANT le changement (`pl4_paramedical/a_date_de_prescription_egale_le_poid/egalite_de_date`, approuvé depuis `.received` après relecture, vert avant et après), contrat triple `PoidsSecsPrescritsContrat` (double, H2 ; PG16 IT ignoré sans `-Ddossiermedical.pg16.url`) 4 cas, `PoidsSecsPourLaSeanceTest`, Séance `PilotageEtRapportsDesSeancesJdbcContrat` (double du port, table des prescriptions retirée du schéma de test). `TableDuDossierMedicalTest` : `hasSize(6)`, `containsExactly` (2 états Jasper), test verrou. Écarts de parité : 0 ; aucun `_nouveau_code`, aucune archive, aucune entrée KB « CORRIGÉ ».
Constat reproduit, à confirmer métiers/DPO <= 2026-12-15 : l'en-tête `poidsSecCibleKg` (`poidsSecCibleEnVigueur`) n'a pas d'identifiant dans son tri ; à égalité stricte il peut différer de la courbe (clé retirée du golden d'égalité).

## 5. Lecteurs `gmao_equipements` de Patient et couverture : SOLDÉS ensuite par LEC-PAT-GMAO (2026-11-14, ADR 0181, `lec-pat-gmao-realisation.md`) ; texte d'origine ci-dessous
Estimation > 12 fichiers de production (port gmao.v1 + JPA + Spring Data + configuration + deux adapters + câblage + pom + contrats) et non neutre : `FichesDesGenerateurs` exclut les équipements supprimés alors que `GenerateursDuCentreJdbc` les décrit encore ; `LectureDesMouvementsJdbc` joint `gmao_equipements` sans borne de centre dans une requête paginée. Il faut un port dédié (supprimés inclus, lecture par lot) et une décision sur la borne de centre. Entrées de `TableDesEquipementsDeLaGmaoTest` conservées (revue 2026-12-15), aucun report. Sous-slice suivante : LEC-PAT-GMAO.
