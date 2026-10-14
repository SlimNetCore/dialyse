---
status: validated
date: 2026-10-09
context: Planification (core)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §3]
---
# Glossaire — Planification

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Place | Salle + créneau + générateur + jours de dialyse attribués à un patient | `Place` | colonnes de `patients`, `position_creneau` | poste | libérée après le dernier jour d'occupation (RG-PAT-033) | proposed |
| Placement | Attribution d'une place à un patient | `Placement` / `PatientPlace` | affectation | affectation (sens Assuré) | refus si incohérent (RG-PLN-050) ; isolement (RG-PLN-053) | proposed |
| Proposition de place | Place candidate notée 0-100 | `PropositionDePlace` | `PlanificationAffectationService.proposer` | — | RG-PLN-030 | proposed |
| Générateur | Machine de dialyse vue comme ressource d'une place | `Generateur` | — | équipement (GMAO) | indisponible si `EquipementMisHorsService` | proposed |
| Créneau / Salle | Plage horaire / local (référence Organisation) | `CreneauRef`, `SalleRef` | `CreneauRef`, `SalleRef` | — | conformiste à Organisation | proposed |
| Jours de dialyse | Jours de la semaine où le patient est attendu | `JoursDeDialyse` | — | — | base de la détection d'absence | proposed |
| Optimisation du planning | Calcul automatique d'un planning, appliqué sur décision | `RunOptimisation` | `RunOptimisation`, Timefold | — | `PropositionAppliquee` | proposed |
| Replanification automatique | Optimisation nocturne pour les centres qui l'activent | politique `ReplanificationNocturne` | `ReplanificationAutomatiqueScheduler` | — | RG-PLN-100 | proposed |
| Roulement | Affectation des infirmiers aux créneaux | `Roulement` | — | — | — | proposed |
| Absence d'infirmier | Indisponibilité d'un infirmier (congé, maladie, formation) | `AbsenceInfirmier` | `AbsenceInfirmier` | absence (seul) | déclenche la recherche de remplaçant | proposed |
| Remplaçant | Infirmier proposé (classé) pour couvrir une absence d'infirmier | `Remplacant` | `RemplacantService` | — | RG-INF-051 | proposed |
| Sous-effectif | Créneau dont l'effectif infirmier est insuffisant | `SousEffectif` | `PresenceInfirmierScheduler` | — | horizon 14 j, ratio 4 (à confirmer) | proposed |
| Capacité théorique | Nombre de places disponibles par semaine | `CapaciteTheorique` | `CapaciteTheoriqueCalculator` | — | lecture | proposed |

Homonymes : *Absence d'infirmier* (ici) ≠ *Absence de patient* (Absences patients). *Générateur* (ici) ≠ *Équipement* (GMAO).
