---
status: proposed
date: 2026-11-12
sources: ADR 0140, ADR 0141, ADR 0142, .ddd/safety-net/not1-doc1-filet.md, KB-DOC1-1..5
---

# DOC1 - realisation

Module `backend/contexts/documents` (contexte Documents, ADR 0140) : langage publie `documents.v1`, composition du papier en-tete, routes `POST /api/v1/documents/print` et `/print/{modeleId}`. Refactoring sur place (ADR 0024) : memes tables, URL, roles, JSON. Aucun flag, aucune facade. Moteur Jasper laisse a la plateforme (DOC2). Decisions : ADR 0142.

## Fait
- Module cree et enregistre (`backend/pom.xml`, `platform/pom.xml`, `.ddd/arch-rules.json`) ; 79 tests (domaine, application, contrat double en memoire / JDBC, ACL, controleur, langage publie, ArchUnit).
- Plateforme : `ModeleDocumentPrinter` implemente `MoteurDeRendu`, `DocumentRestController` implemente `ReglesDImpressionDesTypesSensibles` (print retire, `/modeles` et `/types` conserves), `SocieteLetterheadAdapter`, ACL Dossier medical et Planification adaptees.
- Supprimes (git rm) : `DocumentIdentityProvider`, `PrintRequest`, `SocieteLogoService`, `LogoImageValidator`, tests `LogoImageValidatorTest`, `DocumentRestControllerTest` ; entree `covered.json` retiree.
- `FrontieresDesContextesTest` : usage du seul langage publie, quatre classes interdites de retour.

## Parite
Papier en-tete 11/11 identiques ; routes 13 scenarios dont 6 goldens modifies par decision utilisateur (reste identique).

| Golden | Decision |
|---|---|
| `requetes_refusees`, `patient_inconnu_ou_autre_centre`, `profils`, `center_id_du_client`, `impression_par_modele`, `centre_du_corps_non_garde` | `*_nouveau_code.approved.json`, anciens archives `_ecarts-legacy/*_avant_doc1.legacy.json` (KB-DOC1-1, 2, 5c) |

Golden DM2 `impression/.../erreurs` : cas `ordonnanceId illisible` 500 -> 400, `erreurs_nouveau_code`, ancien archive dans `dm2/_ecarts-legacy/impression/`. Test plateforme `DocumentRestControllerPatientDatasetIntegrationTest` : principal avec centre (un ADMIN sans centre est refuse).

## Ecarts reproduits (dette)
KB-DOC1-3 (tout role imprime), KB-DOC1-4 (document vide), KB-DOC1-5 (a, b, d, e, f, g).

## Questions ouvertes
- Matrice role x type de document : a confirmer avant le 2026-12-15 (KB-DOC1-3).
- ORDONNANCE non garde par modele (reproduit) ; gabarit introuvable = 500 texte (reproduit) ; un ADMIN sans centre est desormais refuse.
- Les contextes consommateurs n'utilisent pas encore `RenduDeDocument` (DOC2) ; `SocieteLetterheadPort` conserve jusqu'a PD.
- Postgres reel : contrat `ModelesDeDocumentJdbc` verifie sur H2 (mode PostgreSQL) seulement.
- Docs fonctionnelle et maquette non regenerees.

## Bascule
Aucun flag (ADR 0024). Retour arriere = revert du commit.
