---
slice: seance-se5-forfait-surcharge
status: built
date: 2026-11-08
order: 5
context: seance
sources: [.ddd/tactical/seance.md, ADR 0092, ADR 0024, .ddd/safety-net/seance-se5-forfait-surcharge.md]
review-by: 2026-12-15
---

# SE5 — Forfait surchargé (« imposer un forfait »), repris SUR PLACE

> Statut vrai au 2026-11-08 : `built` (lots A, B, C). Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, AUCUNE façade, AUCUN drapeau, AUCUN palier de bascule ni métrique de routage. Décisions de l'ADR 0092 prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**.

## Fait

| Couche | Livré |
|---|---|
| Domaine (`contexts/seance/domain`) | `Seance.imposerUnForfait`, `ForfaitImpose`, `ForfaitDuCentre`, `ForfaitsDuCentre` (port), `ForfaitId`, `AuteurDuForfaitImpose` ; seule `FACTUREE` est refusée (422) |
| Application | `ImposerUnForfait` : 403, 400 séance, 400 forfait introuvable pour le centre actif, 422 ; verrou de ligne (`EcritureConcurrente`, 409) ; forfait courant relu après l'écriture |
| Sortants | `SeancesJdbc` (six colonnes `forfait_override_*` lues et écrites), `ForfaitsDuCentreParLeLegacy` (ACL), `ReferentielDeForfaitsFourniLegacy` (plateforme) ; doubles mémoire / H2 / PG16 sous le même contrat |
| Entrant | `SeancesControleur` `PUT /api/v1/seances/{id}/forfait`, `@PreAuthorize ADMIN`, `ErreursDeSeanceAdvice` |
| Retraits legacy | handler `updateForfait` et `loadCurrentForfait`, `modifierForfait`, `SeanceForfaitCatalogPort`/`Adapter`, `UpdateSeanceForfaitRequest` ; seam ouvert puis refermé (`seams.md`), `covered.json` allégé |
| Garde-fous | ArchUnit : seul `contexts/seance` impose un forfait ; plus aucun appelant des setters legacy du forfait surchargé |
| Documentation | RG-SEA-056 (+ RG-SEA-033 renvoi), annexe 16, glossaire (4 termes `proposed`), section 57 de `verifications-production.md` (10 requêtes SELECT, rejouées sur PG16 jetable par `backend/scripts/seance-se5-pg16-rejeu.sh`) |

## Parité de caractérisation

16/16 scénarios verts sur le nouveau code (+ 1 scénario de revue `se5/date/facture_effacee`, décrit le comportement actuel de `PUT /{id}`, KB-SEA-49). Écarts (3, décidés puis approuvés un par un, legacy archivé R100 dans `se5/_ecarts-legacy/`) : KB-SEA-44 (400 au lieu de 500, et 403 avant 400) deux scénarios, fuite du nom de classe de l'état `EN_COURS` corrigée. Concurrence : 409 sous verrou (nouveau comportement, KB-SEA-44 famille).

## Décisions ouvertes (à confirmer, revue au plus tard le 2026-12-15)

| KB | Décision en vigueur | Risque | Requête §57 |
|---|---|---|---|
| 41 | REPRODUIT : prix absent ou 0.00 accepté | ARGENT | 1 |
| 42 | REPRODUIT : auteur lu dans le corps | traçabilité | 6 |
| 43 | REPRODUIT : séances `CREE`/`ABSENT` acceptées, `facture_id` ignoré | ARGENT | 2, 3 |
| 44 | CORRIGÉ : 400, 403 avant 400, 409 sous verrou | — | — |
| 45 | REPRODUIT sans alerte : écrasement sans historique | audit pauvre | — |
| 46 | REPRODUIT : le forfait courant ignore le statut de la PEC | ARGENT | 8 |
| 47 | HORS PÉRIMÈTRE : Facturation `poserLeForfait` n'écrit pas le code ; détection seule | ARGENT | 4, 5 |
| 48 | REPRODUIT sans alerte : ADMIN seul (DIRECTION/SUPERADMIN : `ROLE_SCOPE`) | — | — |
| 49 (revue) | HORS PÉRIMÈTRE, NON corrigé : le changement de date d'une séance non facturée efface `facture_id` | ARGENT / intégrité | 9, 10 |

## Bilan nominatif des résidus datés (revue au plus tard le 2026-12-15)

| # | Résidu | Responsable du retrait | Date butoir |
|---|---|---|---|
| 1 | Le verrou de ligne ne couvre pas `SeancesDeFacturationJdbc.poserLeForfait` ; le dernier écrit gagne ; code non écrit (KB-SEA-47). Question Facturation / prix : la Facturation lit id, nom et prix imposés (pas le code), donc un prix absent ou nul (KB-SEA-41) est celui qui est facturé ; que fait-elle d'un prix absent ? à confirmer avec sa slice | slice Facturation (reprise de l'écrivain) | 2027-01-31 |
| 2 | `PUT /api/v1/seances/{id}` (`updateDate`) fait un merge de TOUTE la ligne (`SeanceRepositoryAdapter.save`, `toJpa` sans `factureId`) : il **efface `facture_id`** d'une séance non `FACTUREE` (KB-SEA-49, prouvé par `se5/date/facture_effacee`, NON corrigé) et réécrit sans verrou les six colonnes `forfait_override_*` | slice DÉDIÉE de la modification de date, à créer (non couverte par SE6 `seance-se6-verrou-facturation`) ; décision sur KB-SEA-49 avant le 2026-12-15 | 2027-01-31 |
| 3 | Setters et accesseurs `forfaitOverride*` de l'entité legacy `Seance` (mapping), retirés avec le 2 | idem 2 | 2027-01-31 |
| 4 | Lecture du forfait par `ReferentielDeForfaitsFourniLegacy` (plateforme, langage publié) tant que le référentiel des forfaits n'a pas son contexte | slice Référentiel des forfaits (Facturation) | 2027-03-31 |

Si une date butoir est dépassée sans retrait : nouvel ADR de prolongation motivé, sinon blocage de la fusion suivante touchant la Séance.

## Plan de mise en production (sans drapeau)

- Pré-requis : décision écrite sur KB-41, 42, 43, 46, 47, 49 ; requêtes 1 à 10 de la section 57 exécutées avant et après déploiement ; recette (a) à (e) de la section 57.
- Surveillance : requêtes 1, 3, 4 chaque semaine, requête 7 à 0 ligne ; 409 `ECRITURE_CONCURRENTE` observés.
- Rollback : `git revert` du commit de reprise (colonnes inchangées, aucune donnée à restaurer). Jamais d'activation par l'agent.

## Reste à faire

Décisions métiers ci-dessus ; résidus 1 à 4 ; événement `ForfaitDeSeanceSurcharge` non émis (aucun consommateur ; à créer avec le premier, ADR 0092 « Revue ») ; ordre des refus : l'auteur est contrôlé après la facturation (422 avant 400, corrigé en revue).
