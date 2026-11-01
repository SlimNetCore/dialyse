---
status: accepted
date: 2026-10-29
deciders: utilisateur
---

# ADR 0062 — Seance : contexte unique, module `backend/contexts/seance`, refactoring sur place

## Contexte

Le domaine Seance (medicale central, Core) est le dernier gros contexte non architecte. Il est reference par
Planification (lecture des seances realisees), Stock (consommables declares, sortie avec motif), Facturation (verrou
FACTUREE sur FacturationValidee), Absences (suppression d'absence detectee), Dossier medical (lien
ordonnance) et Pilotage. Le legacy `bc-seance` porte `Seance`, `VoletParamedical` (434 l.), `ConsommableDeclare`,
`DerogationRegularisation`, et un statut `ABSENT` a supprimer (ADR 0002).

## Decision

1. Un seul contexte `seance` (`backend/contexts/seance`, module Maven `contexts-seance`), package racine
   `com.hemodialyse.backend.contexts.seance`, couches `domain`, `application`, `infrastructure`, `interfaces`,
   `publishedlanguage`, `configuration` (ADR 0011, 0024). Pas de strangle progressif : refactoring sur place, memes
   tables et memes URLs, configuration toujours active.
2. Deux agregats (`Seance`, `ConsommableDeclare`) et un agregat satellite (`DerogationRegularisation`). Statut
   `ABSENT` supprime ; `FACTUREE` devient un verrou interne pose sur reception de `FacturationValidee` (ADR 0002).
3. Langage publie `seance.v1` expose les evenements de domaine (`SeanceCreee`, `SeanceValidee`, `SeanceSignee`,
   `ConsommablesDeSeanceDeclares`, `ForfaitDeSeanceSurchargé`, `SeanceDeverrouilleePourRegularisation`) en types JDK
   uniquement. Le contexte ne depend d'aucun autre contexte que par son langage publie (Stock, Planification) ;
   Patient & couverture est lu via port `PECRepository` (Customer-Supplier).
4. Le verrou de validation jour meme (7 j, RG-SEA-046) et le forfait surcharge sont des regles du domaine ;
   l'invalidation des caches `ref.seances.*` est un `@CacheEvict` synchrone dans l'adaptateur HTTP.
5. Pas de Timefold ni de solveur dans ce contexte. Modulith events-api pour l'ecouteur `FacturationValidee`.

## Consequences

- `FrontieresDesContextesTest` gagne une regle "le legacy n'utilise la seance que par son langage publie".
- Les contextes aval (Facturation, Stock, Absences, Dossier medical, Pilotage) consomment `seance.v1` via ACL.
- Patient & couverture doit exposer un port publie `PECRepository` avant la slice creation de seance.
