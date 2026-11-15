---
status: accepted (livré le 2026-11-15, slice ACL (c) finale, décision D8)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: platform (infrastructure/acl/*), seance, dossier-medical, gmao, planification
related: ADR 0008, 0024, 0140 (D7), 0142, 0190, 0191 ; `acl-c-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 20
---

# 0192 - ACL (c) finale : cinq traducteurs quittent la plateforme, les dix autres restent avec leur cycle réel

## Contexte
Mesure des poms (dépendances compile) : `seance -> absences-patients, patient-couverture, planification, stock, facturation, organisation, gmao, documents` ; `absences-patients` ne dépend pas de `seance` ; `dossier-medical -> seance -> documents` ; `documents -> organisation, documents-api` (aucun chemin vers `dossier-medical`) ; `planification -> gmao` ; `gmao -> documents-api, notifications` (aucun chemin vers `planification`). Le périmètre est les 15 ACL de production restantes de `platform/.../infrastructure/acl/*`.

## Décision : déplacés (5 traducteurs)
| Traducteur | Cible | Cycle ? |
|---|---|---|
| `SeancesRealiseesParLaSeance` (+ test), `LiensDExclusionParLaSeance` | `seance/.../infrastructure/acl/absencespatients` | non : `seance -> absences-patients` ; implémentent les ports publiés `SeancesRealiseesFournies`, `LiensDExclusionFournis` des Absences |
| `FichesFourniesParPatientCouverture` (+ test) | `seance/.../infrastructure/acl/patientcouverture` | non : `seance -> patient-couverture, planification` |
| `PapierEnTeteDuCentreParLaPlateforme`, renommé `PapierEnTeteDuCentreParDocuments` | `dossier-medical/.../infrastructure/acl/documents` | non : le Dossier médical déclare `contexts-documents` (déjà sur le classpath via `contexts-seance`) ; `documents` ne dépend que de `organisation` et `documents-api` |
| `CapaciteDesSallesDePlanification` | scindé : GMAO `infrastructure/acl/planification/CapaciteDesSallesDeLaPlanification` + port fourni `gmao.publishedlanguage.CapaciteDesSallesFournie` ; Planification `infrastructure/acl/gmao/CapaciteDesSallesFournieParLaPlanification` | non : `planification -> gmao`. La GMAO ne peut pas importer la Planification ; elle publie le port fourni mince (ADR 0108/0185/0190/0191), la Planification l'implémente |

Le refus de capacité ne passe plus par `BusinessException` (classe de la plateforme, inaccessible à la GMAO) : l'ACL de la GMAO lève `SalleADCapaciteMaximale` (application GMAO, code `SALLE_CAPACITE_DEPASSEE`) que `ErreursDeLaGmaoAdvice` rend en 422, titre « Regle metier violee », même détail, même code que `ApiExceptionHandler`. Goldens `capacite_salle_depassee` et `modifier_capacite_salle_depassee` inchangés et verts.

Câblage : `@Bean` dans `SeanceConfiguration` (3), `LecturesPublieesConfiguration` du Dossier médical (1), `GmaoConfiguration` (1), `PlanificationConfiguration` (1) ; plus de `@Component`. Garde repointée : `TableDesSeancesTest` (traducteurs de SE7d sans SQL). Aucun changement de comportement.

## Décision : conservés en plateforme (10 fichiers), cycle Maven réel (ADR 0008)
Chemin : `platform -> contexts-organisation | contexts-patient-couverture | contexts-gmao -> ... ` et le consommateur est la plateforme elle-même ; héberger ces traducteurs dans le contexte fournisseur exigerait `contexts-X -> platform`, soit `platform -> contexts-X -> platform` (cycle réel).
| Fichiers | Port implémenté (propriété de la plateforme) | Justification |
|---|---|---|
| `organisation/{CreneauxParOrganisation, MedecinsParOrganisation, SallesParOrganisation, ReferentielsDeLOrganisation}` | `ReferentialAdminDelegate`, `ReferentialKind` (`infrastructure/referential/admin`) | façade d'administration générique des référentiels : noyau de la plateforme (ADR 0140 D7), consommateur = plateforme ; traduisent aussi vers `BusinessException` / `ReferentialValidationException` |
| `patientcouverture/{Transporteurs, Forfaits, Payeurs}Par…, ReferentielsDePatientCouverture` | idem | idem |
| `reprise/AppariementParLesContextes` | `AppariementDeReferentiels` (`infrastructure/migration/port`) | outil de reprise de données (plateforme) |
| `gmao/GenerateursDeRepriseGmao` | `GenerateursDeReprise` (`infrastructure/migration/port`) | idem ; reste avec l'outil de reprise (le dossier `gmao` ne contient plus que lui) |

## Gardes datées
- F-R1 (4 fournisseurs de `seance.v1` en plateforme) : SOLDÉ, entrée supprimée dans la fiche SE7f et l'ADR 0100 ; le dernier, `FichesFournies`, vit dans la Séance.
- M-R1 (2 ACL de notification du Dossier médical en plateforme) : SOLDÉ (fichiers absents de la plateforme depuis les slices PL), entrée supprimée dans la fiche DM9.
- F-R2..F-R7 : NON soldables ici, sans rapport avec ces ACL (lectures transitoires SQL de la Séance, décisions KB métier/DPO, champs de volet, `BaseDeLaCapaciteTheorique`, écart de concurrence) ; entrées et dates conservées, aucun report.
- Butoir 2027-03-31 des gardes `TableDe*Test` : aucun rattachement à ces fichiers (aucun SQL), rien à supprimer ni reporter.

## Conséquences
`platform/.../infrastructure/acl/{absences,dossiermedical,seance}` n'existent plus ; `gmao` ne contient plus que `GenerateursDeRepriseGmao`. ACL de production en plateforme : 19 -> 10 ; sous-dossiers 7 -> 4 (`organisation`, `patientcouverture`, `reprise`, `gmao`). Les 10 restants sont le noyau de la façade des référentiels et de la reprise, retirés avec elles.
