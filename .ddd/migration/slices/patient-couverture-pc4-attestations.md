---
slice: patient-couverture-pc4-attestations
status: built
date: 2026-11-04
order: 4
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC4 — Attestations de droits

> Statut vrai au 2026-11-04 : **`built`** (ADR 0078 `accepted`). Historique : au 2026-11-02 `planned` (module gelé, `GEL.md`, ADR 0073). Le filet se limite à 2 tests unitaires legacy (`bc-insurance`), aucune caractérisation HTTP. Règle R5 (attestation liée à une PEC active) non implémentée et non observée dans le legacy : à confirmer au filet avant de la retenir.

Entité satellite de Patient. Attestation liée à une PEC active (R5).

## Avancement (reprise sur place, ADR 0024)

| Session | État | Contenu |
|---|---|---|
| S3 PC4 (2026-11-04) | **built** | ADR 0078 `accepted` (décisions déléguées à l'assistant ; argent et métier reproduits : à confirmer avec les métiers) ; KB-PAT-38 à 48 statués ; RG-ATT-005 à 010, annexe et test garde ; glossaire (proposed) ; `verifications-production.md` §43 (SELECT seuls) ; roadmap, `GEL.md` (levée complète PC1 à PC4, reste PC5) ; documentation client régénérée ; front : messages i18n fr/en/ar/kab (kab en anglais, « [EN, à relire en kabyle] ») pour `ATTESTATION_PATIENT_INTROUVABLE`, `ATTESTATION_INTROUVABLE` et `ACCESS_DENIED` (suppression d'une attestation), spec 4 tests, `tsc` app et spec sans erreur ; build complet vert, `check-arch` 0 violation. Dette : écrivain hors verrou `HistoricalRecordJdbcAdapter` (import de migration, et rollback : à lancer hors activité du centre) ; lecteurs SQL hors module (PC5). |
| Revue PC4 (2026-11-04) | **traitée** | `covered.json` complété (`DashboardRestController`, `CacheConfig`, `DomainServiceConfig`, `JourDuCentreLegacy`) et `seams.md` aligné (`updateDate`) ; `JourDuCentre` devient un port (`application/centre`) avec l'adapter `JourDuCentreLegacy` (`infrastructure/acl/centres`, liste blanche datée) ; tests : unitaire `JourDuCentreLegacyTest` (horloge fixe 23:30 UTC, fuseaux explicite, absent, vide, illisible) et intégration `jour_du_centre` (tableau de bord et séance `create`/`updateDate` sans date, fuseau du centre différent de celui de la JVM, 2 goldens neufs) ; garde de centre AVANT la lecture du corps (golden neuf `garde_avant_le_corps`) ; course PG16 de deux suppressions (200 + 404) ; ADR 0078 : section « À confirmer avec les métiers » (NON validé par eux) et dette corrigée (`HistoricalRecordJdbcAdapter`, pas `bc-assure`) ; §43 : contrôle des fuseaux en premier ; glossaire dédoublonné. |
| S2 PC4 (2026-11-04) | parité atteinte, legacy retiré, seams fermés, build complet vert | 28 scénarios verts sur le nouveau code ; 21 goldens en écart, tous décidés, archivés octet pour octet en `pc4/_ecarts-legacy/**/*.legacy.json` depuis `git show 945b872c` (nouveaux : `_nouveau_code`) ; test `deux_creations_simultanees…` : ligne `planning_parametres` posée, suppression simultanée = 200 + 404 ; jour du centre pour `DashboardRestController` et la séance sans date (`JourDuCentre`) ; supprimés : module `bc-insurance`, `AttestationRepositoryAdapter`, entité et dépôt JPA, `AttestationSpecifications`, DTO, `PecReadQueryService`, bean `AttestationDomainService`, cache `existsValidAt` ; `bc-assure` RESTE (import de migration) ; contrat PG16 26 tests (`patient-couverture-pc4-pg16-rejeu.sh`), PIT 87 % (`pit-pc4-module.sh`), `check-arch` 0 violation |
| S1 PC4 (2026-11-04) | code fait, parité NON approuvée | domaine `AttestationDroit` (déclarer, saisie de la fiche, `couvre`, `memePeriode`), `Attestations`, `AttestationIntrouvable` ; `GererLesAttestations` (garde de centre, 422 `ATTESTATION_PATIENT_INTROUVABLE`, 404 `ATTESTATION_INTROUVABLE`, verrou du centre), `RechercherLesAttestations`, `CriteresDAttestation` (dates illisibles 400), `CouvertureDeLaFiche` devenue cas d'usage sur `Attestations` ; adapters `AttestationsJdbc`, `LectureDesAttestationsJdbc` (jokers échappés) remplaçant les ACL temporaires `AttestationsDuPatientJdbc` et `CouvertureDeLaFicheJdbc` ; `AttestationsControleur` sur les MÊMES URLs (`/api/v1/pec/attestations**`), cache de liste reproduit ; port publié `NotificationsDesAttestations` ; sources gelées Attestation supprimées et `excludes` retirés ; `PecRestController` legacy supprimé, audit `ATTESTATION_*` ; module : 14 tests applicatifs, 6 domaine, contrat double (9) et H2 (24). Filet PC4 : 15 scénarios en écart sur 28 (à approuver un par un en S2). Reste S2 : parité, retrait du legacy (bc-insurance, adapter JPA, PecReadQueryService, cache `existsValidAt`), jour du centre (tableau de bord, séance sans date), seams. |

## Cas d'usage

`EnregistrerAttestation`, `ConsulterAttestation`, `ListerAttestations`.

## Entrées legacy

`bc-pec` (attestations).

## Données

Tables attestations ; aucune migration.