---
slice: seance-se6-verrou-facturation
status: built
date: 2026-11-08
order: 6
context: seance
sources: [.ddd/tactical/seance.md, ADR 0093, ADR 0024, ADR 0062, ADR 0074, .ddd/safety-net/seance-se6-verrou-facturation.md]
review-by: 2026-12-15
---

# SE6 : modification de date et suppression d'une séance, reprises SUR PLACE

> Statut vrai au 2026-11-08 : `built` (lots A, B, C). Périmètre redéfini (la clé d'état `seance-se6-verrou-facturation` est conservée) : l'ancien intitulé « Verrou facturation » ne correspondait à aucun code Séance ; le verrou (statut `FACTUREE` + `facture_id`) est posé par la Facturation, la Séance le LIT. Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, AUCUNE façade, AUCUN drapeau, AUCUN palier de bascule ni métrique de routage. Décisions de l'ADR 0093 prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO au plus tard le 2026-12-15**.

## Fait

| Couche | Livré |
|---|---|
| Domaine (`contexts/seance/domain`) | `Seance.changerDeJour` (seule `FACTUREE` refusée), `Seance.exigerSupprimable` (statut facturé, puis facture rattachée), `FactureId`, `MotifDeSuppression`, `SuppressionDeSeance` |
| Application | `ChangerLeJourDUneSeance` (403, jour 400, séance 400, 422 ; jour absent = jour du centre via `HorlogeDuCentre`) ; `SupprimerUneSeance` (403, 422 `SEANCE_INTROUVABLE`, motif, statut, facture ; unité de travail unique : consommations annulées, saisies effacées, séance supprimée, trace écrite ; diffusion après validation) ; verrou de ligne, 409 `ECRITURE_CONCURRENTE` |
| Sortants | `Seances.enregistrerLeJour` (n'écrit QUE `date_seance`) et `supprimer` (`SeancesJdbc`) ; `JournalDesSuppressionsJdbc` (table `seance_suppression`) ; ACL `AnnulationDesConsommations` (langage publié du Stock), `SaisiesDeLaSeance` (`SaisiesDeSeanceFourniesLegacy`, plateforme), `DiffusionsDeSeance.seanceSupprimee` ; doubles mémoire / H2 / PG16 sous le même contrat |
| Entrant | `SeancesControleur` `PUT` et `DELETE /api/v1/seances/{id}`, `@PreAuthorize ADMIN`, `ErreursDeSeanceAdvice` (403 avant 400, 409, 422) ; DTO `ChangementDeJourDemande`, `SuppressionDeSeanceDemande` |
| Retraits legacy | handler `update` de `SeanceRestController`, `SeanceSuppressionRestController`, `SuppressionSeanceService`, `SuppressionSeanceJournalPort` + adapter, `SuppressionSeance`, `MotifSuppressionSeance`, `UpdateSeanceRequest`, `SeanceUseCase.updateDate`, `Seance.verifierSuppressible`, `SeanceRepositoryPort.delete` ; seam ouvert puis refermé (`seams.md`, `seamAllowed` vide), `covered.json` allégé |
| Revue (2026-11-08) | Atomicité prouvée sur la pile réelle (`AtomiciteDeLaSuppressionDUneSeanceIntegrationTest`, 2 échecs injectés, rollback complet) ; surcharges courtes de `Seance.restituer` interdites au code de production (ArchUnit) ; D9, D10, inventaire §58 requête 9 |
| Garde-fous | ArchUnit (`FrontieresDesContextesTest`) : seul `contexts/seance` dépend de `ChangerLeJourDUneSeance` et `SupprimerUneSeance` ; `SeanceRepositoryPort.save` (ligne entière) n'a plus que `SeanceDomainService` pour appelant |
| Documentation | RG-SEA-057 (+ renvoi en RG-SEA-050), annexe 16, glossaire (5 termes `proposed`), section 58 de `verifications-production.md` (9 requêtes SELECT, rejouées sur PG16 jetable par `backend/scripts/seance-se6-pg16-rejeu.sh`, même numérotation) |

## Parité de caractérisation

21 scénarios joués contre le nouveau code (20 de `se6` + `se5/date/facture_effacee`) : **13 identiques au legacy, 8 écarts DÉCIDÉS**, approuvés un par un en `*_nouveau_code.approved.json`, golden legacy archivé octet pour octet (R100, `git mv`) sous `_ecarts-legacy/` et vérifié contre `git show a3395f1d:<chemin>` (`283df5cb` pour `se5/date`). Détail D1 à D8 : ADR 0093, section Lot B. Aucun écart sur les droits, le cloisonnement, l'audit, la diffusion, le journal, les montants, le stock ni les libellés.

## Décisions ouvertes (à confirmer, revue au plus tard le 2026-12-15)

| KB | Décision en vigueur | Risque | Requête §58 |
|---|---|---|---|
| 49 | CORRIGÉ : le changement de jour n'écrit que `date_seance` ; dégâts passés non réparés | intégrité de facturation | 1, 3, 4, 8 |
| 50 | CORRIGÉ : toute séance rattachée à une facture est refusée à la suppression (422) ; factures déjà orphelines à traiter | intégrité de facturation | 1, 2, 3 |
| 51 | REPRODUIT : aucun stock restitué à la suppression ; `bons_sortie` / `stock_movements` conservés sans séance (inventaire, revue) | stock / argent | 7, 9 |
| D9 | GARDÉ : un échec de diffusion de `SEANCE_SUPPRIMEE` n'empêche pas la suppression (le legacy : 500 après l'effacement) ; test `DiffusionsFournieesLegacyTest` | — | — |
| D10 | REPRODUIT : séance rattachée non `FACTUREE` : le jour change, la suppression est refusée ; refuser aussi le changement de jour ? | intégrité de facturation | 3, 4 |
| 52 | CORRIGÉ : 400 au lieu de 500, `centerId` obligatoire à la suppression, 403 avant 400 | — | — |
| 53 | REPRODUIT sans alerte : ni jour, ni signature, ni patient regardés | — | — |
| 54 | REPRODUIT : le jour d'une séance validée ou signée reste modifiable (il détermine la période de facturation) | ARGENT | 4, 5 |
| 55 | REPRODUIT : le changement de jour contourne le verrou des jours passés | traçabilité | 5 |
| 56 | CORRIGÉ en partie : date illisible ou nombre refusés ; aucune règle de calendrier | — | — |

## Bilan nominatif des résidus datés (revue au plus tard le 2026-12-15 ; butoir propre à chaque résidu)

| # | Résidu | Responsable du retrait | Date butoir |
|---|---|---|---|
| 1 | Réparation des données déjà abîmées par le legacy : factures orphelines (suppression d'une séance rattachée, KB-SEA-50) et `facture_id` effacés (KB-SEA-49) ; détection §58 requêtes 1, 2, 8, aucune réparation par la Séance | slice Facturation avec les métiers (traitement facture par facture) | 2027-01-31 (et AVANT la mise en production si la requête 1 ramène des lignes) |
| 2 | Le verrou de ligne de la Séance ne couvre pas les écrivains de la Facturation (`SeancesDeFacturationJdbc` : `poserLeForfait`, pose de `FACTUREE` et de `facture_id`) ; le dernier écrit gagne (reprend SE5 résidu 1) | slice Facturation (reprise de l'écrivain) | 2027-01-31 |
| 3 | `SeanceRepositoryPort.save` / `SeanceRepositoryAdapter.toJpa` réécrivent TOUTE la ligne sans `factureId` (usage restant : `SeanceDomainService`, création et scan résiduel sans appelant de production) ; ne doit jamais être rappelé sur une séance existante | retrait avec SE7 (lectures publiées) puis du legacy `Seance` | 2027-03-31 |
| 4 | Setters et accesseurs `forfaitOverride*` et `factureId` de l'entité legacy `SeanceJpaEntity` (mapping), plus aucun écrivain (reprend SE5 résidu 3 ; SE5 résidu 2, le merge de ligne entière de `updateDate`, est CLOS par SE6) | idem 3 | 2027-03-31 |
| 5 | `SaisiesDeSeanceFourniesLegacy` (administrations EPO/fer, volets) et `DiffusionsFournieesLegacy.seanceSupprimee` fournis par la plateforme tant que les volets et la diffusion n'ont pas leur contexte | slices Dossier médical / Notifications (propriétaires des volets et de la diffusion) | 2027-03-31 |
| 6 | Aucun stock restitué à la suppression (KB-SEA-51) : décision restituer (par le Stock, `ConsommationsDeSeance.annuler` à faire rendre) ou documenter | décision métiers, puis slice Stock | décision 2026-12-15 ; réalisation 2027-01-31 |
| 7 | Aucun événement d'intégration (`SeanceSupprimee`, `JourDeSeanceChange`) : aucun consommateur ; à créer avec le premier (Outbox, `seance.v1`) | slice Facturation (premier consommateur) | 2027-03-31 |

Si une date butoir est dépassée sans retrait : nouvel ADR de prolongation motivé, sinon blocage de la fusion suivante touchant la Séance.

## Plan de mise en production (sans drapeau)

- Pré-requis : décision écrite sur KB-49, 50, 51, 54, 55 ; requêtes 1 à 9 de la section 58 exécutées avant et après déploiement (requête 1 à 0 ligne ou factures traitées) ; recette (a) à (g) de la section 58.
- Surveillance : requêtes 1, 3, 4 et 6 chaque semaine ; 409 `ECRITURE_CONCURRENTE` observés ; clients appelant `DELETE` sans `centerId` (400 désormais).
- Rollback : `git revert` du commit de reprise (colonnes inchangées, aucune donnée à restaurer ; les suppressions déjà faites restent irréversibles). Jamais d'activation par l'agent.

## Reste à faire

Décisions métiers ci-dessus ; résidus 1 à 7 ; événements d'intégration non émis ; contrôle humain des 8 écarts décidés (D1 à D8, ADR 0093).
