---
status: proposed
date: 2026-11-02
decision-makers: [utilisateur (décisions du 2026-10-28, communiquées pour la slice P8 ; à valider), architecte]
context-scope: planification
related: ADR 0024, 0049, 0050, 0051, 0056, 0057, 0061 ; `.ddd/tactical/planification.md` P8 ; KB-PLN-89 à 105 ; revue `.ddd/verify/review-planification-p8.md` ; `.ddd/safety-net/seams.md`
---

# 0068 — Planification P8 : verrou du centre en base, reprise des calculs par instance, application tout ou rien, empreinte élargie, optimiseur injectable

> Numérotation : la demande visait « 0062 » ; 0062 et 0063 ont été pris entre-temps par d'autres slices (seance, organisation).
> Cet ADR prend le prochain numéro libre.

## Contexte et problème

L'optimisation (lancement d'un calcul Timefold, suivi, arrêt, application d'une proposition, replanification de 02:30) a été
caractérisée sur le legacy (39 scénarios, KB-PLN-89 à 105) puis refaite sur place dans `contexts/planification`. Le filet
a montré un verrou « un seul calcul » en mémoire (KB-PLN-92), des 500 pour des entrées illisibles, des écritures de places sans
agrégat et une application silencieuse. La revue indépendante a de plus trouvé que la première version du verrou en base
tenait mal avec plusieurs instances (B1).

## Décisions

1. **Un seul calcul par centre, verrou EN BASE** (`VerrouDesPlaces` : ligne `planning_parametres FOR UPDATE`, `lock_timeout` 5 s),
   y compris avec plusieurs instances. Deux lancements simultanés : un 202 et un 409 `OPTIMISATION_DEJA_EN_COURS` (jamais
   `PLANIFICATION_ECRITURE_CONCURRENTE` quand le verrou est tenu par un autre lancement ; ce code reste pour un verrou tenu par
   autre chose). Le front affiche un message dédié pour ces codes.
2. **Cycle de vie gardé** : `ExecutionDOptimisation.progresser/terminer/echouer` ne partent que de `EN_COURS`
   (`OPTIMISATION_TRANSITION_INTERDITE`) ; en base, les transitions sont des UPDATE conditionnels (`WHERE statut = ?`, et
   `AND instance_id = ?` pour un calcul en cours), jamais un upsert : un calcul tardif ne ressuscite ni n'écrase un état final, une
   exécution supprimée n'est pas recréée. `SupprimerUneExecution` prend le verrou du centre.
3. **Reprise par instance** : chaque démarrage tire un jeton d'instance (`planification_optimisation.instance_id`, V48) ; un
   battement de cœur (`battement_le`) est renouvelé toutes les 10 s pour les calculs en cours de l'instance ; la reprise (au démarrage
   puis toutes les 10 s) n'interrompt que les calculs ORPHELINS (sans battement depuis 60 s, ou sans battement du tout). Elle ne touche
   jamais le calcul d'une instance vivante. Contrat H2 et PostgreSQL 16 de la course lancement / reprise / terminaison
   (`OptimisationContrat`, deux instances sur la même base). Conséquence : après un arrêt brutal, le centre reste « en cours »
   jusqu'à 70 s au plus.
4. **Application tout ou rien, UNE transaction** (exception à « une transaction = un agrégat », ADR 0051) : places via
   `PlacesDuCentre`, roulement via `RoulementDeLInfirmier` (refus `AFFECTATION_CHEVAUCHEMENT`, 422), remplacements via `Remplacement`
   et `RemplacantEligible` (refus `REMPLACEMENT_INELIGIBLE`, 422), déplacements temporaires via `DeplacementTemporaire` (cible
   incohérente : `DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE`, 422). Les règles du remplacé relié et de l'éligibilité vivent dans le
   domaine (`CouvertureDesAbsences`). Verrous : centre, puis fiches des infirmiers par identifiant croissant (remplaçants ET
   remplacés possibles). L'empreinte est relue sous verrou.
5. **Codes HTTP** (décisions du 28/10) : 409 `OPTIMISATION_DEJA_EN_COURS`, `OPTIMISATION_DEJA_APPLIQUEE`, `OPTIMISATION_PERIMEE` ;
   404 `OPTIMISATION_INTROUVABLE` (y compris l'exécution d'un autre centre) ; 400 pour une entrée illisible ; autres règles : 422.
   `VALIDATION_ERROR` devient `BAD_REQUEST` (400) pour le corps vide : le code générique de l'application remplace celui de la
   validation Bean, par cohérence avec les entrées illisibles (décision d'API, golden `validations_du_lancement_nouveau_code`).
   `page` négative : 400 ; `size` plafonné à 100. 403 avant la lecture du corps (autorisation d'abord). Droits inchangés.
6. **Empreinte élargie** : la proposition est périmée si change tout ce que le calcul a lu (jours, places, patients à placer,
   générateurs, interventions GMAO, salles, créneaux, personnel, absences, roulement, remplacements, réglages). Une proposition
   calculée avant la montée de version est périmée (voulu).
7. **Notifications** : `SEANCES_DEPLACEES` part après le commit (jamais d'alerte fantôme) ; `INFIRMIER_ROULEMENT_MODIFIE` et
   `INFIRMIER_REMPLACEMENT_AFFECTE` sont des alertes PERSONNELLES : le compte lié à la fiche de l'infirmier et l'ADMIN du centre
   (colonne `notification_evenement.target_user_id`, V49) ; sans compte, l'ADMIN seul ; le canal temps réel ne porte que la
   cible (le contenu se lit au journal). Identifiants déterministes (une alerte par exécution, infirmier et case).
8. **Replanification de 02:30** : centres actifs (`COALESCE(actif, TRUE)`) ayant activé l'option ; « aujourd'hui » est le jour du
   centre (`HorlogeDesCentres`) ; zone `app.planification.fuseau-par-defaut`.
9. **Optimiseur injectable** : le solveur est derrière le port `Optimiseur` (ADR 0050) ; les tests d'application répondent par
   l'écouteur (déterminisme, RG-PLN-090) ; Timefold 2.7.0 reste dans `infrastructure/optimisation/timefold` (poids et contraintes
   identiques à ceux du legacy : relevé de la revue). Le solveur est exclu de la mutation.
10. **Déplacement temporaire à propriétaire unique** : `DeplacementTemporaire` appartient au contexte (écrit par l'application d'une
    proposition de maintenance, lu par la séance et le planning) ; un par (centre, patient, date) ; table partagée en lecture seule
    par les lecteurs P6 (`PlanningDonneesJdbcAdapter`, `SeancePlaceJdbcAdapter`) jusqu'au jalon P9.
11. **Horloge** : plus d'`Instant.now()` dans un adapter de la slice (`Clock` injectée).

## Écarts de la session S1 (revue : « écarts S1 (1) à (6) »)

| # | Écart | Décision |
|---|---|---|
| 1 | Code d'un roulement qui chevauche : `AFFECTATION_CHEVAUCHEMENT` (422), code du P3 | retenu |
| 2 | Couverture jugée par `RemplacantEligible` ; peut refuser une proposition que le legacy appliquait | retenu : le legacy écrivait sans règle (KB-PLN-97) ; le filet de parité n'a montré aucun cas appliqué refusé |
| 3 | Remplacé relié (absent du jour prévu sur la case, pas déjà remplacé, identifiant croissant) | retenu (KB-PLN-97) |
| 4 | Verrou pris pour TOUS les périmètres à l'application (legacy : places seulement) | retenu (double application impossible) |
| 5 | Fiches verrouillées avant la relecture de l'état | retenu (empreinte jugée sous verrou) |
| 6 | Premier refus de place dans l'ordre des déplacements de la proposition (legacy : ordre d'une `HashMap`) | retenu (déterminisme) |
| + | Empreinte élargie | retenu (décision 6) |

## Goldens `_nouveau_code` (motif de chaque écart)

Chaque golden relu et approuvé un par un ; les 103 goldens legacy de 77aca8dd sont inchangés. Trois goldens doublement suffixés,
issus d'une approbation en lot d'une exécution cassée (500 partout), ont été supprimés ; un test de garde
(`GoldensNouveauCodeGardeTest`) refuse désormais un nom doublement suffixé et un golden fait uniquement de 5xx.

| Golden | Motif |
|---|---|
| `lancement/{deuxieme_lancement_refuse, lancements, arrets, suppression, garde_de_centre, impression, calendrier_*}` | décision 5 : 409/404/400, 403 avant le corps |
| `lancement/deux_lancements_simultanes` | décision 1 : 409 `OPTIMISATION_DEJA_EN_COURS` |
| `lancement/validations_du_lancement`, `consultations_mal_formees` | décision 5 : 400 `BAD_REQUEST`, `size` ≤ 100, `page` ≥ 0 |
| `donnees/routes_par_profil` | décision 5 (codes) |
| `application/{executions_non_applicables, seconde_application_*, audit_*}` | décision 5 : 409 |
| `application/{notifications_*, effet_de_chaque_changement_*}` | décision 7 (infirmiers notifiés) |
| `application/remplacements_apres_*` | décision 4 (remplacé relié) |

## Conséquences

- Positives : un seul calcul par centre prouvé sur H2 et PG16, y compris à deux instances ; aucune sortie d'un état final ; alertes
  ciblées ; règles de couverture testées au domaine.
- Négatives : un calcul orphelin bloque le centre jusqu'à 70 s ; migrations V48 et V49 ; l'alerte personnelle se lit au journal
  (une relecture de plus côté front).
- Reporté au jalon P9 (voir `roadmap.md`) : retrait du double calcul de présence (`SemaineDeLInfirmierLegacy` et ses lecteurs).

## Questions ouvertes

- Délai d'orphelin (60 s) et battement (10 s) : à confirmer à l'exploitation.
- `kab` : textes anglais provisoires pour les alertes et erreurs ajoutées, à relire.
