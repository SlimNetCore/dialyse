---
status: proposed
date: 2026-11-08
sources: [ADR 0093, commits a3395f1d ec1bfcb6 f84eff26 + lot C (HEAD 69d7111c)]
reviewer: relecteur indépendant (lecture seule)
---

# Revue indépendante : slice `seance-se6-verrou-facturation` (changement de jour et suppression d'une séance)

Contrôles automatiques : `check-arch` = 0 violation (0 baseline, 0 nouvelle). Tests Maven, `state.ts show` et `scan --incremental` NON rejoués (hors périmètre de lecture).

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| minor | tests | L'atomicité de la suppression n'est prouvée que sur un double : `Banc.unite()` ne prend un instantané que de `seances` ; le test « journal en échec » vérifie seulement que la séance reste. Aucun test sur la pile réelle (transaction Spring + Stock + volets JPA + journal) avec échec injecté. Le code est pourtant correct (un seul `TransactionTemplate`, aucun `REQUIRES_NEW` dans stock/seance/plateforme, `@Transactional` des adapters legacy en REQUIRED). | `contexts/seance/src/test/.../application/Banc.java:86-90` ; `SupprimerUneSeanceTest.java:163-172` ; `UniteDeTravailSpring.executer` | Ajouter un test d'intégration : journal qui échoue après `DELETE seances`, puis vérifier consommations, volets, administrations et séance intacts. |
| minor | legacy | Écart de comportement hors liste D1-D8 : un échec de diffusion `SEANCE_SUPPRIMEE` est avalé (journalisé), alors que le legacy renvoyait une 500 après l'effacement. Il est documenté (ADR décision 5, RG-SEA-057) mais ne figure ni dans le tableau D1-D8, ni dans le tableau A des risques, ni dans un golden. Les 8 écarts goldens sont bien les seuls écarts *testés*. | `platform/.../acl/seance/DiffusionsFournieesLegacy.java:52-63` ; ADR 0093 l.49 vs l.56-65 | Ajouter D9 « diffusion non bloquante » à l'ADR et un test unitaire de `DiffusionsFournieesLegacy` avec notification en échec. |
| minor | legacy/archi | Orphelins après suppression non inventoriés. Annulés : consommations et imputations (Stock), administrations, volets. Restent : `bons_sortie.seance_id NOT NULL`, `stock_movements.seance_id` (historique), sans clé étrangère (le script ne vérifie que l'absence de FK). `derogations_regularisation` est vide (07-seances.md:139). La parité avec le legacy tient, mais l'ADR ne dit rien de ces lignes. | `V1__baseline_schema.sql:437,1303` ; `seance-se6-pg16-rejeu.sh:62-64` ; `ConsommationsImputeesJdbc.java:49-60` | Une phrase dans l'ADR (§KB-SEA-51) : les lignes de stock et pièces de sortie gardent la référence d'une séance disparue, voulu ou non ; ajouter au §58 un SELECT de détection si voulu. |
| minor | tactique | `changerDeJour` ne refuse que le statut `FACTUREE`. Une séance portant `facture_id` mais non `FACTUREE` change de jour (et sort de la période de la facture) alors que sa suppression est refusée : asymétrie non énoncée comme telle. Mitigé par la requête 4 du §58 et KB-SEA-54, mais ni l'ADR ni RG-SEA-057 ne citent ce cas. | `domain/Seance.java` (`changerDeJour` / `exigerSupprimable`) ; ADR 0093 l.29 | Écrire ce cas dans le tableau B (KB-SEA-54) avec la décision métier attendue (refuser aussi si `facture_id` ≠ null ?). |
| minor | tactique | Les anciennes surcharges `Seance.restituer(...)` (sans `FactureId`) posent `facture = null` : tout futur adapter qui les utiliserait rendrait la garde de suppression KB-SEA-50 inopérante en silence (fail-open). Seul `SeancesJdbc` utilise la surcharge complète ; les autres appels sont des tests. | `domain/Seance.java:65-84` ; `SeancesJdbc.java:163-168` | Réserver les surcharges courtes au code de test, ou rendre `FactureId` obligatoire (nul explicite). |
| nit | docs | Les nouvelles lignes de l'annexe 16 ne sont pas alignées sur le gabarit de colonnes du tableau. | `16-annexe-tracabilite.md:37-41` | Cosmétique. |

## Points vérifiés conformes (preuve)

1. Hexagonal/langage : `check-arch` vert ; domaine pur (aucune horloge ni ORM) ; ports `Seances`, `AnnulationDesConsommations`, `SaisiesDeLaSeance`, `JournalDesSuppressions` nommés par l'intention ; câblage dans `SeanceConfiguration` (racine de composition) ; ArchUnit verrouille les appelants. Glossaire à jour (5 termes, `.ddd/strategic/glossaries/seance.md`).
2. Intégrité de facturation : `SeancesJdbc.enregistrerLeJour` = `UPDATE seances SET date_seance = ? WHERE id = ? AND center_id = ?` (seule colonne écrite ; `facture_id` et `forfait_override_*` préservés) ; lecture sous `FOR UPDATE` avec 409 (`SeancesJdbc.java:86-93`). KB-50 : `exigerSupprimable` refuse `FACTUREE` (code legacy) puis `facture != null` quel que soit le statut ; ordre séance, motif, statut conservé car `MotifDeSuppression` est construit avant `SuppressionDeSeance.de` (`SupprimerUneSeance.java:52-56`).
3. Écarts : les 8 archives sont R100 (`git diff -M`) et identiques octet pour octet à `a3395f1d` (7 fichiers se6) et `283df5cb` (se5/date), vérifié par `git show | cmp`. Aucun golden non renommé n'a été modifié entre `a3395f1d` et HEAD (seuls R100 et ajouts). D1-D8 concordent avec les fichiers archivés. Réserve : voir constat « hors D1-D8 » ci-dessus.
4. Fidélité : droits `@PreAuthorize("hasRole('ADMIN')")`, ordre des gardes, journal sur les 9 colonnes de `seance_suppression` (script PG16 : 9 colonnes exactes), ordre administrations, volet paramédical, volet médical, diffusion après validation, stock non restitué (KB-SEA-51 cohérent avec l'ADR et RG-SEA-057).
5. Atomicité/verrou : voir constat 1 ; ordre annulation, effacement, suppression, journal dans une seule `uniteDeTravail.executer`.
6. ADR 0093 : section « RISQUES D'INTÉGRITÉ ET D'ARGENT » en tête (l.14), KB-54/55/51 en B avec condition de mise en production et jalon 2026-12-15 (`review-by` en en-tête).
7. Tests sans horloge système : aucun `now()` dans `contexts/seance/src` hors `HorlogeDuCentre` et `Clock.systemUTC()` de `SeanceConfiguration:124` (composition root) ; les tests de caractérisation s'appuient sur `REPERE = 2026-01-01`.
8. §58 : les 8 requêtes du doc sont textuellement identiques à celles du script PG16 (`grep -F`, 8/8) ; SELECT seul ; colonnes vérifiées dans `V1` (`period_start`, `numero_facture`, `seance_count`, `route_template`, `action_code`, `http_method`, `entity_id`, `occurred_at`) ; numérotation 1-8 identique.
9. Résidus : 7 résidus nominatifs avec responsable et butoir propre (2027-01-31 ou 2027-03-31, tous après la revue du 2026-12-15) ; règle de prolongation par ADR.
10. Annexe 16 : `SeanceRestController (résidu : SE7)` cohérent avec les routes restantes (`/journal`, `/dashboard*`, `/calendar*`) et `seance-se7-lectures-publiees` « planned » (`roadmap.md:327`, `state.json:79`) ; `SeanceSuppressionRestController` retiré, ligne SE6 ajoutée avec RG-SEA-050/057.

## Verdict : **pass-with-actions** (0 blocker, 0 major, 5 minor, 1 nit)

Top 3 actions :
1. Test d'intégration d'atomicité sur la pile réelle (journal en échec, rollback complet).
2. Compléter l'ADR : D9 (diffusion non bloquante), inventaire des lignes `bons_sortie`/`stock_movements` conservées, cas « rattachée non facturée » dans KB-SEA-54.
3. Fermer le fail-open des surcharges `Seance.restituer` sans `FactureId`.
