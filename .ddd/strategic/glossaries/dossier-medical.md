---
status: validated
date: 2026-10-09
context: Dossier médical (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §9]
---
# Glossaire — Dossier médical

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Constantes | Mesures relevées par l'infirmier pendant une séance (poids avant/après, tension avant/après, débit de sang, ultrafiltration, durée), saisies dans le volet paramédical de la Séance ; le Dossier médical en offre l'historique en lecture | `Constantes`, `ConstantesDeSeance` | `volet_paramedical`, `ConstanteSeanceResponse` | « constante enregistrée » (il n'existe pas d'enregistrement de constante autonome) | lecture seule ; valeurs restituées brutes ; patient inconnu = historique vide (DM1, ADR 0080) | proposed |
| Ordonnance | Document médical signé listant des médicaments | `Ordonnance` | `Ordonnance` | — | `BROUILLON → SIGNEE → IMPRIMEE / ANNULEE` ; signée = verrouillée | proposed |
| Ligne d'ordonnance | Un médicament (codé ou nommé) avec sa posologie, sa voie, sa durée et sa quantité ; immuable dans l'ordonnance | `LigneDOrdonnance`, `Medicament` | `LigneOrdonnance`, `lignes_ordonnance` | — | au moins une par ordonnance ; lues dans l'ordre libellé, posologie (DM2, ADR 0081) | proposed |
| Numéro d'ordonnance | Numéro légal `ORD-nnnnn` attribué à la signature, par centre, dans l'ordre des signatures, sans trou | `Ordonnance.numero` | `SEQ_ORD` (`app_settings`) | — | un refus de signature ne consomme aucun numéro (ADR 0081, à confirmer) | proposed |
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
