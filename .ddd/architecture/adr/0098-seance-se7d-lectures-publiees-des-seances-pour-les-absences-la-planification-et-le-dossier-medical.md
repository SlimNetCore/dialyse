---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance, absences-patients, planification, dossier-medical
related: ADR 0024 (refactoring sur place), 0096, 0097 ; `.ddd/migration/slices/seance-se7d-lectures-absences-planification-dossier.md`, `.ddd/safety-net/seance-se7d.md`, KB-SEA-77 à 82, KB-MED-03
---

# 0098 - Séance, SE7d : lectures publiées de la table des séances pour les Absences de patients, la Planification et le Dossier médical

Décisions prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**. Lecture seule : aucune écriture, aucune table, aucune colonne, aucune migration, aucun événement. Les huit requêtes sont reprises À L'IDENTIQUE (prédicats, casse, tri, centre, NULL) : AUCUN écart de comportement n'est décidé par cette slice.

## Points à faire confirmer (en tête)
| KB | Comportement | Décision de cet ADR | Confirmation attendue |
|---|---|---|---|
| KB-SEA-77 | Ordre non défini entre patients d'une même date (semaine des absences, `ORDER BY date_seance` seul) | REPRODUIRE : le port publié ne promet que l'ordre des dates | Métier (Absences) : faut-il un ordre stable (patient) ? |
| KB-SEA-78 | Planification : deux séances réalisées du même patient le même jour, ordre non défini, la grille garde la première lue | REPRODUIRE : même tri (date seule) | Métier (Planification) : départage explicite ? |
| KB-SEA-79 | Dossier médical : création nulle classée en DERNIER sur H2 (filet) et en PREMIER sur PostgreSQL 16 (production) | REPRODUIRE : même `ORDER BY ... created_at DESC` sans `NULLS` ; le contrat attend chaque SGBD tel qu'il est | DBA : compter les créations nulles (§63) ; métier : ordre voulu |
| KB-SEA-80 | Planification : une séance réalisée est invisible si la fiche du patient n'est pas dans le centre de la séance (jointure sur l'identifiant ET le centre) | REPRODUIRE : même jointure, dans l'adapter de la Séance | Métier : cas du transfert de patient (§63) |
| KB-SEA-81 | Annulation d'absence : le lien d'exclusion bloque quel que soit le statut de la séance, et seulement dans le centre | REPRODUIRE : même comptage sans statut | Métier (Absences) ; détection §63 |
| KB-SEA-82 | Casse et blancs du statut : `IN ('VALIDEE','SIGNEE','FACTUREE')` exact pour les cinq lecteurs | REPRODUIRE (comme KB-SEA-73) | Décision commune avec C2-R3 (contrainte ou normalisation) |
| DPO | Le port des constantes publie des mesures cliniques (poids, tension, débit, ultrafiltration, durée) ; le port de la Planification publie le nom et le prénom des patients | Aucune donnée nouvelle : mêmes colonnes, mêmes routes, mêmes rôles (constantes : MEDECIN, ADMIN ; semaine : 4 rôles) | DPO : le passage par un langage publié ne change ni les destinataires ni la finalité |

## Contexte
Cinq classes hors de la Séance lisent `seances` en SQL direct (8 requêtes, audit `.ddd/safety-net/seance-se7d.md`) : Absences `SeancesRealiseesJdbc` (A1 semaine, A2 jours d'un patient, A3 séance réalisée un jour), `ExclusionsDeSeancesJdbc` (A4 lien d'exclusion), `PatientsAttendusJdbc` (A5 patients écartés, plus 3 requêtes sur d'autres tables qui restent) ; Planification `SeancesRealiseesJdbc` (P1, jointure `patients`) ; Dossier médical `HistoriqueDesConstantesJdbc` (D1 total, D2 page avec `volet_paramedical`). Filet vert sur le legacy avant tout code (commit e662b0d9) : 12 goldens SE7d, filets A1 à A4, P6, P8, DM1, contrats des modules, rejeu PostgreSQL 16 `seance-se7d-pg16-rejeu.sh`.

## Décisions
1. **Quatre ports publiés** dans `contexts/seance/publishedlanguage` (`seance.v1`), types du JDK seulement :
   - `SeancesRealiseesPubliees` : A1 `joursRealisesDuCentre` (une ligne par patient et par jour, dates croissantes, ordre intra-jour NON défini), A2 `joursRealisesDuPatient` (jours distincts croissants), A3 `estRealisee`, A5 `patientsRealisesLe` (doublons possibles, aucun ordre) ;
   - `LiensDExclusionPublies` : A4 `absenceLiee(centre, absence)` (sans prédicat de statut) ;
   - `SeancesRealiseesAvecLeurPlacePubliees` : P1 `duCentre` (place figée, nom et prénom BRUTS de la fiche ; la recomposition « prénom nom » reste à la Planification) ;
   - `ConstantesDesSeancesPubliees` : D1 `nombreDeSeances`, D2 `page(centre, patient, taille, rang)` (mesures brutes du volet paramédical du même centre, nulles sans volet).
2. **Adapters JDBC dans `contexts/seance/infrastructure/persistence`** : `LecturesDesSeancesRealiseesJdbc` (A1 à A5, P1) et `ConstantesDesSeancesJdbc` (D1, D2), requêtes mot pour mot. La jointure `patients` de P1 est reproduite dans l'adapter de la Séance (l'entrée de `TableDuPatientCouvertureTest` y est déplacée, même échéance) ; `volet_paramedical` appartient à la Séance (glossaire : volet paramédical). Aucune transaction ouverte, lecture seule, même `JdbcTemplate`.
3. **Dépendances Maven (vérifiées dans les `pom.xml` le 2026-11-08)** :
   - Absences de patients : `contexts-seance` dépend de `contexts-absences-patients` ⇒ cycle ; la Séance ne peut pas être importée ;
   - Planification : `contexts-seance` dépend de `contexts-patient-couverture` (qui dépend de `contexts-planification`) et de `contexts-absences-patients` (qui en dépend aussi) ⇒ cycle ;
   - Dossier médical : aucun module dont dépend la Séance (directement ou transitivement : shared-kernel, patient-couverture, stock, absences-patients, comptabilité, facturation, planification, gmao) ne dépend de `contexts-dossier-medical` ⇒ PAS de cycle.
   
   Donc, comme SE7c2 (ADR 0097, décision 4) : les Absences déclarent `SeancesRealiseesFournies` et `LiensDExclusionFournis`, la Planification `SeancesRealiseesAvecLeurPlaceFournies`, dans leur propre `publishedlanguage` (types simples) ; la plateforme les implémente (`platform/infrastructure/acl/absences/SeancesRealiseesParLaSeance`, `LiensDExclusionParLaSeance`, `platform/infrastructure/acl/planification/SeancesRealiseesAvecLeurPlaceParLaSeance`, délégation pure) ; les ACL internes `infrastructure/acl/seance/*Publiee(s)` traduisent vers les ports des consommateurs. Le Dossier médical dépend DIRECTEMENT de `contexts-seance` (son langage publié uniquement, règle déjà présente dans son `ArchitectureHexagonaleTest`) : ACL `dossiermedical/infrastructure/acl/seance/HistoriqueDesConstantesPublie`, aucun fournisseur dans la plateforme.
4. **Traductions reproduites dans les ACL des consommateurs** : nom de la Planification `(prénom ou "" + " " + nom ou "").trim()` (branche nulle inatteignable, KB-SEA-80), place nulle conservée, patients à risque lus par `RisquesInfectieuxJdbc` AVANT les séances (ordre des lectures inchangé) ; Absences : ensemble des patients écartés construit en Java comme avant ; Dossier médical : total lu AVANT la page, `Constantes` reconstruites des 7 mesures.
5. **Jour du centre** : aucun « aujourd'hui » n'est lu par ces ports (bornes fournies par l'appelant). Si un jour l'est : `HorlogeDuCentre`.
6. **Verrous** : `TableDesSeancesTest` perd les 5 entrées (17 vers 12 lecteurs, compte asserté) ; `FrontieresDesContextesTest` : les Absences et la Planification ne dépendent d'AUCUNE classe de `contexts.seance` ; le Dossier médical ne dépend que de `contexts.seance.publishedlanguage` ; aucun SQL sur `seances` dans les 3 traducteurs de la plateforme.
7. **Tests** : un contrat unique des 4 ports (`SeancesRealiseesEtConstantesContrat`) joué par un double en mémoire, H2 et PostgreSQL 16 jetable (ports 55xxx, mot de passe en variable d'environnement, jamais 5432) ; il reproduit chaque requête, y compris l'ordre indéfini (KB-SEA-77, 78 : ensembles et ordre des dates seulement), les NULL selon le SGBD (KB-SEA-79 : nulle en dernier sur H2, en premier sur PG16), la casse (KB-SEA-82), le centre (KB-SEA-80, 81) et l'ordre des UUID en octets non signés ; traducteurs testés table-driven ; parité par TOUS les goldens SE7d, A1 à A4, P6, P8, DM1 inchangés.
8. **Aucun événement d'intégration**, aucune table, aucune colonne, aucune migration.

## Parité attendue
Tous les goldens `characterization/seance/se7d/**` et ceux des modules concernés inchangés et verts. Aucun écart décidé : un golden qui change arrête la slice.

## Annexe : requêtes de détection (SELECT seule, reprises en section 63 de `verifications-production.md`)
```sql
-- KB-SEA-79 : séances à création nulle (ordre de l'historique des constantes différent entre H2 et PostgreSQL).
SELECT center_id, count(*) FROM seances WHERE created_at IS NULL GROUP BY center_id;
-- KB-SEA-81 : lien d'exclusion porté par une séance qui n'est pas ABSENT.
SELECT center_id, statut, count(*) FROM seances WHERE exclusion_absence_id IS NOT NULL AND statut <> 'ABSENT' GROUP BY center_id, statut;
-- KB-SEA-82 : statuts réalisés hors casse canonique ou avec blancs.
SELECT center_id, statut, count(*) FROM seances WHERE upper(trim(statut)) IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND statut NOT IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, statut;
-- KB-SEA-80 : séances réalisées dont la fiche patient n'est pas dans le centre de la séance (invisibles dans la semaine du planning).
SELECT s.center_id, count(*) FROM seances s LEFT JOIN patients p ON p.id = s.patient_id AND p.center_id = s.center_id WHERE s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') AND p.id IS NULL GROUP BY s.center_id;
-- KB-SEA-78 : deux séances réalisées ou plus d'un même patient le même jour.
SELECT center_id, patient_id, date_seance, count(*) FROM seances WHERE statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') GROUP BY center_id, patient_id, date_seance HAVING count(*) > 1 LIMIT 100;
```

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre pour chacun)
Liste identique à celle de la fiche (`.ddd/migration/slices/seance-se7d-lectures-absences-planification-dossier.md`, section « Résidus nominatifs »), qui fait foi.

| # | Résidu | Revue | Butoir |
|---|---|---|---|
| D-R1 | KB-SEA-77, 78 : ordre non défini (semaine des absences ; deux séances réalisées le même jour au planning) | 2026-12-15 | 2027-03-31 |
| D-R2 | KB-SEA-79 : création nulle ordonnée différemment par H2 (golden) et PG16 (production, rejeu seulement) | 2026-12-15 | 2027-03-31 |
| D-R3 | KB-SEA-80 : jointure au centre de la fiche ; `LecturesDesSeancesRealiseesJdbc` lit `patients` (entrée de `TableDuPatientCouvertureTest`) : à remplacer par un langage publié du Patient | 2026-12-15 | 2027-03-31 |
| D-R4 | KB-SEA-81, 82 : lien d'exclusion sans statut ; casse et blancs du statut (avec C2-R3) | 2026-12-15 | 2027-03-31 |
| D-R5 | 3 langages `*Fournies` et 3 traducteurs `*ParLaSeance` dans la plateforme (cycle Maven) ; tests des Absences joués au-dessus du double `SeancesRealiseesSurLaTable` | 2026-12-15 | 2027-06-30 |
| D-R6 | **PARTIEL : P1 vu par l'optimisation** (`POST /api/v1/planning/optimisations`, `/{id}/application`, empreinte KB-PLN-95) : en HTTP, seul le statut PLANIFIEE est rejoué (P8) ; une séance RÉALISÉE ajoutée après le calcul n'est observée que par le test de domaine `EmpreinteEtIndicateursDOptimisationTest`. Le port de la semaine est le même (`planificationSeancesRealisees`), la requête est prouvée par le contrat, mais l'appelant optimisation n'a pas de golden de bout en bout | 2026-12-15 | 2027-03-31 |
| D-R7 | Concurrence (lecture pendant une validation, une exclusion) non rejouée ; couverture de branches et mutation non mesurées (PIT non lancé) | 2026-12-15 | 2027-03-31 |
| D-R8 | **SOLDÉ (décision utilisateur, 2026-11-08, revue m1)**. Le HTML `docs/client/documentation-fonctionnelle.html:1889` cite la même source que `06-absences-patients.md:193-194` : l'ACL `ExclusionsDeSeancesPubliees` (`absencespatients/infrastructure/acl/seance`, câblée par `AbsencesPatientsContexteConfiguration:170`), et non plus `LiensDExclusionFournis`. Le PDF et le DOCX, ainsi que RG-SEA-062 à 064, suivront la prochaine régénération ordinaire (`docs/client/tools`), sans résidu ouvert | — | — |

## Bilan (lots A à D, 2026-11-08, commits `e662b0d9` filet et `f812dff0` lots A à D)
- Ports publiés et adapters livrés tels que décidés ; contrat `SeancesRealiseesEtConstantesContrat` 14 cas (mémoire 14/14, H2 14/14, PG16 15/15 avec la vérification de version) ; rejeu `seance-se7d-pg16-rejeu.sh` code 0.
- Supprimés : Absences `legacyseance/SeancesRealiseesJdbc`, `legacyseance/ExclusionsDeSeancesJdbc` ; Planification `legacyseance/SeancesRealiseesJdbc` ; Dossier médical `persistence/HistoriqueDesConstantesJdbc`. Édité : `legacyplanning/PatientsAttendusJdbc` (sa requête `seances` passe par `SeancesRealiseesFournies` ; ses trois autres requêtes, sur d'autres tables, restent). `TableDesSeancesTest` : 12 lecteurs, 0 hors plateforme.
- Parité : les 12 goldens SE7d et tous les goldens des modules inchangés (aucun fichier `.approved.json` modifié), aucun écart. Détail, correspondance des tests retirés et résidus nominatifs : `.ddd/migration/slices/seance-se7d-lectures-absences-planification-dossier.md`.

## Lots
LOT A : cet ADR, ports publiés. LOT B : adapters de la Séance, contrat mémoire / H2 / PG16. LOT C : langages fournis, traducteurs, ACL internes, câblage, suppression du SQL des consommateurs, seam, verrous. LOT D : RG, annexe 16, glossaires, §63, fiche, état `built`.
