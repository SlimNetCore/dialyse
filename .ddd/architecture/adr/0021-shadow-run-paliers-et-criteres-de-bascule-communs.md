---
status: superseded by 0024
date: 2026-10-09
decision-makers: [architecte, direction technique, experts métier GMAO, exploitation]
context-scope: global
research: []
---
# 0021 — Basculer chaque slice par centre : shadow sans effet de bord, puis paliers de centres, avec critères chiffrés et retour arrière en une ligne

## Contexte et problème
La façade (ADR 0017) route par centre ; les données ont un propriétaire par centre (ADR 0018). Il faut un protocole commun, mesurable, pour passer de `off` à `on` sans régression silencieuse, et le même vocabulaire de critères dans chaque slice (`.ddd/migration/slices/*`).

## Options considérées
1. Bascule directe après tests de parité — contre : le trafic réel révèle des cas non couverts.
2. Canary par pourcentage de requêtes — contre : un centre verrait un état mixte (écritures nouveau/legacy) : viole « un propriétaire par centre ».
3. **Shadow (copie asynchrone, sans effet de bord) puis paliers par centre ; lectures éventuellement par %** — pour : cohérent avec ADR 0018 ; contre : peu de centres = granularité grossière.

## Décision
Option 3. Protocole :
- **Shadow** (`mode=shadow`) : le legacy répond ; la façade rejoue la requête de façon asynchrone sur le nouveau. Lectures : comparaison JSON normalisée (scrubbers dates/UUID/ordre). Écritures : exécution **dry-run** (transaction annulée, événements d'intégration supprimés, aucune écriture hors schéma de la slice). Durée mini : 10 jours ouvrés **et** ≥ 200 requêtes comparées par endpoint (ou 100 % des cas du filet si le trafic est plus faible).
- **Paliers** (`mode=on`, par centre) : centre pilote (7 j) → ≈ 25 % des centres (7 j) → ≈ 50 % (7 j) → 100 %. Lectures seules : % de requêtes autorisé en plus.
- **Critères de passage de palier** (tous requis) : 0 divergence non expliquée par une ligne `KB-GMAO-nn` ; taux d'erreurs 5xx ≤ celui du legacy sur la même fenêtre + 0,1 pt ; latence p95 ≤ 1,2 × legacy ; `ecarts_reconciliation` = 0 ; `lag_projection` p99 ≤ 5 s ; 0 incident d'isolation de centre.
- **Retour arrière immédiat** si : écart de réconciliation > 0 sur une donnée financière, 5xx > 2 × legacy sur 15 min, fuite inter-centres, divergence de statut d'état d'équipement. Geste : gel d'écriture du centre → `lag_projection = 0` → ligne de routage `off` → resynchronisation legacy→nouveau avant toute réactivation (ADR 0018).
- **Observation** après 100 % : 30 jours (60 pour G3, G4 : effets inter-contextes/argent) sans retour arrière ni divergence, puis retrait.

## Conséquences
+ critères identiques et mesurables ; − la couverture réelle dépend du nombre de centres (inconnu, question ouverte roadmap) ; ⚠ si < 4 centres, les paliers 25/50 % fusionnent, la durée d'observation n'est pas réduite.

## Révision
Après G1 (durées et seuils réels) ; seuils ajustables par ADR de remplacement.

## Amendement 2026-10-10 — critère « fuite inter-centres » et risques acceptés
Le critère de retour arrière « fuite inter-centres » et le critère de palier « 0 incident d'isolation de centre » **excluent les comportements déjà consignés comme risque accepté** par l'utilisateur, à ce jour **KB-GMAO-15** (accord du 2026-10-09, ADR 0019 amendé) : ils ne déclenchent ni retour arrière ni blocage de palier. Tout autre accès inter-centres (nouveau bug, régression, accès à une autre donnée) reste un critère. Un cas douteux est une question ouverte à trancher avec l'utilisateur avant le palier.
