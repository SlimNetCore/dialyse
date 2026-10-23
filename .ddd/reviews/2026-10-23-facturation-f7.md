---
perimetre:
  slice facturation-f7-cloture-mensuelle (et son raccord A6: avertissement `absencesDuMoisNonCloses`)
date: 2026-10-23
relecteur: ddd-reviewer (lecture seule)
sources: [ .ddd/migration/slices/facturation-f7-cloture-mensuelle.md, ADR 0037, ADR 0042, backend/contexts/facturation (domain/cloture, application/cloture, infrastructure, interfaces/http), backend/contexts/absences-patients (UniteDeTravailSpring, ClotureDesAbsencesPourLaFacturation) ]
verdict: pass-with-actions
---

# Revue F7 — Clôture mensuelle de facturation

## Contrôles automatiques

| Contrôle                     | Résultat                                                                                                                                                                                     |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `check-arch.ts`              | **0 violation** (0 en baseline, 0 nouvelle)                                                                                                                                                  |
| `state.ts show`              | 25 slices `built` ; `phases.slices` encore `pending`, `contexts.facturation` = `architected` (valeur hors machine d'état)                                                                    |
| Tests (fiche F7, 2026-10-21) | `./mvnw -o test` 3840 tests verts ; PIT 95 % sur `domain.cloture` + `application.cloture` ; contrats H2 et PostgreSQL 16 verts ; preuve du verrou (retrait de `FOR UPDATE` = 2 échecs sur 4) |

## Points forts (vérifiés)

- Domaine pur : `MoisDeFacturation` sans framework, l'instant et l'auteur sont passés par le cas d'usage (`Clock`
  injecté dans `CloturerLeMois`, `RouvrirLeMois`) ; invariants (double clôture, réouverture d'un mois non clos, motif
  obligatoire ≤ 500) portés par l'agrégat (`MoisDeFacturation.java:60-92`).
- Événements au passé, dans le langage publié (`PeriodeDeFacturationCloturee`, `PeriodeDeFacturationRouverte`), publiés
  dans la transaction.
- Dépendances inter-contextes par langage publié + ACL, dans le bon sens : Comptabilité → `PeriodesComptables`
  (`ClotureComptableComptabilite.java:13-24`) ; Absences → `ClotureDesAbsences` (`AbsencesDuMoisPubliees.java:12-23`).
- Pas d'interblocage possible avec A6 : exclusion/réinclusion = mois de facturation puis mois d'absences ; clôture
  d'absences = mois d'absences seul ; clôture de facturation = mois de facturation puis **lecture sans verrou** du mois
  d'absences (`ClotureDesAbsencesPourLaFacturation.java:23-25`). Aucun cycle.
- Garde dans la transaction, mois verrouillés par ordre croissant (`GardeDesMoisClos.java:26-35`), appelée par les
  quatre actes gelés (`ValiderLaFacturation:82`, `ExclureUneSeance:41`, `ReinclureUneSeance:39`,
  `ChangerLeForfaitDUneSeance:44`).

## Constats

| # | Sévérité | Domaine     | Constat                                                                                                                                                                                                                                                                                                                               | Preuve                                                                                                                                          | Correction recommandée                                                                                                                                                                  |
|---|----------|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | minor    | archi       | `estPostgreSql()` interroge les métadonnées JDBC à **chaque** verrouillage de mois (et à chaque numéro de facture) ; la même chose a été corrigée en A6 par mémoïsation                                                                                                                                                               | `MoisDeFacturationJdbc.java:115-118`, `SequenceDeFacturationJdbc.java:54`, `absences-patients/.../UniteDeTravailSpring.java:48-51`              | Mémoïser le résultat (champ paresseux), comme `MoisAbsencesJdbc`                                                                                                                        |
| 2 | minor    | tactique    | Aucune borne d'attente des verrous côté Facturation : une clôture attend sans limite derrière une longue validation (A2/A6 répondent 409 après 5 s). Effet de bord : quand une exclusion rejoint la transaction des Absences, `SET LOCAL lock_timeout` posé par les Absences s'applique **au reste de la transaction de facturation** | `UniteDeTravailSpring.java:34-46` (rejoint la transaction ouverte puis `SET LOCAL`) ; pas de `lock_timeout` dans `backend/contexts/facturation` | Décider une politique commune (ADR court) : délai posé par la Facturation à l'ouverture de sa transaction, et les Absences ne le posent que si elles ouvrent elles-mêmes la transaction |
| 3 | minor    | tactique    | Intervalle de validation non borné : `moisCouverts()` crée et verrouille une ligne par mois couvert (jusqu'à 1 212 lignes et ~4 requêtes chacune pour 2000-01-01 → 2100-12-31)                                                                                                                                                        | `PeriodeDeFacturation.java:33-39`, `GardeDesMoisClos.java:26-30`, `MoisDeFacturationJdbc.java:64-77`                                            | Borner la période de validation (par ex. 12 mois, refus 400) ou ne verrouiller que les mois ayant des séances éligibles                                                                 |
| 4 | minor    | stratégique | Règles de chronologie divergentes entre les deux clôtures mensuelles : la Facturation accepte de clore le mois en cours et les mois futurs (fiche F7, « À valider » 5), les Absences les refusent (`MOIS_D_ABSENCES_NON_TERMINE`, ADR 0042 § 3). Même terme métier, deux règles                                                       | `facturation-f7-cloture-mensuelle.md:101`, ADR 0042 § 3                                                                                         | Faire trancher par le métier : aligner F7 (refus 422 `PERIODE_NON_TERMINEE`) ou consigner la différence dans les glossaires `facturation` et `absences-patients`                        |
| 5 | nit      | tactique    | Ordre des refus de réouverture : la clôture comptable est vérifiée avant le motif ; un motif absent sur un mois comptablement clos donne 422 au lieu de 400                                                                                                                                                                           | `RouvrirLeMois.java:41-47`                                                                                                                      | Valider le motif d'abord (ou documenter l'ordre dans RG-FAC-043)                                                                                                                        |
| 6 | nit      | tests/doc   | La fiche F7 est périmée : « À faire côté frontend (non modifié ici) » alors que l'écran est livré (`facturation-periodes.ts`, `facturation.store.ts`, `facturation-workspace.component.html`, specs) ; « docs/client non régénérés » alors qu'ils l'ont été le 2026-10-23                                                             | `facturation-f7-cloture-mensuelle.md:104-114, 128`                                                                                              | Mettre la fiche à jour (frontend livré, documentation régénérée)                                                                                                                        |

Aucun blocker, aucun major. Aucun modèle legacy fuité (nouvelle fonctionnalité, pas de legacy). Aucune violation
hexagonale.

## Verdict

**pass-with-actions** — 0 blocker, 0 major, 4 minor, 2 nit.

### Top 3 actions

1. Trancher la règle de chronologie commune aux clôtures mensuelles (constat 4).
2. Politique commune de délai de verrou entre Facturation et Absences (constat 2) + mémoïsation de `estPostgreSql()`
   (constat 1).
3. Borner l'intervalle de validation (constat 3).

## Suivi (2026-10-23)

Slice `facturation-f7b-corrections-revue` (`built`) : constats **1, 3, 5, 6 corrigés** (3 : verrouillage groupé en trois
requêtes, intervalle toujours non borné faute de règle
métier). Restent ouverts : constat 2 (ADR de délai de verrou) et constat 4 (décision métier sur la chronologie des
clôtures).

Mise à jour du 2026-10-23 (après-midi) : **constats 2 et 4 traités** — constat 2 par l' **ADR 0043** (délai fixé par le
propriétaire de la
transaction, 5 s, 409 `FACTURATION_ECRITURE_CONCURRENTE`) ; constat 4 par l' **ADR 0044** (seul un mois terminé se
clôture, 422
`PERIODE_NON_TERMINEE`, comme les absences). **Tous les constats de la revue sont clos.**



