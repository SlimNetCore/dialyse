# ADR-002 - Modulith DDD avec architecture hexagonale

- Statut: Accepted
- Date: 2026-03-14

## Contexte
Le projet doit respecter DDD + separation metier/technique et livrer vite un MVP robuste.

## Decision
Demarrer en modulith Spring Boot 4 segmente par bounded contexts, architecture hexagonale stricte.

## Regles
- Domaine pur: aucune dependance JPA/REST.
- Application: use cases + orchestration.
- Infrastructure: adaptateurs REST, persistence, auth, messaging.
- Front Angular structure par domaines fonctionnels.

## Consequences
- Cycle de livraison plus rapide qu une architecture microservices immediate.
- Evolution vers extraction de services possible plus tard si necessaire.

