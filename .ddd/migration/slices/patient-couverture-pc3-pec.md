---
slice: patient-couverture-pc3-pec
status: built
date: 2026-11-04
order: 3
context: patient-couverture
sources: [.ddd/tactical/patient-couverture.md, ADR 0064]
---

# PC3 — Prise en charge (PEC)

> Statut vrai au 2026-11-04 : **`built`** (revue du 2026-11-04 traitée, ADR 0077 `accepted`). Historique : au 2026-11-02 `characterized` (10 goldens legacy `characterization/patientcouverture/pec`, URL legacy `/api/v1/pec`). Module gelé (`GEL.md`, ADR 0073) : aucune parité, pas de garde de centre ; suppression d'une PEC inconnue silencieuse (le legacy la rejette). Reprise sur place.

Agrégat `PEC` (root). Cycle `CREE -> VALIDEE -> CLOTUREE` (R3). Port publié `PECRepository` consommé par Seance
(création conditionnée).

## Cas d'usage

`CreerPEC`, `ValiderPEC`, `CloturerPEC`, `ConsulterPEC`, `ListerPECActives`.

## Entrées legacy

`bc-pec`.

## Données

Tables PEC ; aucune migration. Port publié exposé pour Seance.

## Avancement (reprise sur place, ADR 0024 ; ADR PC2/PC3 à venir en S3)

| Session | État | Contenu |
|---|---|---|
| S3 PC3 (2026-11-03) | **built** | parité atteinte : 34 scénarios PC3 et 5 du premier filet verts sur le nouveau code ; 24 goldens en écart, TOUS décidés, archivés octet pour octet en `pc3/_ecarts-legacy/**/*.legacy.json` (garde de centre 403, 404 `PEC_INTROUVABLE`, formats 400 et 415, nom réel du patient, audit `PEC_VALIDATION`/`PEC_CLOTURE`, `entity_id` à la création, 409 `PEC_IDENTIFIANT_DEJA_UTILISE`, message neutre « Statut de prise en charge inconnu : X ») ; legacy mort supprimé (`PecDomainService`, `PecUseCase`, `PecSpecifications`, DTO de PEC, moitié PEC de `PecReadQueryService`, bean, routes de `PecRestController`) ; seams fermés ; ADR 0077 (proposed) ; KB-PAT-20 à 28, 35, 36 statuées ; `verifications-production.md` §42 ; build complet vert, contrat H2 et PG16 verts, PIT 85 %. `bc-pec` (module), `PecRepositoryAdapter`, `PecJpaEntity/Repository` retirés le 2026-11-04 (revue : aucune référence) ; `PecValidationJdbc` (module gelé de Séance) et `SeanceBillingEligibilityPort` inchangés. |
| Revue PC2/PC3 (2026-11-04) | **traitée** | majeur 1 : capture inter-centres fermée en course (`UPDATE … AND center_id`, `INSERT … WHERE NOT EXISTS`, test de course A/B H2 + PG16) ; majeur 2 : import de migration hors verrou documenté (ADR 0077 point 12, règle « hors activité ») ; code PEC mort retiré ; règles de PEC/affectation dans le domaine ; front (4 codes, i18n fr/en/ar/kab, kab à relire) ; `patientNom` de repli ; test « centre sans ligne » ; §42-11 et 12 ; ADR 0077 `accepted`. |
| S2 PC3 (2026-11-03) | code fait | domaine `PriseEnCharge` (CREE, VALIDEE, CLOTUREE, transitions 422), `StatutPec` ; ports `PrisesEnCharge`, `AttestationsDuPatient` (ACL de lecture), `LectureDesPec`, `NotificationsDeLaPec` (publié) ; cas d'usage `GererLesPec`, `RechercherLesPec`, `PecDeLaFiche` ; adapters `PrisesEnChargeJdbc`, `LectureDesPecJdbc` ; `PecControleur` sur les MÊMES URLs avec droits du legacy (validation ADMIN seul) et garde de centre ; routes d'attestation laissées au `PecRestController` legacy (PC4) ; sources gelées Pec supprimées, `excludes` retirés ; ACL de couverture de la fiche réduite à l'attestation |

ARGENT, reproduit à l'identique et **à décider avec les métiers** : les deux règles « PEC valide pour une séance » (`session-allowed` : statut seul plus attestation du non-vacancier ; création de séance : `SeanceBillingEligibilityPort`, qui reste la règle de production), le forfait lu par Absences sans regarder le statut de la PEC, l'absence de contrôle de période, de forfait et de chevauchement, la suppression d'une PEC validée, plusieurs PEC validées simultanées. `PecValidationJdbc` du module gelé de Séance : dette consignée, non touchée. Demande de PEC de la fiche : un identifiant de PEC déjà pris n'annule plus la fiche : la fiche est enregistrée, la PEC non créée, réponse 409 `PEC_IDENTIFIANT_DEJA_UTILISE` (interprétation de « sans annuler la fiche », à valider).