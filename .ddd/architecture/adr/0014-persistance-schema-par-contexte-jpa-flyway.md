---
status: proposed
date: 2026-10-09
decision-makers: [architecte, administrateur base de données]
context-scope: global
research: [.ddd/research/spring-boot-4-web-data-tx.md, .ddd/research/testcontainers-postgresql.md]
---
# 0014 — Persister chaque contexte extrait dans son schéma PostgreSQL (`gmao`, …) via JPA, migrations Flyway dans la chaîne existante

## Contexte et problème
Schéma unique legacy (124 tables) ; ADR 0008 : chaque contexte extrait possède son schéma. Les migrations sont appliquées par Flyway CLI depuis `backend/platform/src/main/resources/db/migration` (workflow `.github/workflows/flyway.yml`, PostgreSQL 16, dernière version V9) ; `application.yml` contient `ddl-auto: update`.

## Options considérées
1. Tables dans le schéma `public` — contre : pas de propriété claire, ACL impossible à auditer.
2. **Schéma `gmao` + JPA (Hibernate déjà présent)** avec entités d'infrastructure et mapping explicite (ADR 0012) — pour : outillage connu de l'équipe ; contre : unité de persistance partagée avec le legacy.
3. Spring Data JDBC / JdbcClient — pour : orienté agrégat ; contre : non documenté dans `research/`, nouvelle techno ; à réévaluer.
4. Chaîne Flyway séparée par contexte — contre : modifie la CI et le déploiement maintenant.

## Décision
Option 2, migrations `V<n>__gmao_<objet>.sql` dans la **chaîne unique existante** (numérotation globale) tant que le contexte tourne dans le monolithe ; elles créent aussi la table du registre d'événements (ADR 0013). Aucune écriture du nouveau code dans les tables legacy ; lecture transitoire uniquement via ACL, listée dans le plan avec date de retrait.

## Conséquences
+ un seul pipeline de déploiement ; isolement par schéma.
− migration des données legacy → `gmao` à écrire (slice de bascule) sur copie, jamais `DROP` sans validation écrite.
⚠ `ddl-auto: update` créerait les tables `gmao` hors Flyway : à passer en `validate` pour le profil prod (question ouverte, accord requis).
## Révision
Extraction physique d'un contexte : chaîne Flyway propre (option 4).
