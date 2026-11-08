---
slice: seance-se2-validation-signature
status: built
date: 2026-11-08
order: 2
context: seance
sources: [.ddd/tactical/seance.md, ADR 0062, 0074, 0088, 0089, .ddd/safety-net/seance-se2-validation-signature.md]
---

# SE2 — Validation, signature du médecin, déverrouillage pour régularisation

> Historique : `planned` au 2026-11-02 (module gelé, aucun filet). Caractérisée le 2026-11-08 (net 95ab8291 : 33 scénarios, 33 goldens, KB-SEA-11 à 21). `built` depuis le 2026-11-08 (lots A, B, C). Reprise sur place (ADR 0024) : mêmes tables, mêmes URLs, aucune façade ni drapeau ; la bascule est le remplacement des handlers (lot B), réversible par retour git. Décisions, risques et résidus : `.ddd/architecture/adr/0089-seance-se2-reprise-sur-place-de-la-validation-de-la-signature-et-du-deverrouillage.md`.

## Cas d'usage (module `backend/contexts/seance`)
`ValiderUneSeance` (`POST /api/v1/seances/{id}/valider`, `ADMIN`, `INFIRMIER`), `SignerUneSeance` (`POST …/signer-medecin`, `ADMIN`, `MEDECIN`), `DeverrouillerUneSeance` (`POST …/deverrouiller-regularisation?centerId=`, `ADMIN`). Le scan de l'infirmier (SE1) valide par `Seance.validerParLInfirmier`.

## Fait (lots A, B et C, 2026-11-08)
| Couche | Contenu | Tests |
|---|---|---|
| Domaine | `Seance` (`validerParLInfirmier`, `memoriserPlace`, `signerParLeMedecin`, `deverrouillerPourRegularisation`, `exigerLaRegularisation`), `Signataire` (100 caractères), `PlaceDeSeance`, `MotifDeRefus` (+6) | `SeanceTest` 5 et suivants (matrice invariant vers test) |
| Application | `ValiderUneSeance`, `SignerUneSeance`, `DeverrouillerUneSeance`, ports `PlaceDuPatient`, `ConsommationsALaValidation`, `ConsommationRefusee`, `DiffusionsDeSeance.seanceDeverrouillee`, `Seances.trouverPourModifier` (`FOR UPDATE`) | `ValiderUneSeanceTest` 14, `SignerEtDeverrouillerUneSeanceTest` 9 (fakes) |
| Sortant | `SeancesJdbc` (11 colonnes), ACL `PlaceParLePlanning`, `ConsommationsALaValidationDuStock`, plateforme `PlaceFournieLegacy`, `DiffusionsFournieesLegacy.seanceDeverrouillee` | contrat `Seances` : mémoire 17, H2 20, PG16 (script `seance-se1-pg16-rejeu.sh 55501`) ; ACL `TraducteursDeLaValidationTest` 4 |
| Entrant | `SeancesControleur` (3 routes), `ErreursDeSeanceAdvice` (403 avant 400, 422 `ConsommationRefusee`), DTO `ValidationDeSeanceDemande`, `SignatureDeSeanceDemande` | `SeancesControleurTest` 13 |
| Routage | handlers legacy, 3 méthodes de `SeanceUseCase`, 3 DTO retirés ; seam inscrit puis refermé (`seams.md`), `covered.json` nettoyé | |
| Parité | 33/33 scénarios : 21 identiques, 12 écarts décidés (goldens `*_nouveau_code`, legacy archivé en R100 sous `characterization/seance/se2/_ecarts-legacy/`) ; SE1 31/31 inchangé ; P6 6/6 inchangé (via `ValiderUneSeance`) | 33 + 31 + 6 |
| Verrou | `plus_aucun_appelant_des_ecritures_legacy_de_la_validation_de_seance` (`FrontieresDesContextesTest`) | |
| Docs | RG-SEA-053, annexe 16, glossaire, `verifications-production.md` §54 (SELECT seulement) | `ReglesDeGestionDocumentationTest` |

## Écarts de parité (12, tous décidés, ADR 0089 lot B)
Corps ou paramètre illisible, absent, mal formé : 500 vers 400 · `centerId` absent : message « Centre requis » · signataire de plus de 100 caractères : 500 vers 400 · statut illisible : plus de nom de classe · patient disparu : 404 `PATIENT_INTROUVABLE` · article de consommation invalide : 500 vers 400 · audit : refus 500 vers 400. Aucun écart non voulu.

**13e écart décidé (CORRIGER, à confirmer métiers, sans golden)** : le verrou des jours passés et le déverrouillage lisent le jour du centre (`Africa/Algiers`), plus le jour de la JVM (KB-SEA-01). Aucun golden n'est touché : aucun scénario ne distingue les deux jours (séances posées au jour du centre, nommé `<J>`). À vérifier avant la mise en production : le fuseau de la JVM. Détail : ADR 0089 (tableau et en-tête), RG-SEA-053, `known-bugs.md`.

## Jalons rouges datés (known-bugs.md, « Décisions SE2 »)
KB-SEA-11 : mise en production conditionnée à la décision juridique et DPO, revue au plus tard le 2026-12-15. `PlaceFournieLegacy`, `DiffusionsFournieesLegacy`, lectures `LocalDate.now()` de SE3 à SE7 : date butoir 2027-01-31, point d'étape le 2026-12-15. Résidu mineur : les cas d'usage retournent l'entité `Seance` au contrôleur (comme SE1), DTO de lecture au plus tard le 2026-12-15.

## Bilan des résidus (revue au plus tard le 2026-12-15)
`SeanceDomainService.validerParLeScanResiduel` (avec `scanAndValidate`, `createFromQr`, `SeanceUseCase.create`) ; `Seance.validerParInfirmier`, `signerParMedecin`, `deverrouillerPourRegularisation`, `memoriserPlace` ; `PlaceFournieLegacy` et `SeancePlacePort` (avec SE7) ; `DiffusionsFournieesLegacy` ; date serveur des lectures de SE3 à SE7 (SE4, SE7). Table nominative : ADR 0089, section « Lot C ».

## Décisions à confirmer avant la mise en production
KB-SEA-11 (signataire lu dans le corps : imputation d'une signature médicale, décision juridique et DPO), KB-SEA-12 (validation d'une séance à venir), KB-SEA-13 (déverrouillage sans borne), KB-SEA-15 (consommables cumulés), diffusions en échec non bloquantes, droits (`SUPERADMIN` refusé). Section 54 de `verifications-production.md` pour les contrôles en lecture seule.

## Reste à faire
`/ddd-verify`. Doc fonctionnelle et maquette à régénérer si l'utilisateur le demande (aucun changement visible). Retrait des résidus à la revue du 2026-12-15.

## Plan de bascule
Refactoring sur place : pas de façade, de drapeau, de projection ni de métrique ; mêmes tables et URLs ; rollback = commit précédent. Aucune activation en production par l'assistant.

## Données
Table `seances` ; aucune migration, aucune colonne ajoutée. Tables lues ou écrites par le Stock : `consommations_de_seance`, `consommations_de_seance_imputations`, `sorties_pour_seances`.
