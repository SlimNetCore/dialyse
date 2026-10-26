---
status: proposed
date: 2026-10-26
sources: git diff (non commité) slice stock-s8-peremptions-retrait, ADR 0046, fiche S8, known-bugs KB-STK-53..56, check-arch, scan --incremental
---
# Revue indépendante — slice stock-s8-peremptions-retrait (refactoring sur place, ADR 0024)

## Contrôles automatiques
- `check-arch` : **0 violation** (0 baseline, 0 nouvelle).
- `scan --incremental` : 0 cycle entre modules.
- Aucune référence résiduelle à `com.hemodialyse.backend.domain.stock|article` dans le code ; elle n'apparaît que dans la règle ArchUnit `FrontieresDesContextesTest.java:75`. `bc-article` est retiré de `backend/pom.xml`, de `platform/pom.xml`, de `bc-seance/pom.xml` et du `Dockerfile`.
- Seam fermé (`covered.json` → `seamAllowed: []`) ; tous les fichiers legacy modifiés figurent dans `covered` (dont `SeanceDomainService.java` l.233 et `NotificationService.java` l.242).

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | legacy | **Violation.** Le refus d'un article inactif à la **validation** d'une séance passe de 422 à 400 (`IllegalStateException` → `IllegalArgumentException`). Ce changement n'a ni golden, ni test unitaire, ni ligne KB : KB-STK-56 couvre seulement les raccourcis et l'ajout de consommable, et la fiche le laisse en « point à valider ». C'est un changement de comportement sans filet ni décision écrite (règle 20-legacy-safety §4). | `bc-seance/.../SeanceDomainService.java:273-274` ; `known-bugs.md:946,955` ; fiche `stock-s8-peremptions-retrait.md:87` ; aucun test « inactif » dans `SeanceDomainServiceTest.java` | Faire valider par l'utilisateur, puis ajouter un golden ou un test unitaire (`valider` avec article inactif → 400) et compléter KB-STK-56 et l'ADR 0046. Sinon, revenir à `IllegalStateException` à cet endroit. |
| minor | tactique | Rappel hebdomadaire sans mémoire : si le déclencheur de 07:00 n'a pas tourné à J+1 (panne, redéploiement), un lot qui vient de périmer reste silencieux jusqu'à J+8. Cette limite n'est pas documentée : l'ADR et la fiche ne citent que le cas du « lot reçu déjà périmé ». Le calcul J+1, J+8, J+15 est par ailleurs exact et testé. | `RegleDesPeremptions.java:42` ; `ADR 0046:29` ; fiche `:37-38` | Documenter cette limite dans l'ADR et dans le point 30 de `verifications-production`. Facultatif : surveiller l'absence d'exécution. |
| minor | archi | Multi-instance : `journal.existe` puis `enregistrer` forment un check-then-act. La 2ᵉ instance reçoit une erreur de clé dupliquée, relancée car l'identifiant est imposé, et cela produit un `log.error` « Contrôle impossible » alors que l'alerte a bien été émise une seule fois. Les événements v1 sont publiés deux fois (même identifiant, donc pas d'effet). La fiche affirme « sûr en multi-instance ». | `NotificationsDesPeremptionsLegacy.java:169-173` ; `NotificationService.java:468,485` ; `ControleDesPeremptionsPlanifie.java:182` | Traduire `DuplicateKeyException` en « déjà émise » (`return false`) dans l'ACL, ou poser un verrou de planification. |
| minor | tests | Le test de caractérisation et l'IT contrat dépendent de la date réelle : le jour est calculé par le test, puis recalculé par le cas d'usage. Ils peuvent échouer de façon aléatoire au passage de minuit (UTC, ou Alger, soit 23:00 UTC, pour l'IT). Les tests unitaires, eux, utilisent un jour fixe (`ControlerLesPeremptionsTest.java:41`). | `ControleDesPeremptionsCharacterizationIntegrationTest.java:130` ; `PeremptionsPostgresContractIT.java:89,112` | IT : injecter `Clock.fixed(...)` dans `HorlogeDesCentresLegacy` (le constructeur l'accepte déjà). Caractérisation : garde « relire le jour après exécution », ou horloge fixe. |
| minor | stratégique | **Suggestion.** `LotProcheExpiration` n'a aucun consommateur de production. Sans écouteur transactionnel, le registre Modulith n'inscrit rien : l'« Outbox » et l'unité de travail qui l'enveloppe sont donc sans effet en production, et la garantie « au moins une fois » n'existe pas. L'ADR le reconnaît. Coût actuel quasi nul. Volume futur : jusqu'à 31 événements par lot dans la fenêtre J−30…J, plus un par semaine par lot périmé. | `ControlerLesPeremptions.java:138-141` ; `EvenementsDePeremptionSpring.java:21` ; `ADR 0046:36-38` | À conserver (décision utilisateur), mais indiquer dans le catalogue `tactical/stock.md` : « garantie effective dès le premier écouteur ». Ne pas présenter la notification comme dépendante de l'Outbox. |
| nit | archi | Fuseau : un centre sans `fuseau_horaire` est en UTC. Le cron de 07:00 Alger tombe à 06:00 UTC, donc à la même date : aucun écart pour les centres UTC ou UTC+1. Seul un centre à UTC−7 ou au-delà obtiendrait « hier ». C'est cohérent avec le reste du Stock. | `HorlogeDesCentresLegacy.java:16` ; `ControleDesPeremptionsPlanifie.java:174` | Rien à faire ; une phrase dans l'ADR suffit. |
| nit | archi | La charge utile `lots` (10 lignes de détail) est construite et persistée, mais l'écran ne l'affiche pas : il n'utilise que `count`, `perimes` et `date`. Le profil ADMIN est renvoyé vers l'accueil, pas vers `/stock`. | `NotificationsDesPeremptionsLegacy.java:151-164` ; `notification-presentation.util.ts:272-275` | Afficher le détail, ou ne plus l'envoyer (YAGNI). Confirmer la cible ADMIN. |
| nit | legacy | `ArticleJpaEntity` n'a plus aucune référence. Elle est conservée pour créer le schéma H2 de test, mais pilote aussi `ddl-auto: update` en profil par défaut et `validate` en prod. C'est documenté, sans date de retrait. | `persistence/entity/ArticleJpaEntity.java:12` ; `application.yml:12` ; fiche `:30,88` | Fixer une date de retrait : schéma H2 par script, puis suppression. |

## Vérifiés conformes
- `RegleDesPeremptions` : J+30 inclus, J+31 exclu, périmé le lendemain puis tous les 7 jours, pur et sans horloge, couvert par 6 tests.
- Centres actifs seulement (`CentresActifsLegacy.java:24`, ordre stable) ; articles inactifs exclus (`ControlerLesPeremptions.java:126-129`).
- Identifiants déterministes : événement (lot, jour) et alerte (centre, jour).
- Échec d'un centre isolé (`ControlerLesPeremptions.java:114-119`).
- Câblage uniquement dans `StockContexteConfiguration`.
- La Séance ne dépend que du langage publié (règle ArchUnit `FrontieresDesContextesTest.java:81-88`).
- 400 pour une date illisible, limité aux deux routes (`DatesIllisiblesDeLaDirectionAdvice`).
- i18n présente en fr/en/ar/kab (la formulation kabyle reste à valider, question ouverte de l'ADR 0046:76).

## Verdict : **pass-with-actions**
Top 3 actions :
1. Faire trancher le 400 à la validation de séance par l'utilisateur, puis le couvrir par un test et le tracer dans KB-STK-56 et l'ADR 0046.
2. Rendre l'ACL de notification tolérante à la clé dupliquée (pas de faux `log.error` en multi-instance).
3. Utiliser une horloge fixe dans `PeremptionsPostgresContractIT` (et dans la caractérisation si possible), et documenter la limite « jour J+1 manqué → J+8 ».
