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

## Termes ajoutés par le modèle tactique (2026-10-26, `.ddd/tactical/planification.md`) — à valider

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Places du centre | Ensemble des places occupées d'un centre, qui garantit qu'un générateur ne sert qu'un patient par jour et créneau | `PlacesDuCentre` | `DonneesPlanning`, `Planning` | planning (seul) | RG-PLN-020/021/024 | proposed |
| Catégorie d'isolement | Patient à risque infectieux ou sans risque | `CategorieDIsolement` | `aRisque` | — | RG-PLN-012, 052 | proposed |
| Ressources du centre | Salles, créneaux, générateurs en service, jours ouverts, fermetures datées utilisés pour placer | `RessourcesDuCentre` | `DonneesPlanning` | — | RG-PLN-011, 013 | proposed |
| Dernier jour d'occupation | Dernier jour où le patient occupe sa place | `DernierJourDOccupation` | `FinOccupation` | — | RG-PAT-032 | proposed |
| Déplacement temporaire | Place occupée un jour donné à la place de la place habituelle (générateur indisponible) | `DeplacementTemporaire` | `DeplacementTemporaire` | — | RG-PLN-096 | proposed |
| Exécution d'optimisation | Un calcul de planning, de son lancement à son application | `ExecutionDOptimisation` | `RunOptimisation` | run | RG-PLN-081 | proposed |
| Périmètre | Ce que l'optimisation recalcule : patients, roulement, couverture, complet, maintenance | `Perimetre` | `PerimetreOptimisation` | — | RG-PLN-080 | proposed |
| Empreinte | Preuve que le centre n'a pas changé depuis le calcul | `Empreinte` | `EmpreinteOptimisation` | — | RG-PLN-091 | proposed |
| Préférence de planification | Créneau préféré et jours à choisir d'un patient | `PreferenceDePlanification` | `PreferencePatient` | — | RG-PLN-093 | proposed |
| Profil de planification | Taux d'activité et compétences d'un infirmier | `ProfilDePlanification` | `ProfilInfirmier` | — | RG-PLN-097 | proposed |
| Réglages de planification | Durée de vacation, temps plein, repos, replanification nocturne d'un centre | `ReglagesDePlanification` | `ReglagesOptimisation` | — | RG-PLN-098 | proposed |
| Roulement de l'infirmier | Affectations hebdomadaires d'un infirmier | `RoulementDeLInfirmier` | `AffectationInfirmier` (liste) | — | RG-INF-022 | proposed |
| Affectation au roulement | Un infirmier dans une salle, sur un créneau, certains jours | `AffectationInfirmier` | `AffectationInfirmier` | affectation (sens Placement/Assuré) | RG-INF-020 | proposed |
| Vacation | Présence d'un infirmier sur une case | `Vacation` | `Vacation` (Timefold) | — | RG-PLN-085 | proposed |
| Case | Salle × créneau × jour | `Case` | `Presence` (case) | poste | RG-INF-040 | proposed |
| Effectif requis | ⌈patients / ratio⌉ d'une case | `EffectifRequis` | `Presence` | — | RG-INF-040 | proposed |
| Sur-effectif | Infirmiers comptés au-delà de l'effectif requis | `SurEffectif` | `surplus` | — | RG-INF-047 | proposed |
| Remplaçant éligible | Infirmier non absent, pas déjà prévu sur le créneau, habilité si isolement | `RemplacantEligible` | `RemplacantService` | — | RG-INF-050 | proposed |
