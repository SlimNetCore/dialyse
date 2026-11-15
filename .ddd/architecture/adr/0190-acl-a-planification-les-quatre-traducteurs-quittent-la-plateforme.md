---
status: accepted (livré le 2026-11-15, slice ACL (a), décision D8 : mesurer d'abord)
date: 2026-11-15
decision-makers: [utilisateur, architecte]
context-scope: platform (infrastructure/acl/planification), planification, dossier-medical, seance
related: ADR 0008, 0024, 0061, 0098, 0101, 0108, 0185 ; `acl-a-realisation.md` ; `inventaire-platform-restant-v2.md` ligne 20
---

# 0190 - ACL (a) Planification : les quatre traducteurs de la plateforme sont repris par les contextes

## Contexte
`platform/.../infrastructure/acl/planification` portait 4 traducteurs transitoires (ADR 0008). Mesure des dépendances Maven : `contexts-planification` ne dépend ni de `dossier-medical` ni de `seance` ; en revanche `dossier-medical` (pom l.110) et `seance` (pom l.110) dépendent déjà de `contexts-planification`. La direction de dépendance existante autorise donc l'inversion par port fourni (précédents ADR 0108, 0185) : le fournisseur implémente le port de la Planification.

## Décision
| Traducteur | Cible | Cycle Maven ? |
|---|---|---|
| `CachesDesPatientsLegacy` (CacheManager Spring, aucun autre contexte) | `contexts/planification/.../infrastructure/acl/patientcouverture/` | non (spring-context, spring-tx déjà présents) |
| `RisquesInfectieuxParLeDossierMedical` | `contexts/dossier-medical/.../infrastructure/acl/planification/` | non : `dossier-medical -> planification` |
| `AbordsVasculairesParLeDossierMedical` | idem | non |
| `SeancesRealiseesAvecLeurPlaceParLaSeance` (+ test) | `contexts/seance/.../infrastructure/acl/planification/` | non : `seance -> planification` |

Câblage par `@Bean` dans `PlanificationConfiguration`, `IndicateursEtStatistiquesConfiguration` et `SeanceConfiguration` (plus de `@Component` ni de scan de la plateforme). Aucun changement de comportement : mêmes ports, mêmes délégations.

## Gardes datées
Aucun de ces 4 fichiers ne contient de SQL ni ne figure dans une liste `TableDe*Test` ; aucune entrée datée (F-R1..F-R7, M-R1, butoir 2027-03-31) ne leur est rattachée, donc aucune à supprimer. Seule la garde `TableDesSeancesTest` (traducteurs sans SQL sur les séances) est repointée sur le nouveau chemin du traducteur de la Séance.

## Conséquences
- Plus aucun fichier dans `platform/.../acl/planification` (31 -> 27 fichiers de production d'ACL en plateforme, 10 -> 9 sous-dossiers).
- Reste hors périmètre : les autres sous-dossiers (stock, facturation, organisation, patientcouverture, absences, gmao, dossiermedical, reprise, seance), à mesurer par les slices (b) et (c).
