---
status: validated
date: 2026-10-09
context: Comptabilité (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §7]
---
# Glossaire — Comptabilité

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Écriture comptable | Pièce équilibrée débit/crédit dans un journal | `EcritureComptable` | `EcritureComptable` | — | `BROUILLON → VALIDEE → EXPORTEE` (immuable) | proposed |
| Journal | Registre d'écritures (VE, BQ, CA, AC, ST) | `Journal` | `JournalCode` | — | — | proposed |
| Compte comptable | Compte du plan SCF | `CompteComptable` | `CompteComptable` | compte (seul) | plan par centre | proposed |
| Compte client | Compte 411 du tiers débiteur (patient ou payeur) | `CompteClient` | `411100`, `411500` | — | — | proposed |
| Mapping comptable | Correspondance fait économique → comptes | `MappingComptable` | `MappingComptable`, `RegleTVA` | — | inclut le compte d'article reçu du Stock | proposed |
| Pièce comptable | Écriture saisie manuellement | `PieceComptable` | `PiecesComptablesService` | — | — | proposed |
| Extourne | Écriture inverse annulant une écriture | `EcritureComptable.extourner()` | `extourner` | annulation | — | proposed |
| Période comptable | Mois comptable | `PeriodeComptable` | — | période (seule) | — | proposed |
| Clôture de période comptable | Plus aucune écriture générée sur le mois | `PeriodeComptable.cloturer()` / `PeriodeComptableCloturee` | `cloturerPeriode` | clôture (seule) | ADMIN, non reclôturable (RG-CPT-005) | proposed |
| Comptabilisation du stock | Génération différentielle des écritures de stock (rejouable 35 j) | politique `ComptabilisationStock` | `ComptabiliteStockScheduler` | — | RG-CPT-015 | proposed |
| Export comptable | Transmission des écritures validées au logiciel comptable | `ExportComptable` | `ExportComptablePort` | — | RG-CPT-004 | proposed |

Homonymes : *Compte comptable* ≠ *Compte d'article* (Stock, donne un compte au mapping) ≠ *Compte utilisateur* (Identité & accès).
