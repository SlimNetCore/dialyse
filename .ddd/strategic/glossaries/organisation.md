---
status: validated
date: 2026-10-09
context: Organisation (supporting) — inclut le shared kernel minimal (ADR 0010)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §1]
---
# Glossaire — Organisation

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Société | Entité juridique regroupant des centres | `Societe` / `SocieteId` (kernel) | `societes` | — | — | proposed |
| Centre | Établissement de dialyse, unité d'isolement des données | `Centre` / `CentreId` (kernel) | `CenterId`, `center`, `centers` | Center | isolement par centre (RG-TRV-002) | proposed |
| Transfert de centre | Rattachement d'un centre à une autre société | `Centre.transfererVers()` / `CentreTransfere` | `SocieteDomainService` | — | — | proposed |
| Salle | Local de dialyse d'un centre (dont salles d'isolement) | `Salle` | `SalleRef` | — | — | proposed |
| Créneau | Plage horaire de dialyse d'un centre | `Creneau` | `CreneauRef` | — | — | proposed |
| Mois | Période mensuelle de référence des clôtures | `Mois` (kernel) | — | — | ADR 0009/0010 | proposed |

Homonymes : *Centre* (ici) ≠ *Centre payeur* (Facturation).
