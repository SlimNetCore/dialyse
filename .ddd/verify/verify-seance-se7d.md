---
status: proposed
date: 2026-11-08
scope: slice seance-se7d-lectures-absences-planification-dossier
sources: [git diff e662b0d9^..f812dff0, ADR 0024, ADR 0098, .ddd/safety-net/seance-se7d.md, .ddd/migration/slices/seance-se7d-lectures-absences-planification-dossier.md, .ddd/migration/verifications-production.md §63, backend/scripts/seance-se7d-pg16-rejeu.sh]
reviewer: relecteur indépendant (lecture seule ; ni Maven, ni Docker, ni scan.ts)
---

# Revue SE7d : lectures des séances par les Absences, la Planification et le Dossier médical

## Verdict : **pass-with-actions**

Aucun blocker et aucun major. 0 blocker · 0 major · 4 minor · 4 nit.

## Contrôles automatiques et vérifications faites
| Contrôle | Résultat |
|---|---|
| `check-arch.ts` | 0 violation (0 en baseline, 0 nouvelle) |
| Goldens entre `e662b0d9` et `f812dff0` | `git diff --stat e662b0d9..f812dff0` : aucun `.approved.json` modifié, et les 4 classes `characterization/seance/se7d/*` ne changent pas non plus. Les tests de caractérisation récupèrent les ports applicatifs par `@Autowired` (`SeancesRealisees`, `ExclusionsDeSeances`, `PatientsAttendus`, Planification `SeancesRealisees`) : les goldens passent donc par le nouveau câblage de production. |
| Les 8 requêtes, comparées à `git show e662b0d9:` | A1 à A3 (`LecturesDesSeancesRealiseesJdbc.java:35-52`), A4 (`:65`), A5 (`:57-58`), P1 (`:74-77`), D1 et D2 (`ConstantesDesSeancesJdbc.java:102-111`) sont identiques caractère pour caractère : prédicats, casse, `DISTINCT`, `ORDER BY`, jointure au centre, `LIMIT/OFFSET`, colonnes et paramètres (`Date.valueOf`, et pour A5 le même `java.sql.Date` qu'avant). |
| KB-SEA-77 à 82 | Tous reproduits, aucun corrigé. Pas de départage ajouté (77, 78), pas de `NULLS` (79), jointure `p.center_id = s.center_id` conservée (80), pas de statut pour A4 (81), `IN` en casse exacte (82). |
| Traducteurs | Ceux de la plateforme (`SeancesRealiseesParLaSeance`, `LiensDExclusionParLaSeance`, `SeancesRealiseesAvecLeurPlaceParLaSeance`) ne font que déléguer. `stream().map().toList()` garde l'ordre et les doublons. Dans les ACL internes, le nom est recomposé par `nom(prenom, nom)` comme avant, les patients à risque sont lus AVANT les séances (`planification/.../acl/seance/SeancesRealiseesPubliees.java`), et le total des constantes est lu AVANT la page (`HistoriqueDesConstantesPublie`). |
| Cycle Maven | Aucun. `contexts-seance` dépend de shared-kernel, patient-couverture, stock et absences-patients, et par transitivité de comptabilité, facturation, planification et gmao. Aucun de ces modules ne dépend de `contexts-dossier-medical`. |
| Le code de production du Dossier médical n'importe de la Séance que `publishedlanguage` | Oui : `DossierMedicalConfiguration.java:195` et `HistoriqueDesConstantesPublie.java:10-11`. C'est verrouillé par `FrontieresDesContextesTest` (nouvelle règle) et par `ArchitectureHexagonaleTest:73`. |
| Les interfaces `*Fournies` | Elles ne contiennent que des types JDK (UUID, LocalDate, List, records), sont en lecture seule et ne dépendent ni de la Séance ni du legacy. |
| `TableDesSeancesTest` | `hasSize(12)`, 0 entrée `contexts/`, 12 entrées `platform/`, avec deux nouveaux tests : 0 SQL dans les 3 consommateurs, 0 SQL dans les 3 traducteurs. |
| `TableDuPatientCouvertureTest` | L'entrée est déplacée vers `LecturesDesSeancesRealiseesJdbc`, avec une date (2026-11-08), une justification (KB-SEA-80, D-R3) et des échéances (revue 2026-12-15, butoir 2027-03-31). |
| Couverture des tests retirés | Le cas `ExclusionsDeSeancesJdbcTest` (lié, libre, autre centre) est repris dans `SeancesRealiseesEtConstantesContrat.should_see_an_absence_linked_..._only_in_its_centre`. Ce contrat joue les deux adapters de production (`SeancesRealiseesEtConstantesJdbcContrat.java:48-63`) sur H2 et sur PG16 : aucun adapter de production SE7d n'est donc sans contrat JDBC. Le cas P1 de `LecturesDeLaSemaineJdbcContrat` est repris sous le même nom. |
| §63 | Les 5 requêtes sont toutes en `SELECT` et sont identiques, caractère pour caractère, aux lignes 138-149 du script. Les 14 + 5 vérifications et les 2 contrats PG16 (`mvnw ... -Dtest=...PostgresContractIT`, `:156-159`) sont bien lancés par le script. La valeur « 32 séances » est exacte (32 appels à `seance`), comme « 6 groupes » et « 5 groupes ». |
| `covered.json` | Les 4 fichiers supprimés sont retirés, les 3 traducteurs sont ajoutés, `seamAllowed: []`. |

## Constats

Seuls les constats marqués **violation** contredisent une règle ou un texte publié. Les autres sont des suggestions.

| # | Sévérité | Domaine | Constat | Preuve | Scénario d'échec | Correction recommandée |
|---|---|---|---|---|---|---|
| m1 | minor | legacy / doc | **Violation (affirmation fausse)** sur D-R8. La fiche affirme que la documentation fonctionnelle « cite encore `ExclusionsDeSeancesJdbc` ». Or le HTML a été retouché à la main dans `f812dff0` : il n'y a plus d'occurrence. De plus, il cite `LiensDExclusionFournis` alors que la source Markdown cite `ExclusionsDeSeancesPubliees`. Le HTML ne correspond donc ni au Markdown, ni au PDF ou au DOCX, ni au générateur. | `.ddd/migration/slices/seance-se7d-...-dossier.md:74` ; `docs/client/documentation-fonctionnelle.html:1889` ; `docs/reference/regles-de-gestion/06-absences-patients.md:193-194` | Un lecteur du HTML voit une source différente de celle du référentiel. La prochaine régénération réécrit la ligne sans prévenir. Le résidu D-R8 décrit un état qui n'existe pas. | Annuler la retouche manuelle du HTML (ou aligner les deux sources), corriger le texte de D-R8 (le HTML est retouché, le PDF et le DOCX ne le sont pas, RG-SEA-062 à 064 sont absentes) et régénérer. |
| m2 | minor | tests | Les contrats JDBC des Absences (`LecturesDuSuiviJdbcContrat` sur H2 et PG16, `DetectionPostgresContractIT`, `PatientsAttendusJdbcTest`) ne testent plus le SQL de production. Ils passent par le double `SeancesRealiseesSurLaTable`, qui RECOPIE les requêtes sans l'alias `s.`. La fiche le déclare (D-R5). Les goldens et le contrat de la Séance compensent. | `absences-patients/src/test/.../contrat/SeancesRealiseesSurLaTable.java:20,50` ; `LecturesDuSuiviJdbcContrat.java:39` | Une modification de A5 dans `LecturesDesSeancesRealiseesJdbc` (par exemple un `DISTINCT` ajouté) laisse vertes les 3 suites des Absences. Elle n'est détectée que par le contrat de la Séance et par la matrice SE7d. Si l'un des deux disparaît, la dérive passe sans être vue. | Brancher ces suites sur le vrai adapter, comme le fait déjà le Dossier médical (dépendance de test `contexts-seance`, scope `test`, sans cycle pour les tests). À défaut, ajouter un test d'égalité textuelle double ↔ adapter. |
| m3 | minor | archi | **Violation (texte)** : le commentaire du `pom.xml` affirme « Seul le paquet publishedlanguage est utilisé ». C'est faux pour les tests : le contrat importe `seance.infrastructure.persistence.ConstantesDesSeancesJdbc`. Les deux règles ArchUnit excluent les tests (`ImportOption.DoNotIncludeTests`). | `dossier-medical/pom.xml:101` ; `dossier-medical/src/test/.../contrat/HistoriqueDesConstantesJdbcContrat.java:8,50` | Une classe de test du Dossier médical peut s'appuyer sur n'importe quelle classe interne de la Séance, et aucun verrou ne le détecte. Le couplage est caché derrière une affirmation de « publishedlanguage seul ». | Corriger le commentaire (« en production ; le contrat de test instancie l'adapter de la Séance ») ou inscrire l'exception dans la règle ArchUnit. |
| m4 | minor | archi | Suggestion. La dépendance `contexts-seance` (scope compile) rend accessibles depuis le Dossier médical, par transitivité, les classes internes des Absences, de la Facturation et de la Comptabilité. `ArchitectureHexagonaleTest:73-96` interdit les classes internes de la Séance, de Patient-couverture, de la Planification, de l'Organisation et du Stock, mais PAS celles d'`absencespatients`, de `facturation` ou de `comptabilite`. | `dossier-medical/pom.xml:105` ; `dossier-medical/src/test/.../architecture/ArchitectureHexagonaleTest.java:73-96` | Une auto-complétion importe par exemple `absencespatients.domain.SeanceRealisee` dans le Dossier médical : le code compile et la règle hexagonale du module laisse passer. | Ajouter ces trois contextes à `les_autres_contextes_seulement_par_leur_langage_publie`, ou exclure les dépendances transitives dans le `pom`. |
| n1 | nit | doc | Le filet annonce que le rejeu fait « 15 vérifications ». Le script de `e662b0d9` en compte 14 (le comptage incluait la définition de `verifier()`). La fiche et le §63 donnent bien 14. | `.ddd/safety-net/seance-se7d.md:38` | Les chiffres sont incohérents d'un artefact à l'autre. | Remplacer par 14. |
| n2 | nit | doc | Les résidus de l'ADR (D-R1 à D-R7) ne correspondent pas à ceux de la fiche (D-R1 à D-R8). D-R5 n'y mentionne pas le double des Absences. | `.ddd/architecture/adr/0098-...md:64-73` ; fiche `:64-74` | Le suivi des résidus est incohérent. | Aligner l'ADR (ou le renvoyer à la fiche). |
| n3 | nit | doc | Les mentions « (non commités) » sont périmées depuis `f812dff0`. | ADR 0098 `:75` ; fiche `:25` | — | Retirer ces mentions. |
| n4 | nit | stratégique | Trois types différents portent le même nom simple, `SeancesRealiseesPubliees` : l'interface de la Séance et deux ACL côté consommateur, chez les Absences et la Planification. Dans ces deux contextes, le suffixe « Publiees » décrit un port du fournisseur. ArchUnit empêche la confusion à la compilation, pas à la lecture. | `absencespatients/.../acl/seance/SeancesRealiseesPubliees.java` ; `planification/.../acl/seance/SeancesRealiseesPubliees.java` ; `seance/publishedlanguage/SeancesRealiseesPubliees.java` | — | À trancher avec la question ouverte du glossaire des Absences (D-R5), par exemple `SeancesRealiseesParLaSeance` côté ACL. |

## Points vérifiés sans écart
- Le refactoring sur place est conforme à l'ADR 0024 : pas de flag, pas de façade, mêmes tables, mêmes routes, aucune écriture, aucune migration.
- Les décisions sur KB-SEA-77 à 82 sont tracées dans l'ADR 0098 et `known-bugs.md:1953`, et les détections sont dans le §63.
- Les glossaires ajoutent des termes au statut `proposed`, avec renvois et questions ouvertes. RG-SEA-062 à 064 sont uniques et l'annexe 16 est à jour (12 lecteurs).
- Pas de nouvel accès à l'horloge (décision 5), pas de transaction ouverte par les adapters.
- Les risques non déclarés relevés ne vont pas au-delà de m2 à m4. D-R6 (optimisation, couverture partielle) et D-R7 (concurrence) sont bien déclarés.

## Top 3 actions
1. m1 : corriger le texte de D-R8 et annuler la retouche manuelle du HTML (ou aligner les deux sources), puis régénérer la documentation fonctionnelle.
2. m2 : faire tourner les contrats JDBC des Absences sur le vrai `LecturesDesSeancesRealiseesJdbc` (dépendance de test), ou verrouiller l'égalité double ↔ adapter.
3. m3 et m4 : corriger le commentaire du `pom` du Dossier médical et étendre son `ArchitectureHexagonaleTest` à `absencespatients`, `facturation` et `comptabilite`.

## Suites données (2026-11-08)
| # | Suite |
|---|---|
| m1 | Corrigé. L'ACL côté Absences est `ExclusionsDeSeancesPubliees` (`absencespatients/infrastructure/acl/seance`, câblée par `AbsencesPatientsContexteConfiguration:170`). Le HTML (`documentation-fonctionnelle.html:1889`) cite maintenant la même source que `06-absences-patients.md:193-194`. D-R8 est soldé dans la fiche et dans l'ADR 0098 (décision de l'utilisateur). Le PDF, le DOCX et RG-SEA-062 à 064 suivront la prochaine régénération ordinaire. |
| m2 | Accepté tel quel (décision de l'utilisateur), résidu D-R5 conservé. En tête de `SeancesRealiseesSurLaTable`, un commentaire indique que le SQL est copié et que la référence est le contrat de la Séance plus les goldens SE7d. |
| m3 | Corrigé. Le commentaire `dossier-medical/pom.xml:100-105` distingue la production (`publishedlanguage` seul) du contrat de test (qui importe en plus `ConstantesDesSeancesJdbc`) et cite la transitivité. |
| m4 | Corrigé. Nouvelle règle `ArchitectureHexagonaleTest.la_seance_seulement_par_son_langage_publie_ni_absences_ni_facturation_ni_comptabilite` (production) : refuse tout paquet de la Séance hors `publishedlanguage`, ainsi que tout paquet `absencespatients`, `facturation` et `comptabilite`. Elle passe (11/11 règles). |
| n1 | Corrigé : `seance-se7d.md:38` indique 14 vérifications. |
| n2 | Corrigé : les résidus de l'ADR 0098 sont alignés mot pour mot sur ceux de la fiche (D-R1 à D-R8), qui fait foi. |
| n3 | Corrigé : les mentions « non commités » sont remplacées par les commits `e662b0d9` (filet) et `f812dff0` (lots A à D), dans la fiche et dans l'ADR. |
| n4 | Pas de renommage (décision de l'utilisateur). Une note du glossaire de la Séance distingue les trois `SeancesRealiseesPubliees` par leur paquet. |

Vérifications : `ArchitectureHexagonaleTest` du Dossier médical 11/11 ; tests du Dossier médical verts ; `ReglesDeGestionDocumentationTest` 7/7, `TableDesSeancesTest` 11/11, `FrontieresDesContextesTest` 37/37. Suite complète `./mvnw -o test -fae` (un build à la fois) : BUILD SUCCESS, et d'après les résumés Maven des 15 modules testés, **7 668 tests, 0 échec, 0 erreur, 10 ignorés** (7 667 + la nouvelle règle). La suite a été lancée deux fois : la 1re (BUILD SUCCESS) a perdu ses lignes de résumé à cause du filtre de sortie, d'où une relance. `check-arch.ts` : 0 violation. Aucun golden ni code de production modifié, aucun commit.
