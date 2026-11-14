# LEC-PLN-GMAO : lectures de la Séance sur la Planification, la GMAO et le Dossier médical (ligne 15 de l'inventaire v2)

status: sous-slices (a) et (b) livrées ; (c) poids sec NON livrée (budget de fichiers + cycle Maven, plan précis § 4) | date: 2026-11-14 | ADR 0179 | refactoring sur place (ADR 0024)

## 1. Inventaire (Grep des SQL de `platform` et des ACL sur les tables de planification, GMAO et dossier médical)

| Lecteur | Table | Garde (entrée) | État |
|---|---|---|---|
| Séance `PlaceDuPatientJdbc` | `deplacement_temporaire` | `TableDeLaPlanificationTest` (seule entrée, butoir 2027-03-31) | SOLDÉ (supprimé, liste blanche vide) |
| Séance `GenerateursDesPatientsJdbc` | `gmao_equipements` | `TableDesEquipementsDeLaGmaoTest` (butoir 2027-03-31) | SOLDÉ (supprimé) |
| Séance `StatistiquesDesSeancesJdbc` | `prescriptions_medicales` (poids sec) | `TableDuDossierMedicalTest` (butoir 2027-03-31) | RESTE : (c), plan § 4 |
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

## 4. Reste : (c) poids sec de `StatistiquesDesSeancesJdbc`
Piège : `contexts-dossier-medical` dépend de `contexts-seance` ; la Séance ne peut pas importer dossier-medical.v1 (cycle). `StatistiquesMedicalesPubliees.poidsSecCibleEnVigueur` existe mais départage différemment (`updated_at DESC` sans `NULLS LAST`, sans identifiant) : ne pas l'utiliser. Plan (ADR 0179 § 4) : (i) dossier-medical.v1 `poidsSecsCiblesPrescrits(centre, patient)` en `BigDecimal` trié `date_prescription DESC, updated_at DESC NULLS LAST, id DESC` ; (ii) port de la Séance `PoidsSecDesPrescriptions` ; (iii) `StatistiquesDesSeancesJdbc` sans sous-requête, première prescription <= jour appliquée en mémoire, ordre des clés conservé ; (iv) ACL dans `dossier-medical/infrastructure/acl/seance` câblée par `IndicateursEtStatistiquesConfiguration` ; (v) contrat triple (égalité de date, `updated_at` nul) ; (vi) supprimer l'entrée de `TableDuDossierMedicalTest` (`hasSize(7)` -> 6, `containsExactly`). Filet : goldens PL4/DM8 (statistiques paramédicales) existants ; ajouter un golden « deux prescriptions de même date » AVANT. ~6 fichiers de production.
