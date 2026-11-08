---
slice: seance-se7c-lectures-facturation-stock
status: built (c1 et c2 built, 2026-11-08)
date: 2026-11-08
order: 7.3
context: seance
parent: seance-se7-lectures-publiees
depends_on: [seance-se7a-routes-residuelles]
sources: [.ddd/safety-net/seance-se7-lectures-publiees.md, .ddd/safety-net/seance-se7c2-ecritures-facturation.md, .ddd/safety-net/known-bugs.md (KB-SEA-47, 49, 50, 68 à 70), ADR 0096]
---

# SE7c : lectures et écritures de Facturation et de Stock sur la table des séances

Consommateurs qui touchaient `seances` hors contexte, aucun ne passait par `seance.v1`. Deux sous-slices : **c1 Stock (lecture seule)** `slices.seance-se7c1-lectures-stock` = `built` ; **c2 Facturation (5 fichiers, 5 UPDATE d'argent)** `slices.seance-se7c2-ecritures-facturation` = `built` (2026-11-08). La clé du parent `seance-se7c-lectures-facturation-stock` est à `built`.

## c1 : Stock (built, 2026-11-08, ADR 0096)
| Fait | Détail |
|---|---|
| Étape 0 | Filet complémentaire `characterization/seance/se7c/PerimetreDesSeancesDuStockCharacterizationIntegrationTest` (9 tests, verts sur le legacy avant migration) ; le statut était déjà verrouillé par `SeanceFactureeImmuable...` (F6). KB-SEA-68 à 70 consignés, non corrigés |
| Ports publiés | `FacturationDesSeancesPubliee`, `PlacesDesSeancesPubliees` dans `contexts/seance/publishedlanguage` |
| Adapter | `LecturesPublieesDesSeancesJdbc` (`contexts/seance/infrastructure/persistence`), contrat à 8 cas : double en mémoire, H2, PostgreSQL 16 jetable (`backend/scripts/seance-se7c1-pg16-rejeu.sh`, port 55543) |
| ACL du Stock | `platform/.../infrastructure/acl/stock/StatutDesSeancesParLaSeance`, `PerimetreDesSeancesParLaSeance` (dans la plateforme : cycle Maven Seance -> Stock, ADR 0096 décision 4) ; seam inscrit puis refermé, fichiers dans `covered.json` |
| Retiré | `StatutDesSeancesLegacy`, `PerimetreDesSeancesLegacy` et leurs 2 beans ; entrée Stock de `TableDeLOrganisationTest` |
| Verrous | `TableDesSeancesTest` réécrit (liste blanche nominative datée de 22 lecteurs (garde durcie par la revue : `FROM a, seances`, `public.seances`, ressources SQL hors Flyway ; elle a révélé 4 ressources), aucun du Stock) ; règle ArchUnit `le_stock_ne_depend_pas_de_la_seance` ; `FrontieresDesContextesTest` sans exception |
| Parité | Goldens Stock S6b / S4 inchangés et verts ; `SeanceFactureeImmuable...` et le filet SE7c appellent les ports du Stock câblés en production |
| Écarts de parité | Aucun (REPRODUIRE) |
| Flag / bascule | Aucun (refactoring sur place, ADR 0024) ; rollback = retour au commit précédent ; `.ddd/migration/verifications-production.md` §61 (SELECT seule) |

### Reste à faire (c1)
Décisions KB-SEA-68, 69, 70 (métiers) ; remplacer la lecture de `salle` et `position_creneau` par `organisation.v1` (jalon O4) ; replacer l'ACL du Stock dans `contexts/stock` si un module de langage publié partagé est décidé.

## c2 : Facturation (planned, audit de couverture rendue)
Audit par fichier et par requête : `.ddd/safety-net/seance-se7c2-ecritures-facturation.md`. Découpage recommandé : c2a (lectures + 4 UPDATE dont 3 gardés par `facture_id IS NULL` ; `lierALAbsence` ne l'est pas), c2b (`LiaisonDesSeancesJdbc.lier`, le seul UPDATE qui crée de l'argent, test de course PG16). Tests de caractérisation à ajouter AVANT tout code : `lierALAbsence` sur une séance facturée, KB-SEA-47 côté Facturation, KB-FAC-17 (statut en minuscules), course d'émission, `statut_avant_exclusion` blanc, tableau de bord contre synthèse. Aucun code c2 écrit.

### c2 : lot A livré (2026-11-08)
ADR 0097 (accepted, à confirmer, review-by 2026-12-15) + 4 ports publiés dans `contexts/seance/publishedlanguage` (`SeancesAFacturerPubliees`, `ExclusionsEtForfaitsDesSeancesPublies`, `LiaisonDesSeancesAUneFacturePubliee`, `SeancesDesFacturesPubliees`). Aucun adapter, aucun trafic changé. Cycle Maven vérifié (Séance -> Absences-patients -> Facturation) : traducteurs dans `platform/infrastructure/acl/facturation/`. **c2a livré** : `FacturationDesSeancesJdbc` (séances à facturer, `trouver`, `exclure`, `reinclure`, `poserLeForfait`, `lierALAbsence` encore SANS garde de facture, comptages) ; contrat commun de 21 cas (double, H2, PG16 jetable par `backend/scripts/seance-se7c2a-pg16-rejeu.sh`, port 55546) ; entrée provisoire de `TableDuPatientCouvertureTest` (jointure patients, retirée à B avec `SeancesFacturablesJdbc`). Aucun trafic changé. Reste : c2b (`lier`, `lierALAbsence` corrigé KB-SEA-71), B (bascule), C (documents, état).

### c2 : built (2026-11-08)
c2b + B livrés (`42108ce2`), lot C (documents, état) fait : `slices.seance-se7c2-ecritures-facturation` = `built`, clé parente `built`. Détail, correspondance des contrats supprimés et résidus C2-R1 à C2-R8 : `.ddd/migration/slices/seance-se7c2-ecritures-facturation.md`.

## Bilan nominatif des résidus (revue ≤ 2026-12-15 ET butoir propre)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| R1 | `LecturesPublieesDesSeancesJdbc` lit `salle` et `position_creneau` en SQL direct : à remplacer par `organisation.v1` (O4) | 2026-12-15 | 2027-03-31 |
| R2 | ACL du Stock logée dans la plateforme (cycle Maven) | 2026-12-15 | 2027-06-30 |
| R3 | Décisions KB-SEA-68, 69, 70 | 2026-12-15 | 2027-01-31 (et, au plus tard, avant toute mise en production) |
| R4 | ~~22 lecteurs SQL de `seances` hors Séance en liste blanche (`TableDesSeancesTest`) : Facturation (5)~~ SOLDÉ par c2 (`42108ce2`, 17 lecteurs) | 2026-12-15 | 2027-01-31 |
| R5 | idem : absences-patients (3), planification (1), dossier-médical (1), fournisseurs plateforme de la Séance (2) | 2026-12-15 | 2027-03-31 |
| R6 | idem : pilotage Direction (3), statistiques du patient, cahier de dialyse (service et état `cahier_dialyse.jrxml`), annulation de migration (SUPPRESSION de séances), 3 scripts SQL de développement / démonstration (`seed.sql`, `renadial-seed.sql`, `renadial-purge.sql`) | 2026-12-15 | 2027-06-30 |
| R7 | c2 : KB-SEA-47, KB-FAC-17, deux définitions de « facturée » : reproduits par l'ADR 0097, suivis par C2-R2, C2-R3, C2-R4 (fiche c2) | 2026-12-15 | 2027-01-31 |
