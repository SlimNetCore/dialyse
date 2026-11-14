# LEC-PAT-GMAO : lecteurs `gmao_equipements` de Patient et couverture et `SituationsDesFacturesJdbc` (lignes 14 et 15 de l'inventaire v2)

status: livrée (deux lecteurs GMAO puis KB-FAC-49) ; LIGNES 14 ET 15 SOLDÉES | date: 2026-11-14 | ADR 0181 | refactoring sur place (ADR 0024)

## 1. Inventaire résiduel avant la slice

| Lecteur | Table lue | Garde (entrée) | État |
|---|---|---|---|
| Patient et couverture `GenerateursDuCentreJdbc` | `gmao_equipements` | `TableDesEquipementsDeLaGmaoTest` | SOLDÉ (supprimé, remplacé par l'ACL `GenerateursDuCentreParLaGmao`) |
| Patient et couverture `LectureDesMouvementsJdbc` | `gmao_equipements` (jointure sans borne) | `TableDesEquipementsDeLaGmaoTest` | SOLDÉ (plus de jointure) |
| Facturation `SituationsDesFacturesJdbc` | `patients` (jointure dans un GROUP BY) | `TableDuPatientCouvertureTest` (entrée JOINTURE, KB-FAC-49) | SOLDÉ |

Après la slice : `TableDesEquipementsDeLaGmaoTest` liste blanche VIDE ; `TableDuPatientCouvertureTest` ne garde que PLACE et CYCLE (propriété de la Planification, hors périmètre).

## 2. Décisions de l'utilisateur (2026-11-14), appliquées
1. Journal des mouvements : générateur lu par gmao.v1, BORNÉ AU CENTRE (changement de comportement).
2. Port gmao.v1 dédié décrivant AUSSI les équipements supprimés, lecture par lot.
3. KB-FAC-49 : tri départagé par l'identifiant de la facture ; fiche du patient par une lecture publiée bornée au centre, en mémoire ; golden de fiche d'un autre centre AVANT le changement ; tout écart de montant = STOP (aucun).

## 3. Livré (12 fichiers de production touchés, 1 supprimé, 1 ACL créée)
- **gmao** (4) : `DescriptionsDesEquipements` (publishedlanguage), `DescriptionsDesEquipementsJpa`, requête `descriptionsDuCentre` de `EquipementSpringData`, `GmaoConfiguration` (1 bean).
- **patient-couverture** (5) : ACL `acl/gmao/GenerateursDuCentreParLaGmao` (créée) ; `GenerateursDuCentreJdbc` SUPPRIMÉ ; `LectureDesMouvementsJdbc` ; `PatientCouvertureConfiguration` ; `pom.xml` (dépendance `contexts-gmao`).
- **facturation** (2) : `SituationsDesFacturesJdbc` (GROUP BY réduit, `LotsDePatients`, lots de 500, COALESCE reproduits en mémoire) ; `FacturationContexteConfiguration`. Aucune nouvelle API publiée : `LotsDePatients.aFacturerDuCentre` (ADR 0177) est réutilisé.
- Gardes : 2 entrées supprimées de `TableDesEquipementsDeLaGmaoTest` (+ test verrou `patient_et_couverture_ne_lit_plus…`) ; entrée JOINTURE supprimée de `TableDuPatientCouvertureTest` (+ test verrou). `seamAllowed` = [], aucun fichier legacy de platform modifié (tests de platform seulement).

## 4. Tests
- Contrat triple gmao `PilotageContrat` +1 cas (double en mémoire, JPA/H2, PostgreSQL 16 IT ignoré sans URL jetable) : lot, supprimés compris, autre centre et inconnu absents, valeurs nulles ignorées, lot vide.
- Patient et couverture : `EquipementsEnMemoire` (double borné), `GenerateursDuCentreParLaGmaoTest` (3 : traduction, supprimé décrit, autre centre/inconnu absents), `PatientsJdbcContrat` +1 (code du générateur : du centre, supprimé, autre centre, inconnu, nul ; H2 et PG16), table `gmao_equipements` retirée du schéma de test (preuve qu'aucun SQL ne la lit).
- Facturation : `ReglementsJdbcContrat` +1 (fiche du centre / autre centre / inconnu, par la liste et par la facture ; H2 et PG16), schéma H2 de test aligné.

## 5. Parité et écarts
Goldens ajoutés AVANT le changement, approuvés un par un depuis `.received` (jamais `-Dcharacterization.approve=true`) :
- `pc1/mouvements/le_generateur_d_un_mouvement_est_dec/generateur_d_un_autre_centre` ; `pc1/consultation/la_fiche_d_un_patient_dont_le_genera/fiche_generateur_d_un_autre_centre` et `fiche_generateur_inconnu` (la fiche est DÉJÀ bornée : parité exacte) ; `facturation/reglements/recherche/patient_d_un_autre_centre_ou_inconnu` et `..._compact`.

Écart unique, décidé : `generateur_d_un_autre_centre` (« G-AUTRE-CENTRE » devient `null`) : golden `generateur_d_un_autre_centre_nouveau_code.approved.json`, archive `pc1/_ecarts-legacy/mouvements/le_generateur_d_un_mouvement_est_dec/generateur_d_un_autre_centre_avant_lec_pat_gmao.legacy.json`, KB-PAT-13 « CORRIGÉ (2026-11-14) » dans `known-bugs.md`, référentiel RG-PAT-035 mis à jour. Tous les autres goldens inchangés byte-à-byte (mouvements, fiche supprimé et hors service, règlements, tableau de bord et export, situation d'une facture). Écart de montant : 0.

## 6. Constat KB-FAC-49 : le départage n'a aucun effet observable
`factures` porte `UNIQUE (center_id, numero_facture)` (`schema.sql:1281`, V1) et la lecture est bornée à un centre : deux factures de même date ET de même numéro ne coexistent pas, le tri d'avant était déjà total. Le golden d'égalité demandé est donc impossible à construire (la base refuse la seconde facture) ; remplacé par le test `la_base_refuse_deux_factures_de_meme_numero…` qui verrouille ce fait. L'identifiant est un départage défensif. Confirmation métiers/DPO <= 2026-12-15. Le départage des règlements (`created_at`) de KB-FAC-49 reste REPRODUIT, hors périmètre.

## 7. Limites et suites
- La fiche est lue par lots de 500 patients distincts à chaque appel de la liste (avant : jointure SQL) ; aucun effet sur le résultat, coût mesurable seulement sur de très grandes périodes.
- Plus aucun lecteur SQL de `gmao_equipements` ni de `patients` dans Facturation ; restent les lecteurs PLACE et CYCLE de la Planification (ADR 0052, hors ligne 14).
- Échecs connus non touchés : `InventairePl7...feuille_de_comptage` (date), parfois `LancementEtSuivi...`.
