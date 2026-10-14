---
status: validated
date: 2026-10-09
context: Pilotage direction (supporting, lecture)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §11]
---
# Glossaire — Pilotage direction

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Direction | Vue de la société sur ses centres, anonymisée | — | `Direction*` | — | aucune écriture dans les autres contextes | proposed |
| Instantané mensuel | Indicateurs d'un mois écoulé, figés, jamais recalculés | `InstantaneMensuel` | `direction_snapshot` | snapshot | RG-DIR-090 | proposed |
| Alerte de direction | Seuil dépassé (sévérité) | `AlerteDirection` | `DirectionAlertPolicy` | — | à valider | proposed |
| État des clôtures | Avancement des 4 clôtures mensuelles par centre | `EtatClotures` (projection) | — (à créer) | — | ADR 0009 | proposed |
| Perte valorisée | Somme des valorisations d'absences (HT) | projection | — | — | issue d'Absences patients | proposed |
