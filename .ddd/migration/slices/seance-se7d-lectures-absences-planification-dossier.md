---
slice: seance-se7d-lectures-absences-planification-dossier
status: built
date: 2026-11-08
order: 7.4
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [ADR 0098, .ddd/safety-net/seance-se7d.md, .ddd/safety-net/known-bugs.md (KB-SEA-77 à 82), .ddd/migration/verifications-production.md §63, .ddd/safety-net/seams.md (SE7d lot C)]
---

# SE7d : lectures d'Absences patients, Planification et Dossier médical

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URL, aucun flag, aucune migration, aucune écriture. Décisions : ADR 0098 (accepted, à confirmer métiers / DPO au plus tard le 2026-12-15). Audit des 8 requêtes : `.ddd/safety-net/seance-se7d.md`.

## Avant SE7d (rappel)
| Fichier (supprimé ou édité) | Requêtes | Nature |
|---|---|---|
| `contexts/absences-patients/.../acl/legacyseance/SeancesRealiseesJdbc.java` (supprimé) | A1, A2, A3 | semaine, jours d'un patient, séance réalisée un jour |
| `contexts/absences-patients/.../acl/legacyseance/ExclusionsDeSeancesJdbc.java` (supprimé) | A4 | lien d'exclusion |
| `contexts/absences-patients/.../acl/legacyplanning/PatientsAttendusJdbc.java` (édité) | A5 (+ 3 requêtes sur d'autres tables, conservées) | patients écartés de la détection |
| `contexts/planification/.../acl/legacyseance/SeancesRealiseesJdbc.java` (supprimé) | P1 (jointure `patients`) | séances réalisées de la semaine |
| `contexts/dossier-medical/.../persistence/HistoriqueDesConstantesJdbc.java` (supprimé) | D1, D2 (jointure `volet_paramedical`) | historique des constantes |

## Lots livrés (commits `e662b0d9` filet, `f812dff0` lots A à D)
| Lot | Contenu |
|---|---|
| Filet | `e662b0d9` : 12 goldens SE7d, rejeu PG16, KB-SEA-77 à 82 ; vert sur le legacy avant tout code |
| A | ADR 0098 ; 4 ports publiés `seance.v1` : `SeancesRealiseesPubliees`, `LiensDExclusionPublies`, `SeancesRealiseesAvecLeurPlacePubliees`, `ConstantesDesSeancesPubliees` (types JDK) |
| B | `contexts/seance/.../persistence/LecturesDesSeancesRealiseesJdbc` (A1 à A5, P1) et `ConstantesDesSeancesJdbc` (D1, D2), SQL mot pour mot, beans dans `SeanceConfiguration` ; contrat `SeancesRealiseesEtConstantesContrat` (14 cas) joué par `SeancesRealiseesEtConstantesEnMemoire`, H2 (`...JdbcContratTest`) et PG16 (`...PostgresContractIT`, rejoué par `seance-se7d-pg16-rejeu.sh`) |
| C | Langages fournis `absencespatients/publishedlanguage/SeancesRealiseesFournies`, `LiensDExclusionFournis`, `planification/publishedlanguage/SeancesRealiseesAvecLeurPlaceFournies` ; traducteurs de plateforme `infrastructure/acl/absences/SeancesRealiseesParLaSeance`, `LiensDExclusionParLaSeance`, `infrastructure/acl/planification/SeancesRealiseesAvecLeurPlaceParLaSeance` (seam ouvert puis fermé, chemins exacts) ; ACL internes `absencespatients/infrastructure/acl/seance/SeancesRealiseesPubliees`, `ExclusionsDeSeancesPubliees`, `planification/infrastructure/acl/seance/SeancesRealiseesPubliees` ; Dossier médical : dépendance Maven directe `contexts-seance`, ACL `dossiermedical/infrastructure/acl/seance/HistoriqueDesConstantesPublie` ; câblage des 3 configurations ; verrous (`TableDesSeancesTest` 17 vers 12, `TableDuPatientCouvertureTest` entrée déplacée, 3 règles ArchUnit dans `FrontieresDesContextesTest`) |
| D | RG-SEA-062, 063, 064 (`07-seances.md`), source de RG-ABS (exclusion) corrigée (`06-absences-patients.md`), annexe 16 ; glossaires `proposed` (séance, absences-patients, planification, dossier-medical) ; §63 ; known-bugs ; cette fiche ; état `built` |

## Choix Maven / `*Fournies` (vérifiés dans les `pom.xml` le 2026-11-08)
| Consommateur | Cycle ? | Choix |
|---|---|---|
| Absences de patients | oui : `contexts-seance` -> `contexts-absences-patients` | `*Fournies` + traducteurs de plateforme `*ParLaSeance` (comme SE7c2) |
| Planification | oui : `contexts-seance` -> `contexts-patient-couverture` -> `contexts-planification` (et -> absences -> planification) | `*Fournies` + traducteur de plateforme |
| Dossier médical | non : aucun module dont dépend la Séance ne dépend du Dossier médical | dépendance directe sur `seance.publishedlanguage` (aucun fournisseur de plateforme) ; règle ArchUnit : rien d'autre de la Séance |

## Parité
| Élément | Résultat |
|---|---|
| Goldens SE7d (12) | Inchangés et verts, 3 exécutions (la 3e en `-Dsurefire.runOrder=random`, graine 186462648573800) |
| Goldens des modules (A1 à A4, P6, P8, DM1) et tous les autres | Inchangés et verts dans la suite complète ; aucun `.approved.json` modifié, aucun `.received` |
| Écarts | AUCUN (aucun écart décidé ; KB-SEA-77 à 82 reproduits) |
| Flag / bascule | Aucun (ADR 0024) ; rollback = retour au commit précédent ; contrôle SELECT seule §63 |

## Correspondance des cas de tests retirés ou déplacés
| Cas retiré | Où il est joué maintenant |
|---|---|
| `absences/.../infrastructure/ExclusionsDeSeancesJdbcTest.une_absence_liee_a_une_seance_exclue_du_centre_vient_d_une_exclusion` (H2, table minimale : lié, libre, autre centre) | Contrat Séance `should_see_an_absence_linked_to_an_exclusion_whatever_the_status_of_the_seance_but_only_in_its_centre` (mémoire, H2, PG16 ; ajoute le statut VALIDEE, KB-SEA-81) ; traduction : `absencespatients/.../acl/seance/TraducteursDesSeancesTest` ; golden SE7d `annulation_et_lien_d_exclusion` |
| `planification/.../LecturesDeLaSemaineJdbcContrat.should_read_the_realised_sessions_of_the_period_with_their_frozen_place_and_ignore_the_others` (H2, PG16) | Contrat Séance, MÊME NOM (mémoire, H2, PG16 ; ajoute la casse `validee`) ; traduction (nom, place, risque, ordre des lectures) : `planification/.../acl/seance/SeancesRealiseesPublieesTest` ; goldens P6 et SE7d |
| `absences/.../LecturesDuSuiviJdbcContrat` (3 cas A1 à A3), `PatientsAttendusJdbcTest` (6 + 1 cas de séance), `DetectionPostgresContractIT` | Conservés : l'ACL de production au-dessus du double `SeancesRealiseesSurLaTable` (lit la table semée par ces tests ; même principe que `SeancesDesFacturesSurLaTable`, SE7c2). Le vrai adapter est prouvé par le contrat Séance et par les goldens câblés en production |
| `dossier-medical/.../HistoriqueDesConstantesJdbcContrat` (9 cas, H2, PG16) | Conservé, joué désormais par l'ACL de production `HistoriqueDesConstantesPublie` au-dessus du VRAI `ConstantesDesSeancesJdbc` de la Séance (dépendance directe) ; PG16 rejoué par `seance-se7d-pg16-rejeu.sh` (9/9) |

## Exécutions (2026-11-08)
- Contrat Séance : mémoire 14/14, H2 14/14 ; PG16 15/15 (avec la version) dans `seance-se7d-pg16-rejeu.sh 55548` (2e passage ; le 1er avait échoué sur une collision de clé primaire : les UUID d'égalité du contrat reprenaient ceux du jeu du script, corrigés dans le test, aucun code de production touché).
- Rejeu PG16 : 19 vérifications OK (14 sur les huit requêtes, 5 détections §63), `HistoriqueDesConstantesPostgresContractIT` 9/9, code 0.
- Tests ciblés des modules (absences-patients, planification, dossier-medical, seance, platform : verrous, traducteurs) : verts.
- Suite complète `./mvnw -o test -fae` (depuis `backend/`, un build à la fois) : DEUX exécutions, toutes deux BUILD SUCCESS. La 1re a été relancée une fois parce que le filtre de sortie avait écarté les lignes de résumé « Tests run » ; la 2e donne, d'après les résumés Maven des 15 modules testés, **7 667 tests, 0 échec, 0 erreur, 10 ignorés** (shared-kernel 22, bc-patient 18, stock 154, bc-seance 51, bc-medical 32, gmao 751, comptabilité 310, planification 743, patient-couverture 203, facturation 479 dont 1 ignoré, absences-patients 395, organisation 77, seance 698, dossier-medical 595, platform 3 139 dont 9 ignorés). Avant SE7d : 7 609. Les XML Surefire des `target/` ne sont pas sommés (rapports périmés).
- `check-arch` : 0 violation.
- Suites de la revue (`.ddd/verify/verify-seance-se7d.md`, section « Suites données ») : une règle ArchUnit est ajoutée au Dossier médical (m4) et la documentation est corrigée. Suite complète : **7 668 tests, 0 échec, 0 erreur, 10 ignorés** (lancée deux fois, la 1re ayant perdu ses lignes de résumé au filtrage) ; `check-arch` : 0.

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre)
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

## Reste à faire
Confirmation métiers / DPO de l'ADR 0098 (au plus tard 2026-12-15) ; exécuter les requêtes §63 sur une copie de production ; résidus D-R1 à D-R8 ; `/ddd-verify seance-se7d-lectures-absences-planification-dossier`.
