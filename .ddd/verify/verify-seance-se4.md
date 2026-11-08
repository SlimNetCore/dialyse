---
status: proposed
date: 2026-11-08
sources: ADR 0091, commits 10710fd0, 4d30a5f7, a387b8a4, 071efd45
---

# Revue indépendante : slice seance-se4-derogation-regularisation

Contrôles automatiques : `check-arch.ts` = 0 violation (0 baseline, 0 nouvelle). Revue en lecture seule : les tests n'ont pas été relancés.

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | archi/tests | Le repli sur violation de clé n'est prouvé que par un mock. Sur PostgreSQL, un INSERT en échec dans la transaction en cours l'abandonne (25P02). L'appel `journal.existe(id)` du repli échouerait donc dans l'unité de travail du cas d'usage. Le repli ne servirait que si le verrou consultatif était contourné. Dans ce cas l'erreur remonte, le centre est rendu en incident et il n'y a pas de doublon. L'ADR et le §56 présentent pourtant ce repli comme un filet opérationnel. | `platform/src/main/java/com/hemodialyse/backend/infrastructure/acl/seance/RappelsFournisLegacy.java:44-50` ; `platform/src/test/java/com/hemodialyse/backend/infrastructure/acl/seance/RappelsFournisLegacyTest.java:66` (mock) | Reformuler : le verrou est la garantie, le repli est une tolérance limitée. Ou tester le repli sur PG16 avec deux transactions, ou le faire via `INSERT … ON CONFLICT DO NOTHING`. |
| minor | tests | Le rejeu à deux instances et le cron de bout en bout ne sont pas testés (verrou PG16, cron et déduplication testés séparément). Le résidu est déclaré avec une date, 2027-01-31, postérieure au 2026-12-15. | `.ddd/migration/slices/seance-se4-derogation-regularisation.md:49` | Écrire le test avant la mise en production. Sinon, garder la recette manuelle §56(e) comme condition de déploiement (déjà écrite). |
| minor | legacy/docs | §56 requête 1 compte aussi les séances « créées » hors fenêtre déjà déverrouillées. Celles-là ne sont plus bloquées pour l'infirmier. Le chiffre surestime donc KB-SEA-37. | `.ddd/migration/verifications-production.md` §56-1 (pas de `AND s.regularisation_deverrouillee_at IS NULL`) | Ajouter le filtre `regularisation_deverrouillee_at IS NULL`. |
| minor | tactique/docs | Résidus à échéance 2027-01-31 (lignes 44-46, 49) sans date de revue ≤ 2026-12-15 propre. Seul l'en-tête de la table porte « revue au plus tard le 2026-12-15 ». | `.ddd/migration/slices/seance-se4-derogation-regularisation.md:40-49` | Acceptable. Rappeler la revue du 2026-12-15 dans chaque ligne, ou la lire comme implicite. |
| nit | tests | `Clock.systemUTC()` est construit dans les tests d'application. Il n'est pas lu, car le jour vient de la fonction injectée. Aucune dépendance réelle à l'horloge système. | `backend/contexts/seance/src/test/java/com/hemodialyse/backend/contexts/seance/application/RappelerLesSeancesARegulariserTest.java:207,235` | Utiliser `Clock.fixed`. |
| nit | archi | Surcharge `notifySeancesARegulariser` à 3 arguments : plus aucun appelant de production (déclaré en résidu). | `platform/.../application/notification/NotificationService.java:535` | Retrait au 2027-01-31, comme prévu. |

## Vérifications positives (preuves)
1. **Hexagonal / langage.** `check-arch` à 0. Le domaine est pur (records validés, aucune horloge ni ORM). `HorlogeDuCentre` et les ports `CentresARappeler`, `RappelsDeRegularisation` et `VerrousDesRappels` sont nommés par le métier. Le planificateur, l'ACL et le câblage sont dans `SeanceConfiguration`. Un verrou ArchUnit interdit un second appelant de `notifySeancesARegulariser`.
2. **Fidélité legacy.** Fenêtre J-7 à J-1 (`FenetreDeRegularisation.java:24-26`, identique à l'ancien `SeanceRegularisationService`). Statut `CREE`, non déverrouillées, tri `DATE`, page de 1 pour le total et la plus ancienne. `plusAncienne` vide si inconnue, comme le legacy (`NotificationService.java:546-548`). Cible `ADMIN`, type `SEANCES_A_REGULARISER`, tous les centres sans filtre (`CentresARappelerJdbc.java:24`). Les 9 scénarios identiques sont déclarés dans l'ADR.
3. **Écarts décidés.** Un seul écart de parité (KB-SEA-35, étape 04 de `cloisonnement_et_repetition`) et une correction (KB-SEA-36). Les deux sont dans l'ADR (décisions, conséquence « fige au premier passage », « à confirmer métiers »), dans le glossaire et dans RG-SEA-055. Aucun écart silencieux.
4. **Archive du golden.** `git show 10710fd0:<ancien chemin> | cmp` est IDENTIQUE à `_ecarts-legacy/.../cloisonnement_et_repetition_avant_se4.legacy.json`. Git le voit en R100.
5. **Un seul planificateur.** Seul `RappelDesRegularisationsPlanifie.java:29` porte `@Scheduled` pour ce rappel (grep de tout le backend). Cron `0 0 7 * * *` avec la zone `app.seance.fuseau-par-defaut`, repli Stock, Absences, puis `Africa/Algiers`. `SeanceRegularisationScheduler` et `SeanceRegularisationService` sont supprimés (D dans `git diff`). Le test du planificateur vérifie un seul `@Scheduled` et le cron.
6. **Verrou et identifiant.** `pg_advisory_xact_lock(hashtextextended(clé))` avec le préfixe `rappel-regularisation|centre|jour` (`VerrousDesRappelsJdbc.java:22,33,40`). Il est libéré à la fin de la transaction, ce que `VerrousDesRappelsPostgresContractIT` vérifie. Un autre centre ou un autre jour n'attend pas, vérifié par le même test. Le délai est borné par `lock_timeout` (`UniteDeTravailSpring.java:54`) et 55P03 devient `EcritureConcurrente`. L'identifiant UUID v3 de « type|centre|jour du centre » est contrôlé avant l'envoi (`NotificationService.java:607`) sur le journal `notification_evenement.id`, et le temps réel part après commit (`NotificationService.java:641`). Le contrôle de lecture est protégé par le verrou, sauf pour le point du major ci-dessus.
7. **Multi-instances.** La condition de déploiement est écrite dans l'ADR (lot C) et au §56 : pas de flag, une seule instance, ou verrou et identifiant vérifiés sur PG de production.
8. **KB-SEA-37.** Il est en tête de l'ADR 0091, avec une condition de mise en production et un jalon daté au 2026-12-15. La requête de suivi est §56-1, à lancer chaque semaine.
9. **Table V50.** `derogations_regularisation` n'est ni supprimée ni écrite. Aucune référence en code de production : `V50__derogation_regularisation.sql` est le seul fichier concerné. Le suivi se fait par §56-2.
10. **§56.** Les 7 requêtes sont des SELECT. Les colonnes sont vérifiées dans les migrations : `seances` (center_id, patient_id, date_seance, statut, regularisation_deverrouillee_at), `notification_evenement` (center_id, type, payload, cree_le), `derogations_regularisation` (autorite, date_derogation, motif). La jointure `patients(id, center_id)` est cohérente avec le schéma de base. Les n° 5 à 7 n'ont pas été passées sur PG16 : cela est signalé et acceptable. Le `::date` sur `cree_le TIMESTAMP` est valide.
11. **Tests sans horloge système.** Le cas d'usage ne lit jamais l'heure (jour fourni par `HorlogeDuCentre`). Le filet appelle `executerPourLeCentre(centre, jourFixe)`.

## Verdict : pass-with-actions
Aucun blocker.

Top 3 actions :
1. Corriger la présentation du repli sur violation de clé, ou le tester sur PG16, ou le remplacer par `ON CONFLICT DO NOTHING`.
2. Ajouter le filtre « non déverrouillée » à la requête §56-1.
3. Obtenir la décision écrite KB-SEA-37 avant le 2026-12-15 ; aucune mise en production sans elle. Écrire ou jouer la recette à deux instances (§56 e).
