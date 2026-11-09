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
| Centre actif | Centre non désactivé ; un centre dont l'état n'a jamais été renseigné est actif | `AnnuaireDesCentres.actifs()` | `centers.actif`, `COALESCE(actif, TRUE)` | centre « en service », centre « ouvert » | ADR 0107 (le Stock garde la variante stricte, ci-dessous, O4-R3) | proposed |
| Centre actif au sens strict | Centre explicitement actif ; un centre dont l'état n'a jamais été renseigné en est EXCLU. Variante réservée au Stock (contrôle de nuit des péremptions), à confirmer métiers/DPO au plus tard le 2026-12-15 | `AnnuaireDesCentres.actifsAuSensStrict()` | `centers.actif = TRUE` | — | ADR 0108, KB-ORG-09, `verifications-production.md` §72 | proposed |
| Verrou du centre | Verrou de la ligne d'un centre, pris dans la transaction de l'appelant, qui sérialise les opérations « centre entier » d'un autre contexte (clôture de période de stock) | `VerrouDuCentre` | `SELECT … FOR UPDATE` sur `centers` | — | ADR 0108 | proposed |
| Fuseau horaire du centre | Fuseau (identifiant IANA) dans lequel se juge « aujourd'hui » pour le centre ; vide si inconnu ou illisible, chaque consommateur applique alors son défaut | `AnnuaireDesCentres.fuseauHoraireDe()` | `centers.fuseau_horaire` | timezone, fuseau du site | ADR 0107 | proposed |
| Annuaire des centres | Langage publié de lecture des centres (actifs, tous, fuseau) | `AnnuaireDesCentres` | lecteurs SQL `CentresActifs*`, `HorlogeDesCentres*`, `JourDuCentre*` | répertoire, registre des centres | organisation.v1, lot A de O4 | proposed |
| Annuaire des sociétés | Langage publié de lecture des sociétés actives, de la société d'un centre, d'une société avec ses centres | `AnnuaireDesSocietes` | `SocietesEtCentresJdbc`, `SocieteDUnCentreJdbc` | répertoire des sociétés | organisation.v1, lot A de O4 | proposed |

Homonymes : *Centre* (ici) ≠ *Centre payeur* (Facturation).
