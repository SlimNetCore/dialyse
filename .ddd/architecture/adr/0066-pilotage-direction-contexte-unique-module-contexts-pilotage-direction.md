---
status: accepted
date: 2026-10-29
deciders: utilisateur
---

# ADR 0066 — Pilotage direction : contexte unique, module `backend/contexts/pilotage-direction`, refactoring sur place

## Contexte

Le domaine Pilotage direction (Supporting) porte le tableau de bord direction : indicateurs consolidés multi-contextes,
instantanés mensuels figés, supervision, alertes. Il est porté aujourd'hui par `platform/application/direction` (C8 :
`Direction*QueryService`, `DirectionSnapshotService`, `DirectionRealtimeService`, `DirectionAlertPolicy`, …). Il est
consommé par l'UI direction et par le reporting (synthèse PDF/HTML). Il consomme les langages publiés des autres
contextes (Facturation pour le verrou de clôture mensuelle, Sceance, Absences patients, Stock, GMAO, Planification,
Patient & couverture, Dossier médical, Organisation) et ne produit aucune écriture cross-context (R4).

## Décision

1. Un seul contexte `pilotage-direction` (`backend/contexts/pilotage-direction`, module Maven
   `contexts-pilotage-direction`), package racine `com.hemodialyse.backend.contexts.pilotagedirection`, couches
   `domain`, `application`, `infrastructure`, `interfaces`, `publishedlanguage`, `configuration` (ADR 0011, 0024). Pas
   de strangler progressif : refactoring sur place, mêmes tables et mêmes URLs, configuration toujours active.
2. Agrégats internes : `InstantaneMensuel` (root), `AlerteDirection` (root). Entités dédiées : `TableauDeBord`,
   `ExportSynthese`. Value object `TypeAlerte` (SOUS_EFFECTIF, SUR_CAPACITE, RETARD_VALIDATION, GREFFE_EN_ATTENTE).
   Invariants : un instantané par centre et mois (R1), alerte unique par centre et jour (R2), données anonymisées
   multi-centres (R3), pas d'écriture dans les autres contextes (R4).
3. Langage publié `pilotagedirection.v1` expose les événements `InstantaneMensuelCapture`, `AlerteDirectionGeneree`,
   `ExportSyntheseGenere` en types JDK uniquement. Le contexte ne dépend d'aucun autre contexte que par son langage
   publié ou shared-kernel.
4. Ports sortants : `InstantaneRepository`, `AlerteRepository`, `ExportRepository`, `TableauDeBordRepository` (read
   model), `FacturationPublishedLanguage` (verrou mois clôture, OHS Facturation), `PublicateurDEvenements` (outbox),
   `JournalDesMessagesTraites` (idempotence), `Horloge`, `GenerateurIdentifiants`, `ModeleDocumentPrinter`.
5. Multi-centre strict : toute donnée (instantané, alerte, export, tableau de bord) est rattachée à un `centerId`
   (ADR 0010, shared kernel `CentreId`). Vue société anonymisée multi-centres conservée (agrégation, pas de fuite
   inter-centres).
6. Cache : `ref.instants.mois` (TTL PT3M, clé centerId + mois + annee), `ref.alertes.centre` (TTL PT3M, clé centerId),
   invalidation `@CacheEvict` synchrone lors des écritures.
7. Conformiste en lecture : aucune écriture dans les autres contextes ; consommation des événements publiés
   (`FacturationValidee`, `SeanceValidee`, `AbsenceDetectee`) pour alimenter les indicateurs.

## Conséquences

- `FrontieresDesContextesTest` gagne une règle « le legacy n'utilise le pilotage direction que par son langage publié ».
- Les contextes amont restent propriétaires de leurs données ; le pilotage direction ne lit que leurs langages publiés
  et read models.
- Les agrégats et use cases sont à implémenter lors des slices dédiés ; le squelette ne contient aucun code métier.
- ADR 0009 (clôtures mensuelles par contexte) reste la référence pour l'état des clôtures affiché par le pilotage.