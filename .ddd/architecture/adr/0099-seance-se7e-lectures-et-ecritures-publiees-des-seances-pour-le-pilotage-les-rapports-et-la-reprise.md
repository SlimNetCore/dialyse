---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance, plateforme (pilotage de la Direction, statistiques du patient, cahier de dialyse, reprise de données, référentiel d'administration)
related: ADR 0024 (refactoring sur place), 0096, 0097, 0098 ; `.ddd/migration/slices/seance-se7e-lectures-pilotage-et-rapports.md`, `.ddd/safety-net/seance-se7e.md`, KB-SEA-83 à 92
---

# 0099 - Séance, SE7e : lectures et écritures publiées de la table des séances pour le pilotage, les rapports et la reprise

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Aucune table, aucune colonne, aucune migration, aucun événement. Les seize requêtes Java (E1 à E10, E12 à E17 de l'audit) sont reprises À L'IDENTIQUE (prédicats, casse, tri, centre, NULL, libellés) ; la dix-septième (E11, état Jasper) reste dans le modèle de document (décision 3). AUCUN écart de comportement n'est décidé par cette slice.

## Points à faire confirmer (en tête)
| KB | Comportement | Décision de cet ADR | Confirmation attendue |
|---|---|---|---|
| KB-SEA-83 | Vue d'ensemble et taux d'absentéisme comptent toutes les séances réalisées ; la répartition par caisse ne compte que celles dont la fiche est au centre de la séance | REPRODUIRE : deux méthodes du même port, `parCentre` et `parCaisse`, chacune avec sa requête | Métier (Direction) : un seul prédicat pour les trois vues ? Détection §63.4 |
| KB-SEA-84 | Chaîne payeur, agence, caisse sans contrôle de centre ; ordre des caisses à chiffre égal non défini | REPRODUIRE (ordre du SGBD publié tel quel) | Métier ; détection §64.2 |
| KB-SEA-85 | Statistiques paramédicales : tous statuts, volet joint sans le centre | REPRODUIRE | Métier : séances réalisées seulement ? Détection §64.3 |
| KB-SEA-86 | Ordre intra-jour non défini ; clés JSON de `poidsEvolution` = libellés du SGBD (MAJUSCULES sur H2, minuscules sur PostgreSQL) | REPRODUIRE : le port rend les lignes brutes du pilote (`List<Map<String, Object>>`) ; le contrat attend chaque SGBD tel qu'il est | Front : quelle casse lit-il ? Détection §64.4 |
| KB-SEA-87 | Précision de `AVG` : 12 décimales sur H2, 16 sur PostgreSQL 16 | REPRODUIRE (valeur `Double` du pilote, nulle rendue `0.0` par la plateforme) | Métier : arrondi voulu ? |
| KB-SEA-88 | Cahier : forfait imposé sans code imprimé vide ; casse exacte du statut | REPRODUIRE (compte par le port, contenu par l'état Jasper inchangé) | Détection §64.5 |
| KB-SEA-89 | Reprise : une séance déjà présente de n'importe quel statut empêche l'import | REPRODUIRE | Métier (reprise) |
| KB-SEA-90 | Annulation d'un lot bloquée par une séance d'un autre centre ; facture cherchée sans centre | REPRODUIRE | Détection §64.6 |
| KB-SEA-91 | Forfait « utilisé » par une séance de n'importe quel centre ou statut | REPRODUIRE | Détection §64.7 |
| KB-SEA-92 | Garde aveugle aux noms de table calculés | CORRIGÉ dans l'OUTILLAGE (garde de test, aucun code de production) : décision 6 | — |
| DPO | `StatistiquesDesSeancesPubliees` publie des mesures cliniques (poids, tension, ultrafiltration, poids sec) | Aucune donnée nouvelle : mêmes colonnes, mêmes routes, mêmes rôles (ADMIN, INFIRMIER, MEDECIN, SECRETAIRE ; export ADMIN) | DPO : le passage par un langage publié ne change ni les destinataires ni la finalité |

## Contexte
Après SE7d, la plateforme gardait douze lecteurs de `seances` (allow-list de `TableDesSeancesTest`) et deux lecteurs invisibles (noms de table calculés, KB-SEA-92). Audit des 17 requêtes : `.ddd/safety-net/seance-se7e.md`. Filet vert sur le legacy avant tout code (commit `b46f81dd`, vérifié de nouveau le 2026-11-08 : 15/15) : 15 goldens SE7e, F5, A1, DM1, DM8, tests d'intégration du pilotage, du cahier, de la reprise et du référentiel, rejeu PostgreSQL 16 `seance-se7e-pg16-rejeu.sh`.

## Décisions
1. **Cinq ports publiés** dans `contexts/seance/publishedlanguage` (`seance.v1`), types du JDK seulement :
   - `SeancesRealiseesDesCentresPubliees` : E1/E5 `parCentre`, E2 `auTotal`, E3 `parMois` (`MoisRealisePublie`), E4 `parCaisse` (`CaisseRealiseePubliee`) ;
   - `StatistiquesDesSeancesPubliees` : E6 `nombreDeSeances`, E7 `moyenneDuPoidsAvant`, `moyenneDuPoidsApres`, `moyenneDeLUltrafiltration`, E8 `evolutionDuPoids` (lignes brutes du SGBD, KB-SEA-86), E9 `evolutionDeLaTension` (`TensionsDeSeancePubliees`, tension brute ; le découpage systolique / diastolique reste à la plateforme) ;
   - `SeancesDuCahierPubliees` : E10 `nombreDeSeancesAImprimer` (bornes fournies par l'appelant, ses défauts 1900-01-01 / 2999-12-31 inchangés) ;
   - `ForfaitsImposesAuxSeancesPublies` : E17 `nombreDeSeancesQuiImposentLeForfait` ;
   - `SeancesDeLaReprisePubliees` (ÉCRITURES) : E12 `seanceDejaPresente`, E13 `reprendre`, `modifier` (inatteignable aujourd'hui, conservé pour l'équivalence), E14 `seancesDuPatient`, E15 `factureDeLaSeance`, E16 `retirer` ; constante `NATURE = "seances"` (libellé observable des bloquants d'annulation, golden `reprise/*`, et clé des lignes créées par le lot).
2. **Adapters JDBC dans `contexts/seance/infrastructure/persistence`** : `SeancesRealiseesDesCentresJdbc` (E1 à E5), `StatistiquesDesSeancesJdbc` (E6 à E9, dates de période en littéral ISO comme avant, valeurs `LocalDate`), `SeancesDuCahierEtForfaitsImposesJdbc` (E10, E17), `SeancesDeLaRepriseJdbc` (E12 à E16 : même liste blanche de champs `patientId`, `dateSeance`, `statut`, même message `Champ non autorisé pour seances : …`, même date de création, centre du lot sur la recherche, la modification et la suppression, aucun centre sur E14 et E15). Aucune transaction ouverte : même `JdbcTemplate`, participation à la transaction de l'appelant (import, annulation). La jointure `patients` / `centre_payeur` / `agence` / `caisse_assurance` de E4 et la sous-requête `prescriptions_medicales` de E8 sont reproduites dans l'adapter de la Séance (entrée de `TableDuPatientCouvertureTest` ajoutée, résidu E-R2).
3. **Cahier de dialyse : l'état Jasper GARDE sa requête** (solution la plus sûre). Raisons vérifiées dans le code : le modèle `CAHIER_DIALYSE` est PERSONNALISABLE par centre (`ModeleDocumentPrinter.render` prend la version active de `modele_document_version` si elle existe, `JrxmlSecurityValidator` : « une seule requête ») ; une source de données issue d'un port ne servirait que le modèle d'origine, pas les versions personnalisées des centres, qui garderaient leur propre SQL ; elle changerait le contrat des modèles (paramètres, champs) et EXCEL / HTML ne sont pas rejoués (limite du filet). Donc : `cahier_dialyse.jrxml` reste un lecteur NOMMÉ de `TableDesSeancesTest`, justifié, butoir propre 2027-06-30 (résidu E-R3) ; mise en page et rendu inchangés ; seul le refus `CAHIER_VIDE` passe par `SeancesDuCahierPubliees`. Les versions personnalisées actives sont détectées par §64.8.
4. **Dépendances Maven (vérifiées dans `platform/pom.xml` le 2026-11-08)** : la plateforme dépend directement de `contexts-seance` ; la Séance ne dépend pas de la plateforme ⇒ AUCUN cycle. Les consommateurs (`DirectionDashboardQueryService`, `DirectionBreakdownQueryService`, `DirectionAbsencesQueryService`, `PatientStatsQueryService`, `CahierDialyseReportService`, `HistoricalRecordJdbcAdapter`, `MigrationRollbackJdbcAdapter`, `ReferentialAdminJdbcAdapter` + `ReferentialAdminTables`) reçoivent les ports par injection ; aucun fournisseur `*Fournies`, aucun traducteur de plateforme.
5. **Traductions reproduites chez les consommateurs** : ordre des lectures inchangé (fiche, séances, finances ; compte, moyennes, évolutions, poids sec courant), `0.0` pour une moyenne nulle, `java.sql.Date` rétabli dans `taEvolution`, place du comptage publié inchangée dans la liste des usages d'un forfait (3e sur 4), même interception `DataAccessException` (contrôle ignoré) et même passage en minuscules des identifiants à l'annulation d'un lot ; les séances sortent du descripteur `TABLES` de `HistoricalRecordJdbcAdapter` (la liste blanche est reprise dans la Séance).
6. **Garde (KB-SEA-92)** : technique partagée `architecture/NomsDeTableCalcules` (test) : un littéral exact `"seances"` (ou `"public.seances"`) est un lecteur s'il est dans un fichier qui construit une requête (`"FROM " +`, `new StringBuilder("FROM ").append(`), ou si un fichier qui construit une requête nomme sa classe. Faux positif nommé et justifié : `MigrationEntity.java` (code d'URL `/import/seances`, n'atteint aucune requête). `TableDesSeancesTest` : 12 vers **6** entrées (2 fournisseurs `seance.v1` et 3 scripts de données, tous de SE7f ; l'état Jasper, justifié), compte asserté, entrées mortes et faux positifs morts détectés, bac de détection des noms calculés et indirects.
7. **Verrous ArchUnit** (`FrontieresDesContextesTest`) : chaque consommateur dépend de SON port publié (5 règles positives) ; `SeancesDeLaReprisePubliees` (seul port d'écriture et de suppression hors de la Séance) n'est utilisé que par `HistoricalRecordJdbcAdapter` et `MigrationRollbackJdbcAdapter`.
8. **Tests** : un contrat unique des 5 ports (`PilotageEtRapportsDesSeancesContrat`, 13 cas) joué par un double en mémoire, H2 et PostgreSQL 16 jetable (port 55549, mot de passe en variable d'environnement, jamais 5432) : casse (KB-SEA-83, 88), fiche au centre (KB-SEA-83), caisse sans centre et ordre du SGBD (KB-SEA-84), tous statuts et volet d'un autre centre (KB-SEA-85), libellés du SGBD et ordre intra-jour (KB-SEA-86), décimales du SGBD (KB-SEA-87), séance déjà présente de tout statut (KB-SEA-89), séances et facture sans centre (KB-SEA-90), forfait de tout centre (KB-SEA-91), liste blanche des champs, gardes de centre de la modification et de la suppression. Parité : TOUS les goldens SE7e et ceux des modules inchangés.
9. **Jour du centre** : aucun « aujourd'hui » n'est lu par ces ports. Les périodes par défaut (`LocalDate.now()` de la Direction) restent chez l'appelant, inchangées.

## Parité attendue
Tous les goldens `characterization/seance/se7e/**`, F5, A1, DM1, DM8 et ceux des modules inchangés et verts. Aucun écart décidé : un golden qui change arrête la slice.

## Annexe : requêtes de détection (SELECT seule, reprises en section 64 de `verifications-production.md`, toutes rejouées par `seance-se7e-pg16-rejeu.sh`)
```sql
-- 64.1 Migrations Flyway qui créent ou modifient la table des séances (V1, V21, V23, V26, V39) et son index (V31).
SELECT version, success FROM flyway_schema_history WHERE version IN ('1', '21', '23', '26', '31', '39') ORDER BY installed_rank;
-- 64.2 KB-SEA-84 : fiches rattachées à la caisse d'un autre centre.
SELECT p.center_id, ca.center_id AS centre_de_la_caisse, count(*) FROM patients p JOIN centre_payeur cp ON cp.id = p.centre_payeur_id JOIN agence ag ON ag.id = cp.agence_id JOIN caisse_assurance ca ON ca.id = ag.caisse_id WHERE ca.center_id <> p.center_id GROUP BY p.center_id, ca.center_id;
-- 64.3 KB-SEA-85 : volets paramédicaux posés dans un autre centre que leur séance.
SELECT s.center_id, vp.center_id AS centre_du_volet, count(*) FROM volet_paramedical vp JOIN seances s ON s.id = vp.seance_id WHERE vp.center_id <> s.center_id GROUP BY s.center_id, vp.center_id;
-- 64.4 KB-SEA-86 : plusieurs séances d'un patient le même jour, tous statuts.
SELECT center_id, patient_id, date_seance, count(*) FROM seances GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1 LIMIT 100;
-- 64.5 KB-SEA-88 : forfait imposé sans code recopié.
SELECT center_id, count(*) FROM seances WHERE forfait_override_id IS NOT NULL AND (forfait_override_code IS NULL OR trim(forfait_override_code) = '') GROUP BY center_id;
-- 64.6 KB-SEA-90 : patients dont les séances sont dans plusieurs centres.
SELECT patient_id, count(DISTINCT center_id) FROM seances GROUP BY patient_id HAVING count(DISTINCT center_id) > 1 LIMIT 100;
-- 64.7 KB-SEA-91 : forfait imposé à une séance d'un autre centre que le forfait.
SELECT s.center_id, f.center_id AS centre_du_forfait, count(*) FROM seances s JOIN forfait f ON f.id = s.forfait_override_id WHERE f.center_id <> s.center_id GROUP BY s.center_id, f.center_id;
-- 64.8 E-R3 : versions personnalisées actives du cahier de dialyse (requête embarquée hors du dépôt).
SELECT md.center_id, mdv.version, position('seances' in lower(mdv.contenu)) > 0 AS lit_seances FROM modele_document_version mdv JOIN modele_document md ON md.id = mdv.modele_id WHERE md.code = 'CAHIER_DIALYSE' AND mdv.actif = TRUE ORDER BY md.center_id;
```

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre pour chacun)
Liste identique à celle de la fiche (`.ddd/migration/slices/seance-se7e-lectures-pilotage-et-rapports.md`, section « Résidus nominatifs »), qui fait foi.

| # | Résidu | Revue | Butoir |
|---|---|---|---|
| E-R1 | KB-SEA-83 à 87 : deux comptes de séances de la Direction, caisse sans centre, statistiques de tous statuts avec le volet d'un autre centre, libellés et précision du SGBD (décisions métier) | 2026-12-15 | 2027-03-31 |
| E-R2 | `SeancesRealiseesDesCentresJdbc` lit `patients`, `centre_payeur`, `agence`, `caisse_assurance` (entrée de `TableDuPatientCouvertureTest`) et `StatistiquesDesSeancesJdbc` lit `prescriptions_medicales` (poids sec) : à remplacer par des langages publiés du Patient et du Dossier médical | 2026-12-15 | 2027-03-31 |
| E-R3 | `cahier_dialyse.jrxml` garde sa requête sur `seances` (modèle personnalisable, décision 3) ; versions personnalisées actives des centres hors du dépôt (§64.8) | 2026-12-15 | 2027-06-30 |
| E-R4 | `SeancesDeLaReprisePubliees` est un port d'ÉCRITURE générique (carte de champs) hors de l'agrégat `Seance` : à remplacer par un cas d'usage « reprendre une séance historique » de la Séance | 2026-12-15 | 2027-06-30 |
| E-R5 | KB-SEA-89 à 91 (reprise et référentiel sans centre ni statut) : décisions métier | 2026-12-15 | 2027-03-31 |
| E-R6 | `MigrationRollbackJdbcAdapter` lit encore `volet_medical` et `volet_paramedical` (tables de la Séance) en SQL direct pour « complétée » ; `TableDesSeancesTest` ne garde que `seances` | 2026-12-15 | 2027-03-31 |
| E-R7 | Technique `NomsDeTableCalcules` appliquée à `TableDesSeancesTest` seulement (les autres gardes gardent leur détection `new Table/Usage("…")`) ; faux positif nommé `MigrationEntity.java` | 2026-12-15 | 2027-03-31 |
| E-R8 | Non rejoués : instantanés et diffusion temps réel de la Direction (période par défaut = aujourd'hui), cahier EXCEL / HTML, sérialisation JSON de `poidsEvolution` sur PostgreSQL, concurrence (import pendant une validation) ; couverture de branches et mutation non mesurées | 2026-12-15 | 2027-03-31 |

## Lots
LOT A : cet ADR, ports publiés. LOT B : adapters de la Séance, contrat mémoire / H2 / PG16. LOT C : bascule des consommateurs de plateforme, seam (chemins exacts), verrous (garde durcie, ArchUnit, patient-couverture). LOT D : RG-SEA-065 à 068, annexe 16, glossaires, §64, fiche, état `built`.
