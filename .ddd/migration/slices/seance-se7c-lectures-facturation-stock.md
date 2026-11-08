---
slice: seance-se7c-lectures-facturation-stock
status: planned
date: 2026-11-08
order: 7.3
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/safety-net/known-bugs.md (KB-SEA-47, 49, 50)]
---

# SE7c : lectures et écritures de Facturation et de Stock sur la table des séances

Consommateurs qui touchent `seances` hors contexte, aucun ne passe par `seance.v1` aujourd'hui.

| Fichier | Occurrences | Nature |
|---|---|---|
| `contexts/facturation/.../acl/legacyseance/SeancesDeFacturationJdbc.java` | 5 | lecture + **4 UPDATE** (statut `ABSENT`, exclusion, statut, forfait imposé : ARGENT) |
| `contexts/facturation/.../acl/legacyseance/LiaisonDesSeancesJdbc.java` | 1 | **UPDATE** `facture_id` / statut `FACTUREE` (ARGENT) |
| `contexts/facturation/.../acl/legacyseance/SeancesFacturablesJdbc.java` | 1 | lecture des séances facturables |
| `contexts/facturation/.../persistence/LectureDeLaSyntheseJdbc.java` | 3 | lecture (synthèse mensuelle) |
| `contexts/facturation/.../persistence/LectureDuTableauDeBordJdbc.java` | 3 | lecture (pilotage) |
| `contexts/stock/.../acl/legacyseances/StatutDesSeancesLegacy.java`, `PerimetreDesSeancesLegacy.java` | 1 + 1 | lecture statut, salle, créneau |

Filet existant : Facturation F2 à F6 (simulation, validation, retrait, immuabilité), Stock S6b/S4. Risque : élevé (argent, intégrité de facturation, KB-SEA-47/49/50). Découpage conseillé en deux lots : c1 Stock (2 fichiers, lecture seule), c2 Facturation (5 fichiers, 5 UPDATE). Ports publiés à créer dans `seance.v1` : statut/périmètre d'une séance, séances facturables, liaison à la facture, exclusion, forfait imposé.
