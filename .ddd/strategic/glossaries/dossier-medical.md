---
status: validated
date: 2026-10-09
context: Dossier médical (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §9]
---
# Glossaire — Dossier médical

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Ordonnance | Document médical signé listant des médicaments | `Ordonnance` | `Ordonnance` | — | `BROUILLON → SIGNEE → IMPRIMEE / ANNULEE` ; signée = verrouillée | proposed |
| Prescription médicale | Prescription de traitement (EPO/fer, dose × fréquence) suivie en observance | `Prescription` | `prescriptions_medicales` | — | même document qu'ordonnance ? (H8) | proposed |
| Observance | Respect de la prescription d'anémie sur une période | `AlerteObservance` | `AlerteObservance` | — | alerte « retard constaté » (06:30) | proposed |
| Administration de traitement | Injection EPO/fer réalisée par l'infirmier | `AdministrationTraitement` | idem | — | — | proposed |
| Demande d'examen | Demande d'analyses suivie jusqu'à validation | `DemandeExamen` | idem | — | `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE / ANNULE` | proposed |
| Observation biologique | Résultat mesuré d'une analyse | `ObservationBiologique` | idem, `resultats_analyses` | — | correction tracée | proposed |
| Sérologie | Résultat d'un marqueur sérologique | `Serologie` | `serologies_patient` | — | — | proposed |
| Allergie / Antécédent | Éléments du dossier clinique | `Allergie`, `Antecedent` | idem | — | antécédent résolu | proposed |
| Bilan pré-greffe | Parcours de préparation à la greffe rénale | `BilanPreGreffe` | `bilans_pre_greffe` | — | transitions à expliciter (H12) | proposed |
| Décision RCP | Avis de la réunion de concertation pluridisciplinaire | `DecisionRcp` | `AvisRcp` | — | favorable / défavorable / ajourné | proposed |
| Greffe réalisée | Statut final du bilan : le patient a été greffé | `BilanPreGreffe.constaterGreffe()` / `GreffeRealisee` | `GREFFE_REALISEE`, `changerStatut` | — | ≠ état « greffé » (Patient & couverture), H7 | proposed |
| Donneur vivant | Donneur apparenté ou non, avec crossmatch | `DonneurVivant` | idem | — | — | proposed |
