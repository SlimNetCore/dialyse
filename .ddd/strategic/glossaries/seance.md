---
status: validated
date: 2026-10-09
context: Séance (core)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §4]
---
# Glossaire — Séance

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Séance | Acte de dialyse d'un patient, un jour donné, dans un centre, documenté au poste | `Seance` | `seances`, `Seance` | session, séance facturable | `CREE → VALIDEE → SIGNEE` sans retour (RG-SEA-001) | proposed |
| Séance programmée | Séance créée à partir du planning | `Seance` (origine `PROGRAMMEE`) | — | — | issue de `PatientPlacé` | proposed |
| Validation de séance | Constat par l'infirmier que la dialyse a eu lieu (scan QR) ; rend la séance **réalisée** | `Seance.valider()` / `SeanceValidee` | `validate`, `scanAndValidate` | « valider » seul (6 sens, H2) | jour même, sinon régularisation | proposed |
| Séance réalisée | Séance validée ou signée | `Seance.estRealisee()` | — | séance faite | — | proposed |
| Signature médicale | Visa du médecin sur la séance ; **non requis pour facturer** | `Seance.signer()` / `SeanceSignee` | `signByMedecin` | — | RG-FAC-020 confirmée (Q9) | proposed |
| Régularisation | Validation a posteriori d'une séance passée restée créée, après déverrouillage | `Seance.deverrouillerPourRegularisation()` | `unlockForRegularisation` | rattrapage | admin seul, 7 j (RG-SEA-046) | proposed |
| Dérogation hors planning | Séance réalisée hors de la place prévue, avec motif | `DerogationPlanning` | `marquerHorsPlanning` | — | motif obligatoire | proposed |
| Consommable déclaré | Article utilisé pendant la séance, déclaré par l'infirmier | `ConsommableDeclare` | `addSeanceConsommation` | consommation (≠ sortie de stock) | le stock décide des lots (FEFO) | proposed |
| Volet paramédical / médical | Données cliniques saisies pendant la séance | `VoletParamedical`, `VoletMedical` | `VoletParamedical` (434 l.) | — | à redécouper au tactique | proposed |
| Forfait surchargé | Forfait imposé sur une séance, prioritaire sur la PEC | `Seance.surchargerForfait()` | `updateForfait` | tarif | RG-FAC-021 | proposed |
| Verrou de facturation | Interdiction de modifier une séance prise dans une facturation validée | `Seance.verrouillerPourFacturation()` | statut `FACTUREE` | statut « facturée » comme étape de soin | posé sur `FacturationValidee` (ADR 0002) | proposed |

Homonymes : *Séance* (ici) ≠ *Séance facturable* (Facturation) ≠ *Séance, motif de sortie* (Stock). `ABSENT` n'existe pas dans ce contexte (→ Exclusion de facturation, Facturation).
