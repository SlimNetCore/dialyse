---
slice: patient-couverture-pc4-attestations
status: characterized
date: 2026-11-01
sources: [.ddd/migration/slices/patient-couverture-pc4-attestations.md, .ddd/tactical/patient-couverture.md, ADR 0064]
---

# Filet de caractérisation — PC4 Attestations de droits

## Point d'observation

Domaine pur (JUnit) sur `AttestationDroit` et `AttestationDomainService`, avec dépôt en mémoire.
Isolation multi-centre vérifiée par filtrage `centerId` dans `findByPatient`.
Les lectures paginées passent par `PecReadQueryService` / `PecRestController` (endpoint
`/api/v1/pec/attestations-center/search`).

## Tests existants couvrant la slice

- `backend/bc-insurance/src/test/java/.../domain/insurance/model/AttestationDroitTest` (validation dates) ;
- `backend/bc-insurance/src/test/java/.../domain/insurance/service/AttestationDomainServiceTest` (création, rejet, liste
  par patient scoping centre, suppression).

## Scénarios verrouillés (multi-centre)

- `AttestationDroit` : dates obligatoires ; date fin ne peut précéder date début.
- Création d'attestation rattachée à un `centerId` et un `patientId`.
- Rejet si date début après date fin.
- Liste par patient filtrée par `centerId` (pas de fuite inter-centres).
- Suppression par identifiant.
- Règle R5 : attestation liée à une PEC active (vérifiée via `AttestationRepositoryPort.existsValidAt` dans
  `PecDomainService`).

## Points d'attention

- `AttestationDomainService.create` ne valide pas la présence d'une PEC active ; cette règle est portée par
  `PecDomainService`.
- `createdAt` est fixé à `OffsetDateTime.now()` (non-déterministe) ; non testé.
- Compatibilité H2/PostgreSQL : aucun SQL spécifique.
