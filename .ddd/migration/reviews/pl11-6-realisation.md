---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4 PL11-6, 6), pl11-decisions.md (D12), ADR 0161
---

# PL11-6 - Retrait de l'adaptateur SQL dynamique (clôture de PL11)

## Fait
- Supprimés (main) : `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`, `ReferentialCacheEvictor`, `ReferentialAdminRepositoryPort` (dont `findAllForMatching`), `ReferentialValuesValidator`.
- Supprimés (test) : `InMemoryReferentialAdminRepository`, `ReferentialAdminDomainServiceTest` (moteur retiré).
- `ReferentialAdminDomainService` : façade fine (pagination, lecture du fichier, distribution au SPI), constructeur à un seul délégué ; `Matching` retiré. `DomainServiceConfig` mis à jour. `ReferentialKind.directs()/direct()` retirés ; `ReferentialCatalogue` ne lit plus que les descripteurs des délégués.
- Tests : `ReferentialAdminDelegationTest` (9 : + fichier vide / trop long, anomalies tronquées, référentiel sans délégué) ; garde `LaFacadeDesReferentiels…` allégée ; règles ArchUnit permanentes dans `FrontieresDesContextesTest` (pas de JDBC/JPA dans la façade ; classes retirées interdites).
- Butoirs 2027-03-31 liés à PL11 : déjà tous soldés par PL11-1 à PL11-4 (plus aucune entrée `ReferentialAdmin*` dans les tables d'exceptions) ; aucune table d'exceptions vide à remplacer ; aucun report.
- Documents : ADR 0161, ADR 0070 `superseded`, `covered.json` (5 entrées retirées), RG 07 et 14 + `documentation-fonctionnelle.html` (sources).

## Goldens
Aucun golden modifié (o2, rf*, pl11-0-*).

## Fichiers legacy non couverts bloquants
Aucun.
