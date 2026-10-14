---
status: proposed
date: 2026-10-09
decision-makers: [direction technique, architecte, experts métier GMAO]
context-scope: global
research: []
---
# 0020 — Commencer par la slice « Plans de maintenance préventive », puis Intervenants, Équipement, Interventions

## Contexte et problème
ADR 0008 propose GMAO en premier (isolé : 0 arête vers un autre `bc-*`, `query.ts seam` gmao↔stock vide ; `bc-gmao` 30 fichiers, 16 tests). Mais GMAO n'est pas une slice : l'agrégat Équipement est lu en SQL par 8 sites legacy et muté par le legacy lors des interventions (`InterventionEquipementStatutService.java:49-58`). La première slice doit être petite, réelle, peu risquée, représentative (skill strangler-fig).

## Options considérées
1. **Plans de maintenance** — 9 endpoints, un agrégat, aucune écriture croisée, aucun lecteur SQL externe, pas d'argent ; prouve façade, flag par centre, schéma `gmao`, Flyway, caractérisation, parité, shadow, retrait. Ne prouve ni événements publiés ni ACL vers Planification.
2. Cycle de vie de l'équipement — prouve outbox + ACL, mais 8 lecteurs SQL, seam dans le service d'intervention, effet sur le planning : trop risqué pour rôder l'outillage.
3. Interventions — cœur de la douleur (565 l., coûts), mais argent, dépendances Équipement/Intervenant : en dernier.

## Décision
Ordre : **G1 Plans** (score 22) → G2 Intervenants (21) → G3 Cycle de vie équipement (18) → G4 Interventions (18) → G5 Pièces jointes (18) → G6 Consultation & pilotage (19, dépend de G3/G4). Dépendances qui priment sur le score : G2 avant G4 (tarif), G3 avant G4 (propriétaire de l'état de l'équipement), G4 avant G5/G6. Risque non couvert par G1 (registre Modulith, ACL) : un **spike** dans G1 publie `PlanDeMaintenanceÉtabli` via le registre sans consommateur externe (réponse à `layout.md` Q1).

## Conséquences
+ outillage éprouvé sur un périmètre sans effet de bord ; − l'ACL `legacy-planification` n'est prouvée qu'en G3 (mois 4-5). ⚠ La durée de vie de la projection GMAO→legacy est liée à l'extraction de Planification (6ᵉ contexte) : voir ADR 0018, échéance 2028-06-30 ; si la direction veut la raccourcir, avancer Planification dans l'ordre (arbitrage au gate).

## Révision
Après G1 (retrospective, durées réelles) et à chaque gate de contexte.
