---
status: accepted (CLOT1, action M2)
date: 2026-11-15
decision-makers: [utilisateur (directive CLOT1), architecte]
context-scope: platform (db/migration), tous contextes
related: ADR 0014 (précisé), 0184 ; revue globale 2026-11-15 M2
---

# 0205 - Flyway centralisé en plateforme (V1-V59) : une seule base, un seul historique

## Contexte
`backend/platform/src/main/resources/db/migration` contient 57 fichiers (V1-V59) dont les DDL de gmao, stock, facturation, comptabilité, absences, planification. La revue (M2) relève une contradiction apparente avec l'ADR 0014 (« schéma par contexte »).

## Vérification de l'ADR 0014
L'ADR 0014 décide déjà les migrations « dans la **chaîne unique existante** (numérotation globale) tant que le contexte tourne dans le monolithe » et réserve la chaîne propre à l'extraction physique. En revanche, son titre promet un schéma PostgreSQL par contexte : aucun `CREATE SCHEMA` n'existe dans les migrations ; les tables vivent dans `public`, préfixées par contexte. Cette partie n'a jamais été appliquée.

## Décision
1. **Une seule base, un seul historique Flyway**, tenus par la plateforme : l'ordre des DDL entre contextes (clés, index, vues de lecture) doit rester global et déterministe. Les DDL de contextes y restent.
2. **ADR 0014 amendé** (statut et lien ajoutés) : « schéma par contexte » est réinterprété en **isolation par propriété de table et préfixe de contexte**, vérifiée par les gardes `TableDe*Test` et ArchUnit, et non par schéma PostgreSQL. La chaîne Flyway par contexte reste l'option de l'extraction physique.
3. **Aucun script Flyway n'est modifié** par cette décision (V1-V59 intacts, pas de V60). Tout nouveau `V*` reste dans `platform/.../db/migration` ; aucun script de migration n'est autorisé ailleurs.
4. Condition de réexamen : événement « un contexte est extrait vers sa propre base », pas une date.

## Conséquences
+ pas de risque sur l'historique de production ; un seul pipeline (`flyway.yml`).
- Les DDL des contextes vivent loin de leur code ; la propriété d'une table se lit dans les gardes de tables, pas dans le dossier.
