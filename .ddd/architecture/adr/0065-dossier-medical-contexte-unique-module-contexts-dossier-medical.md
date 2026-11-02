---
status: partially superseded by ADR 0072 (2026-11-02 : le module est gelé ; le contexte unique reste valide)
date: 2026-10-29
deciders: utilisateur
---

# ADR 0065 — Dossier médical : contexte unique, module `backend/contexts/dossier-medical`, refactoring sur place

## Contexte

Le domaine Dossier médical (Core, données de santé sensibles) porte le dossier clinique du patient : constantes,
prescriptions, séances médicales, résultats d'analyses, historique clinique. Il est porté aujourd'hui par `bc-medical`
(98 fichiers, ≥ 10 sous-ensembles cohésifs : ordonnance, examen, biologie, sérologie, observance, greffe…). L'ADR 0007
a déjà décidé de garder un seul contexte « Dossier médical », parcours greffe inclus (bilan pré-greffe isolé et
extractible), pour un seul périmètre de sécurité « données de santé ». Il est consommé par Seance (constant),
Facturation (PEC/absences), Planification (risques infectieux, abords vasculaires), Pilotage (indicateurs) et Patient &
couverture (greffe).

## Décision

1. Un seul contexte `dossier-medical` (`backend/contexts/dossier-medical`, module Maven `contexts-dossier-medical`),
   package racine `com.hemodialyse.backend.contexts.dossiermedical`, couches `domain`, `application`, `infrastructure`,
   `interfaces`, `publishedlanguage`, `configuration` (ADR 0011, 0024). Pas de strangler progressif : refactoring sur
   place, mêmes tables et mêmes URLs, configuration toujours active.
2. Agrégats internes (modules par agrégat) : `Constante`, `Prescription` (ordonnance), `DemandeExamen`, `Serologie`,
   `Antecedent`, `Allergie`, `ObservationBiologique`, `BilanPreGreffe` / `EtapeBilanPreGreffe`,
   `AdministrationTraitement`.
   Le bilan pré-greffe reste isolé et extractible (ADR 0007). Invariants cliniques portés par les agrégats et les
   specifications (transition de statut d'ordonnance/examen, détection sérologique positive, KDIGO).
3. Langage publié `dossiermedical.v1` expose les événements cliniques significatifs (`ConstanteEnregistree`,
   `PrescriptionSignee`, `ResultatExamenRecu`, `SerologiePositiveDetectee`, `BilanPreGreffeComplete`, …) en types JDK
   uniquement. Le contexte ne dépend d'aucun autre contexte que par son langage publié (Patient & couverture pour
   l'identité patient, Organisation pour les centres, Seance pour la séance médicale).
4. Ports sortants : `ConstanteRepository`, `PrescriptionRepository`, `DemandeExamenRepository`, `SerologieRepository`,
   `AntecedentRepository`, `AllergieRepository`, `ObservationBiologiqueRepository`, `BilanPreGreffeRepository`,
   `PatientsPublies`, `SeancesPubliees`, `PublicateurDEvenements`, `JournalDesMessagesTraites`, `Horloge`,
   `GenerateurIdentifiants`. `RisquesInfectieux` et `AbordsVasculaires` sont exposés comme ports publiés pour la
   Planification.
5. Multi-centre strict : toute donnée clinique est rattachée à un `centerId` (ADR 0010, shared kernel `CentreId`).
   Cloisonnement d'accès « données de santé ».
6. Cache : `ref.dossier.constant`, `ref.dossier.prescriptions.actives` (clés incluant toujours le `centerId`), avec
   invalidation `@CacheEvict` synchrone lors des écritures.

## Conséquences

- `FrontieresDesContextesTest` gagne une règle « le legacy n'utilise le dossier médical que par son langage publié ».
- Les contextes aval (Seance, Facturation, Planification, Pilotage) consomment `dossiermedical.v1` via ACL et les ports
  publiés (`RisquesInfectieux`, `AbordsVasculaires`).
- Le contexte dépend de Patient & couverture et d'Organisation (lecture centres/patients) via ACL.
- Les agrégats et use cases sont à implémenter lors des slices dédiés ; le squelette ne contient aucun code métier.
- ADR 0007 (contexte unique, greffe inclus) reste la référence stratégique.