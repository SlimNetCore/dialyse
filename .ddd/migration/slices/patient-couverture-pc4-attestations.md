---
slice: patient-couverture-pc4-attestations
status: planned
date: 2026-10-29
order: 4
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC4 — Attestations de droits

> Statut vrai au 2026-11-02 : `planned` (module gelé, `GEL.md`, ADR 0073). Le filet se limite à 2 tests unitaires legacy (`bc-insurance`), aucune caractérisation HTTP. Règle R5 (attestation liée à une PEC active) non implémentée et non observée dans le legacy : à confirmer au filet avant de la retenir.

Entité satellite de Patient. Attestation liée à une PEC active (R5).

## Avancement (reprise sur place, ADR 0024)

| Session | État | Contenu |
|---|---|---|
| S2 PC4 (2026-11-04) | parité atteinte, legacy retiré, seams fermés, build complet vert ; docs et état `built` en S3 | 28 scénarios verts sur le nouveau code ; 21 goldens en écart, tous décidés, archivés octet pour octet en `pc4/_ecarts-legacy/**/*.legacy.json` depuis `git show 945b872c` (nouveaux : `_nouveau_code`) ; test `deux_creations_simultanees…` : ligne `planning_parametres` posée, suppression simultanée = 200 + 404 ; jour du centre pour `DashboardRestController` et la séance sans date (`JourDuCentre`) ; supprimés : module `bc-insurance`, `AttestationRepositoryAdapter`, entité et dépôt JPA, `AttestationSpecifications`, DTO, `PecReadQueryService`, bean `AttestationDomainService`, cache `existsValidAt` ; `bc-assure` RESTE (import de migration) ; contrat PG16 26 tests (`patient-couverture-pc4-pg16-rejeu.sh`), PIT 87 % (`pit-pc4-module.sh`), `check-arch` 0 violation |
| S1 PC4 (2026-11-04) | code fait, parité NON approuvée | domaine `AttestationDroit` (déclarer, saisie de la fiche, `couvre`, `memePeriode`), `Attestations`, `AttestationIntrouvable` ; `GererLesAttestations` (garde de centre, 422 `ATTESTATION_PATIENT_INTROUVABLE`, 404 `ATTESTATION_INTROUVABLE`, verrou du centre), `RechercherLesAttestations`, `CriteresDAttestation` (dates illisibles 400), `CouvertureDeLaFiche` devenue cas d'usage sur `Attestations` ; adapters `AttestationsJdbc`, `LectureDesAttestationsJdbc` (jokers échappés) remplaçant les ACL temporaires `AttestationsDuPatientJdbc` et `CouvertureDeLaFicheJdbc` ; `AttestationsControleur` sur les MÊMES URLs (`/api/v1/pec/attestations**`), cache de liste reproduit ; port publié `NotificationsDesAttestations` ; sources gelées Attestation supprimées et `excludes` retirés ; `PecRestController` legacy supprimé, audit `ATTESTATION_*` ; module : 14 tests applicatifs, 6 domaine, contrat double (9) et H2 (24). Filet PC4 : 15 scénarios en écart sur 28 (à approuver un par un en S2). Reste S2 : parité, retrait du legacy (bc-insurance, adapter JPA, PecReadQueryService, cache `existsValidAt`), jour du centre (tableau de bord, séance sans date), seams. |

## Cas d'usage

`EnregistrerAttestation`, `ConsulterAttestation`, `ListerAttestations`.

## Entrées legacy

`bc-pec` (attestations).

## Données

Tables attestations ; aucune migration.