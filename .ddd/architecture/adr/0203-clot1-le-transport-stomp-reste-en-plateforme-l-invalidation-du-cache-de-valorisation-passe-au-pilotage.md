---
status: accepted (CLOT1, action M3 de la revue globale 2026-11-15)
date: 2026-11-15
decision-makers: [utilisateur (directive CLOT1), architecte]
context-scope: platform (infrastructure/websocket, infrastructure/stock), pilotage-direction
related: ADR 0024, 0104, 0140 (D7), 0152 ; revue `.ddd/reviews/2026-11-15-global-decommissionnement-platform.md` M3
---

# 0203 - STOMP : le transport reste en plateforme, l'invalidation du cache de valorisation passe au Pilotage

## Contexte
`StockEvenementsListener` (platform) faisait deux choses : relayer `MouvementsDeStockModifies` / `VerrousDeRecalculModifies` vers STOMP, et vider le cache `stock.valorisation.articles`, propriété du Pilotage direction (`StockDesGroupesParLeStock.CACHE`). Le pom de `pilotage-direction` dépend déjà de `contexts-stock` : l'écouteur peut vivre dans le Pilotage sans cycle.

## Décision
1. **Invalidation déplacée** : `InvalidationDeLaValorisationDuStock` (pilotage-direction, `infrastructure/acl/stock`, câblé dans `PilotageDirectionConfiguration`) écoute le langage publié de Stock (`@TransactionalEventListener AFTER_COMMIT`, `fallbackExecution`) ; mêmes règles qu'avant (recalcul `STARTED` n'invalide pas). `StockEvenementsListener` ne touche plus aucun cache.
2. **Relais conservés en plateforme, assumés comme noyau de transport (ADR 0140 D7)** : `StockEvenementsListener`, `RelaiDesInventairesVersWebSocket`, `WebSocketDirectionPublisher`. Ils ne contiennent plus de règle : chacun traduit un événement/port publié en un envoi sur un topic fixe (`/topic/center/{id}/events`, `/topic/societe/{id}/dashboard`). Les déplacer exigerait que chaque contexte dépende de `spring-messaging` ou d'un nouveau port d'émission générique, et changerait la responsabilité des topics sans bénéfice ; topics et payloads sont des contrats observés par le front et les goldens, ils restent inchangés.
3. **Condition de retrait** : événement, pas date. Les relais suivent le sort du transport STOMP (retiré seulement si le temps réel quitte la plateforme).

## Conséquences
+ la plateforme ne connaît plus le cache du Pilotage ; goldens et contrats WebSocket inchangés.
- Les trois relais restent des classes de la plateforme qui importent le langage publié de Stock et du Pilotage (dépendance autorisée : `platform -> contexts-*`).
