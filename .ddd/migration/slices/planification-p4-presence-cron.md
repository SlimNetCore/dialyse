---
slice: planification-p4-presence-cron
status: built
date: 2026-10-27
order: 4
context: planification
sources: [.ddd/tactical/planification.md (P17 à P19), .ddd/safety-net/planification-p4-presence-cron.md, RG-INF-040 à 047, 050 à 052, 060, 061, ADR 0024, 0049, 0053, 0054, 0057, 0058, KB-PLN-42 à 51]
---

# P4 — Présence, alertes, charge, impression, contrôle de 07:15

Refonte sur place (ADR 0024) : pas de façade, de routage, de projection ni de métrique ; mêmes URLs, aucune migration de schéma.
**Aucun drapeau** : le nouveau code sert directement les 5 routes de lecture (plus une nouvelle) et le job ; le legacy de P4 est supprimé.
Retour arrière = redéploiement de la version précédente (mêmes tables, aucune écriture nouvelle hormis la clé d'alerte).

## Fait

| Couche | Contenu (module `backend/contexts/planification`) |
|---|---|
| domaine | `Presence` (modèle de lecture), `PresenceDeLaSemaine` (cases, alertes, sur-effectif, double affectation), `ClassementDesRemplacants`, `ChargeDesInfirmiers`, `OuvertureDuCentre`, `RatioDeLaPresence` |
| application | `ConsulterLaPresence`, `ClasserLesRemplacants`, `ConsulterLaChargeDesInfirmiers`, `ImprimerLaPresence`, `ControlerLaPresence`, `SurEffectifCalculeParLaPresence` (port P3), ports `PersonnelDeLaPresence`, `CentresActifs` |
| langage publié | ports **consommés** (fournis par le legacy) `PlanningDesPlaces`, `NotificationsDeLaPresence`, `ImpressionDeLaPresence` |
| infrastructure | `PersonnelDeLaPresenceJdbc`, ACL `legacycentres/CentresActifsLegacy` ; `ParametresDuPlanningJdbc` lit un ratio illisible comme le défaut |
| interfaces | `PresenceControleur` (6 routes dont `alertes-sureffectif`), job `ControleDeLaPresenceDe0715` (cron `0 15 7 * * *`, zone Africa/Algiers) ; advices : paramètre manquant 400, impression refusée 422 |
| plateforme (ACL) | `acl/planification/{PlanningDesPlacesLegacy, NotificationsDeLaPresenceLegacy, ImpressionDeLaPresenceLegacy}` (nouveaux, inscrits dans `covered.json`) ; `SemaineDeLInfirmierLegacy` lit `PresenceDonneesPort` ; `NotificationService` : variantes à identifiant imposé |
| legacy retiré | `PresenceInfirmierRestController`, `PresenceInfirmierScheduler`, `PresenceInfirmierReportService`, `PresenceInfirmierQueryService`, `SurEffectifDuRoulementLegacy`, `RemplacantService`, `ChargeInfirmierService` ; `PresenceInfirmierService.alertes` et `alertesSureffectif` ; leurs tests (`PresenceInfirmierSchedulerTest`, `PresenceInfirmierRestControllerTest`, `PresenceInfirmierReportIntegrationTest`, `InfirmierPresenceIntegrationTest`, `ChargeInfirmierServiceTest`, `RemplacantServiceTest`, 3 tests d'alertes) |
| conservé (dette de P4 vers P8) | `PresenceDonneesPort`, `PresenceDonneesJdbcAdapter`, `Presence`, `PresenceInfirmierService.construire/creneauxDe/requis`, `CalendrierCentre` : lus par l'optimisation (P8) et « Mon planning » ; **non morts**, donc non retirés |
| frontend | non touché (JSON inchangé ; la route `alertes-sureffectif` n'est pas encore consommée) : aucun test frontend à relancer |

## Preuves

Voir la réponse à l'appelant pour les nombres de la dernière exécution. Moyens :
- Domaine : `PresenceDeLaSemaineTest` (17), `ClassementEtChargeTest` (5) ; application avec doubles : `PresenceApplicationTest` (14) ;
  contrat du personnel et des centres actifs : double en mémoire (6), JDBC H2 (6), PostgreSQL 16 (7) ; ArchUnit (11) ; `check-arch.ts` : 0 violation.
- PostgreSQL 16 jetable : `backend/scripts/planification-p4-pg16-rejeu.sh <port != 5432>` (constats SELECT-only vides + contrat PG).
- Mutation : `backend/scripts/pit-p4-module.sh` (domaine + application, P1 à P4) : 633 mutants, 93 % tués (587), force des tests 95 %, couverture de lignes 95 % ; survivants : retours `null` d'accesseurs, lambdas de tri, appels de validation.
- Parité : les 75 scénarios d'origine s'exécutent contre le nouveau code (verts, ordre par défaut et aléatoire), plus 3 scénarios ajoutés
  (route des alertes de sur-effectif, sa matrice de droits 11 profils + anonyme, isolation d'un centre dont les données sont illisibles).
  **28 goldens changent par décision** (et non 21 : le premier décompte était faux), renommés `*_nouveau_code.approved.json` (convention de P1/P3,
  appliquée automatiquement par `PlanificationCharacterizationSupport.nom`).
- **Traçabilité des goldens legacy — ce qui a pu être reconstitué.** Les goldens d'origine n'avaient jamais été commités et ont été écrasés par
  l'approbation. Ils ont été **reconstitués le 2026-10-27** en rejouant le filet, dans un `git worktree` jetable créé à partir de HEAD (legacy de
  présence encore présent, worktree supprimé ensuite), avec les scénarios remis dans leurs prémisses d'origine (job appelé par
  `PresenceInfirmierScheduler`, modèle de document créé par la première impression, pas de suppression d'alerte avant la relecture). Les 28
  fichiers obtenus sont dans `p4-presence/_ecarts-legacy/**/*.legacy.json`. Limites : ils proviennent d'un rejeu, non de la sortie du jour de
  la caractérisation (valeurs normalisées identiques, scénarios ajoutés après exclus) ; le scénario « échec d'un centre » est rejoué avec le
  ratio 0 d'origine, son équivalent d'aujourd'hui (`centre_B_illisible_isole`) en est un nouveau, non une adaptation. Les 47 goldens non
  modifiés sont ceux de la caractérisation d'origine, inchangés.

## Écarts de parité décidés (2026-10-27, ADR 0058)

| KB | Décision |
|---|---|
| 42 | paramètres illisibles ou manquants 400 ; page négative 400 ; `size` plafonnée à 100 avec taille effective |
| 43 | droits inchangés ; RG-INF-044 corrigée (matrice verrouillée par les goldens d'autorisation, inchangés) |
| 44 | jour et mois par défaut au fuseau du centre ; cron en zone Africa/Algiers |
| 45, 46 | tous les centres actifs contrôlés ; alerte unique par type, centre et jour du centre |
| 47 | ratio illisible en base lu comme 4 et journalisé ; **décision du 27/10/2026 : on garde 1-20** (amende « 1-99 ») ; 21 à 99 en base à traiter à la main avant déploiement (SELECT n° 1) |
| 48 | remplaçants : case validée (422) |
| 49 | charge triée nom puis prénom ; remplacements ignorés les jours d'absence ou de fermeture |
| 50 | route `alertes-sureffectif` (surplus + doubles affectations) |
| 51 | formats `CSV`, `RTF`, inconnu : 400 ; la lecture ne crée plus le modèle de document |

Scénarios du filet adaptés (pas seulement les goldens) : modèles de document posés par le test (la lecture ne les crée plus) ; réexécution du
contrôle après un nouveau réglage précédée de la suppression de l'alerte du jour ; noms de méthodes reformulés ; le scénario « échec d'un
centre » devient « ratio illisible lu comme le défaut » (l'isolation des échecs est prouvée par `PresenceApplicationTest`).

## Reste à faire / points ouverts

- **Points à valider** : jours d'absence comptés sur jours fermés ; double affectation sans notification à 07:15 et comptée présente dans chaque
  salle ; patient « en sommeil » compté ; **question ouverte** : l'alerte est figée sur la journée (clé centre, jour, type) — un sous-effectif
  apparu après la première exécution du jour ne refait pas d'alerte ; ADR 0058 `proposed` ; 12 termes de glossaire `proposed`.
- Limite documentée : un seul cron à 07:15 Africa/Algiers sert tous les centres ; un centre d'un autre fuseau est contrôlé à une autre heure
  locale (sur son propre jour).
- Isolation d'un centre en échec : prouvée en intégration sans seam (fiche à la qualification illisible dans un centre) et avec doubles
  (échec de la liste des centres actifs rendu dans le bilan, journalisé par le job).
- Dette P4 vers P8 : le calcul de présence existe en double (module et `bc-seance`) ; **retrait au plus tard avec P8** (avant P9), critère : plus
  aucun lecteur de `PresenceDonneesPort` ; pas de date calendaire dans la feuille de route, à fixer au gate du plan de P8. « Mon planning »
  lit encore la présence legacy (double affectation à l'ancien comportement).
- `PlanningDesPlacesLegacy` et `CalendrierDuCentreLegacy` disparaissent avec P5 ; `DirectionCapaciteQueryService` n'est pas touché.
- P8 écrit toujours roulement et remplacements par SQL direct sans juger le non-chevauchement (KB-PLN-40) : non traité ici.
- La route `alertes-sureffectif` n'a pas d'écran ; aucun test Playwright/ng serve (lancés par l'utilisateur).

## Plan de bascule et retour arrière

Pas de bascule progressive (ADR 0024) : déploiement unique. **Avant** : contrôles SELECT-only de `verifications-production.md` (P4 : ratios hors
1-20, centres actifs sans infirmier, doublons d'alertes, modèles désactivés ou au format non servi, fuseaux). **Surveillance** : statuts 400 sur
`/api/v1/infirmiers/presence/**`, journal `[PRESENCE]`, unicité des alertes `INFIRMIER_SOUS_EFFECTIF` et `INFIRMIER_SUREFFECTIF` par centre et
par jour. **Retour arrière** : redéploiement de la version précédente (aucun changement de schéma).

## Seams

Édition minimale du legacy autorisée par l'utilisateur (2026-10-27), inscrite dans `.ddd/safety-net/seams.md` **avant** les éditions (fichiers
déjà dans `covered.json`, les nouveaux ACL ajoutés) ; éditions par Read/Edit/Write, le hook de garde est resté actif. **Seam fermé** à la fin de
la slice ; `seamAllowed` vide.
