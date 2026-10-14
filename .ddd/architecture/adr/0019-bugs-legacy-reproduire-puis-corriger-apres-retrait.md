---
status: proposed
amended-by: 0024 (plus de Strangler progressif : refactoring sur place)
date: 2026-10-09
decision-makers: [experts métier GMAO, architecte, direction technique]
context-scope: global
research: []
---
# 0019 — Reproduire à l'identique les comportements legacy jusqu'au retrait de la slice, corriger ensuite par changement tracé

## Contexte et problème
Le modèle tactique GMAO liste des écarts entre legacy et cible (`tactical/gmao.md` Q1-Q13) : annulation répétée acceptée, réforme bloquant dans la transaction, statuts morts, date de prochaine exécution, etc. Corriger « au passage » ruinerait la parité (tests de caractérisation, shadow) et rendrait le rollback incomparable (règle 20-legacy-safety n°4).

## Options considérées
1. Corriger dès le nouveau code — contre : parité invérifiable, divergences shadow non explicables.
2. **Reproduire pendant la migration, corriger après retrait du legacy de la slice** — pour : parité mesurable ; contre : le bug vit quelques semaines de plus.
3. Tout reproduire sans jamais corriger — contre : le nouveau modèle hérite du legacy.

## Décision
Option 2. Chaque écart est consigné dans `.ddd/safety-net/known-bugs.md` avec : id `KB-GMAO-nn`, comportement observé, décision `reproduire` (défaut) ou `corriger-maintenant` (exception), slice, date de correction prévue. Le fichier approuvé de caractérisation décrit le comportement legacy ; la correction est un commit séparé (`fix(gmao): KB-GMAO-nn`) après la date de retrait, qui modifie l'approved file et le référentiel de règles. `corriger-maintenant` n'est admis que pour : perte/corruption de données, faille de sécurité ou d'isolation de centre, écart financier — sur décision écrite de l'utilisateur.

Propositions par question (à valider au gate `plan`, détail dans chaque slice) :
| Q | Sujet | Décision proposée | Slice |
|---|---|---|---|
| 1 | Sémantique `ÉquipementMisHorsService` | un événement `gmao.v1` + champ `cause` ; l'ACL reproduit la règle `GenerateurIndisponibleService:39-41` | G3 |
| 2 | Annuler une intervention annulée | reproduire (accepté), KB, refus après retrait | G4 |
| 3 | Équipement réformé + démarrer/terminer | reproduire le refus synchrone par pré-vérification (et non « ignorer » de la cible), KB sur l'écart de transaction | G4 |
| 4 | `SalleFermée` | hors périmètre GMAO v1 | — |
| 5 | Seuil de réforme | global (parité) ; par centre = évolution après retrait | G6 |
| 6 | Statuts `DESACTIF`/`ARCHIVE` morts | conservés dans l'enum, sans commande ; compter les lignes en prod avant G1/G3 | G1, G3 |
| 7 | Pièce ↔ Stock | Separate Ways (confirmer avant G4) | G4 |
| 8 | Quota 50 pièces | course acceptée (parité) | G5 |
| 9 | Casse/espaces de `CodeEquipement` | reproduire le legacy (à établir par caractérisation) | G3 |
| 10 | Snapshot nature intervenant | oui (nouveau comportement non observable) | G4 |
| 11 | Préventive ↔ exécution du plan | indépendants (parité) | G1, G4 |
| 12 | Prochaine date depuis l'exécution | reproduire (`now()` + fréquence) | G1 |
| 13 | Termes *(proposed)* | à valider avant G1 | G1 |

## Conséquences
+ divergences shadow toutes explicables par une ligne KB ; − dette consciente et datée ; ⚠ une KB sans date de correction après retrait est escaladée au point d'étape de la roadmap.

## Révision
À chaque fin de slice : les KB de la slice passent en `corrigé` ou sont renégociées.

## Amendement 2026-10-10 — KB-GMAO-15 (isolation de centre) : risque accepté
KB-GMAO-15 (un ADMIN lit et modifie les plans d'un autre centre) est **reproduit** : risque **accepté par l'utilisateur le 2026-10-09**, correction `fix(gmao): KB-GMAO-15` après retrait du legacy de la slice. Conséquence : « fuite inter-centres » **n'est pas** un critère de rollback ni de non-passage de palier pour KB-GMAO-15 (voir amendements ADR 0021 et fiche G1). Toute autre fuite inter-centres reste un critère. Le routage par palier suit le centre **propriétaire du plan** et non celui de la session (un seul écrivain par plan, ADR 0018). En cas d'ambiguïté sur ce qui relève de KB-GMAO-15 : question ouverte à trancher avec l'utilisateur, pas de décision implicite.

## Amendement 2026-10-10 — KB-GMAO-14 / E-PG-1 (équipement inconnu) : REFUSER PROPREMENT
**Décision de l'utilisateur (2026-10-10).** Pour un équipement inconnu, le nouveau code ne crée plus de plan orphelin. Le legacy répond 500 `DATA_ACCESS_ERROR` par violation de la FK `fk_gmao_plan_equipement` sous PostgreSQL (constaté sur PG16 réel) et crée un orphelin sous H2. Le nouveau code vérifie l'existence via le port applicatif `EquipementExiste` (ACL `EquipementExisteLegacyJdbc` : lecture seule de `public.gmao_equipements`, aucune écriture legacy, même critère que la FK : une ligne supprimée logiquement reste référençable) et refuse : **HTTP 400**, `code=EQUIPEMENT_INCONNU`, `title=Requete invalide`, `detail=Équipement non trouvé`.
- **400 et non 404** : cohérence avec le legacy pour une référence inconnue dans une commande (KB-GMAO-16 : plan inconnu sur une commande, 400 ; message « Équipement non trouvé » déjà renvoyé en 400 par le legacy ailleurs). Le 404 reste réservé aux `GET` d'une ressource (`GET /{id}` d'un plan inconnu).
- **Écart de parité accepté et testé, non masqué** : 1 scénario du filet (`creation_sans_controle_d_existence_…`). `PlanMaintenanceParityIntegrationTest` redéfinit la vérification (golden `etablir_equipement_inconnu_refuse_400_nouveau_code`) ; `fkEquipementActive()` reste vrai (équivalent fonctionnel de la FK dans le nouveau code, identique sous H2 et PG16).
- **Conséquence sur la projection nouveau -> legacy** : un équipement supprimé ou inexistant au moment de la projection n'est plus un échec silencieux : état « en échec » persistant (`gmao.projection_plan_legacy`, ancienne migration V36 additive, supprimée ; ne pas confondre avec la V36 actuelle des tâches), reprise avec backoff exponentiel (10 s doublé, plafond 15 min), compteurs d'échecs, métriques `lag_projection` (cible p99 <= 5 s), `gmao_projection_echecs`, `gmao_projection_en_echec`, journal ERROR.

## Amendement 2026-10-10 — Slice G2 (intervenants) : KB-GMAO-22 corrigé, KB-GMAO-30 et KB-GMAO-27 reproduits
**Décisions de l'utilisateur (2026-10-10).**
- **KB-GMAO-22 (`GET /intervenants/{id}` absent, 500) — CORRIGER dans le nouveau code, écart de parité accepté et testé** : intervenant inconnu ou d'un autre centre (indistinguables) -> **404 propre** ; intervenant existant du centre -> **200** (sens minimal d'un `GET /{id}`, signalé en question ouverte Q-G2-1 pour validation) ; identifiant illisible -> 400 (comme KB-GMAO-23) ; non-administrateur -> 403. Tant que le centre n'est pas basculé, le legacy répond toujours 500 : l'écart n'existe que sur le nouveau code. `IntervenantParityIntegrationTest` redéfinit la vérification (goldens `consulter_par_id_*_nouveau_code`, y compris autre centre) ; aucun masquage.
- **KB-GMAO-30 (`PUT` = remplacement total, téléphone, email et tarif omis effacés) — REPRODUIRE à l'identique** ; correction à planifier — date à fixer par l'utilisateur (le retrait du legacy a lieu au déploiement, ADR 0024), décision métier (client UI à adapter avant).
- **KB-GMAO-27 (longueurs non validées -> 500 `DATA_ACCESS_ERROR`) — REPRODUIRE à l'identique** : la table `gmao_intervenants` est inchangée (nom/email 255, téléphone 50, tarif `NUMERIC(10,2)`), l'API ne valide rien de plus ; correction (400 + unicité du nom) à planifier — date à fixer par l'utilisateur (le retrait du legacy a lieu au déploiement, ADR 0024).
- Autres KB-GMAO-23..26, 28, 29, 31 : reproduire par défaut (ADR 0019). **KB-GMAO-10 (snapshot de la nature/du tarif à la clôture) reste hors G2** (G4).
- **Argent** : le tarif horaire valorise le temps d'intervention. La base arrondit à 2 décimales, demi supérieur, comme le legacy ; la clôture legacy lit la même table `gmao_intervenants` (KB-GMAO-31).

## Amendement 2026-10-12 — Refactoring sur place (ADR 0024) et KB-GMAO-15 corrigé
- Les amendements ci-dessus qui parlent de projection, rattrapage, réconciliation, palier, shadow, `gmao.projection_plan_legacy`, V36 (l'ancienne, projection ; la V36 actuelle est la migration des tâches d'intervention) ou V38 décrivent l'ancienne approche (ADR 0017/0018/0021/0023, superseded par ADR 0024) : historique seulement. Il n'y a plus de « retrait du legacy de la slice » progressif : le contrôleur legacy est supprimé au déploiement.
- **KB-GMAO-15 (isolation de centre des plans) : décision utilisateur 2026-10-12, CORRIGÉ dans la livraison G1**, en remplacement du risque accepté le 2026-10-09 (voir `known-bugs.md` et la fiche G1). Un plan d'un autre centre est traité comme inexistant : 404 sur les `GET`, 400 « Plan non trouvé » sur les commandes (comme KB-GMAO-16).
- **KB-GMAO-27 et KB-GMAO-30** : la date « ≥ 2027-05-31 » (liée au Strangler) est supprimée. Correction à planifier — date à fixer par l'utilisateur (le retrait du legacy a lieu au déploiement, ADR 0024).
## Amendement 2026-10-14 — Échéance des corrections (décision utilisateur)
- Les bugs reproduits pendant le refactoring sur place sont à corriger **dans la livraison qui suit le déploiement de GMAO** (une seule slice de correction, avec ses goldens `*_nouveau_code`) : **KB-GMAO-17** (plan supprimé logiquement « ressuscité » par une commande), **KB-GMAO-27 et KB-GMAO-30** (intervenants : longueurs → 500, tarif omis effacé par le PUT), **KB-GMAO-44** (montants renvoyés à 4/5 décimales), **KB-GMAO-46** (tarif et nature lus à la clôture, sans snapshot), **KB-GMAO-80 à 83** (alerte générateur non dédupliquée, reprise de patients sur générateur indisponible, compte rendu trompeur, patient sorti sans date ignoré).
- Les arbitrages métier restent à confirmer par les experts avant cette slice (notamment KB-46 : snapshot du tarif et de la nature, et KB-81 : refuser ou avertir). Aucune date calendaire n'est fixée ici : elle dépend de la date de déploiement.

- Le port `EquipementExiste` (KB-GMAO-14) est servi par le repository des équipements du module (G3 propriétaire de `gmao_equipements`) ; l'ACL JDBC `EquipementExisteLegacyJdbc` est supprimée. Même critère : un équipement supprimé logiquement reste référençable.
