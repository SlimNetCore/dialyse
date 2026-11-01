---
status: accepted
date: 2026-10-29
deciders: utilisateur
---

# ADR 0063 — Organisation : contexte referentiel partage, module `backend/contexts/organisation`

## Contexte

Le domaine Organisation (referentiel partage : societes, centres, salles, creneaux, positions de creneau, medecins,
machines, protocoles) est le support de l'isolation multi-centre et de la planification. Il est consomme par
Planification (salles/creneaux), Stock (centres actifs), Seance, Facturation, GMAO et Pilotage. Le legacy
`bc-referential` porte une partie de ces referentiels.

## Decision

1. Un seul contexte `organisation` (`backend/contexts/organisation`, module Maven `contexts-organisation`), package
   racine `com.hemodialyse.backend.contexts.organisation`, couches `domain`, `application`, `infrastructure`,
   `interfaces`, `publishedlanguage`, `configuration` (ADR 0011, 0024). Refactoring sur place, configuration toujours
   active.
2. Agregats : `Societe`, `Centre`, `Salle`, `Creneau` (et position de creneau). `Centre` est la racine de l'OHS :
   seul l'identifiant de centre est publie (Published Language, ADR 0010).
3. Langage publie `organisation.v1` expose `CentreCreee`, `CentreTransfere`, `SalleOuverte`, `SalleFermee`,
   `CreneauCree`, `ParametresCentreModifies` (types JDK uniquement). Le contexte ne consomme aucun evenement externe
   pour la v1.
4. Referentiels et caches : `ref.centres.actifs` (PT3M), `ref.salles.du.centre` (PT3M), `ref.parametres.centre`
   (PT15M), cles incluant `centerId`/`societeId`.
5. Pas de Timefold. Modulith events-api pour la publication des evenements de domaine.

## Consequences

- `FrontieresDesContextesTest` gagne une regle "le legacy n'utilise l'organisation que par son langage publie"
  (vide tant que le module est un squelette).
- Les contextes aval (Planification, Stock, Seance, Facturation, GMAO, Pilotage) consomment `organisation.v1` via ACL.
- patient-couverture, dossier-medical et pilotage-direction restent a architecturer dans une passe suivante.