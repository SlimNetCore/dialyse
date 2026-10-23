---
status: accepted
date: 2026-10-23
deciders: utilisateur (« fais ce qu'il y a de mieux », 2026-10-23) ; suite de la revue `.ddd/reviews/2026-10-23-facturation-f7.md` (constat 2)
supersedes: —
related: ADR 0013, 0037, 0042 ; slices A2 (délai de verrou des Absences), F7
---

# ADR 0043 — Délai d'attente des verrous : fixé par le propriétaire de la transaction, commun à la Facturation et aux Absences

## Contexte

Les Absences (A2, A6) bornent l'attente d'un verrou de ligne : leur unité de travail pose `SET LOCAL lock_timeout = 5 s`
sur PostgreSQL et un
refus (SQLSTATE 55P03) devient 409 `ABSENCE_ECRITURE_CONCURRENTE`. La Facturation (F7) ne bornait rien : une clôture
pouvait attendre sans limite
derrière une longue validation, et inversement. Pire, quand une exclusion de séance (Facturation) appelle la déclaration
d'absence, l'unité de
travail des Absences **rejoint** la transaction de facturation et y posait son `SET LOCAL`, qui s'appliquait alors **au
reste** de la transaction
de facturation : effet de bord d'un contexte sur l'autre.

## Décision

1. **Le propriétaire de la transaction fixe le délai.** Une unité de travail ne pose `SET LOCAL lock_timeout` que si
   elle **ouvre** la transaction (`TransactionStatus.isNewTransaction()`) ; une transaction rejointe garde le délai de
   son propriétaire. Appliqué aux deux unités de travail (`facturation/.../transaction/UniteDeTravailSpring`,
   `absences-patients/.../transaction/UniteDeTravailSpring`).
2. **Même délai partout : 5 s** (`FacturationContexteConfiguration.DELAI_DE_VERROU`, comme les Absences). H2 garde son
   propre délai.
3. **Même traduction.** Côté Facturation, un verrou non obtenu dans le délai (55P03,
   `PessimisticLockingFailureException`, `QueryTimeoutException`)
   ou un interblocage détecté devient `EcritureConcurrenteDeFacturation` (port `UniteDeTravail`, couche application) :
   **409
   `FACTURATION_ECRITURE_CONCURRENTE`** pour tous les contrôleurs du contexte
   (`EcrituresConcurrentesDeFacturationAdvice`). Rien n'est écrit (rollback),
   l'acte peut être relancé. Côté Absences, inchangé : 409 `ABSENCE_ECRITURE_CONCURRENTE` (traduit par les dépôts,
   `RefusDeVerrou`) ; quand ce refus
   survient dans une transaction de facturation, c'est ce code qui remonte (clé i18n
   `FACTURATION.ERROR.ABSENCE_ECRITURE_CONCURRENTE` existante).

## Conséquences

- Une clôture (ou une validation) qui attend plus de 5 s répond 409 au lieu de rester bloquée ; l'écran affiche un
  message « réessayez ».
- Une validation très longue (beaucoup de séances) qui détient des verrous plus de 5 s fait échouer les actes
  concurrents du même mois, jamais la
  validation elle-même (elle tient ses verrous). Si cela arrive en pratique, augmenter `DELAI_DE_VERROU` (une seule
  constante).
- Les autres appelants des Absences ouvrent leur propre transaction (contrôle de nuit, écrans) : délai inchangé. Le seul
  appelant qui la rejoint
  aujourd'hui est la Facturation, qui pose désormais son propre délai (le même).
- Preuves : `UniteDeTravailSpringTest` (traduction, sans base) ; `MoisDeFacturationConcurrentPostgresContractIT`
  (PostgreSQL 16 : délai dépassé = 409,
  délai du propriétaire conservé dans une transaction rejointe), à rejouer par
  `backend/scripts/facturation-pg16-rejeu.sh`.

