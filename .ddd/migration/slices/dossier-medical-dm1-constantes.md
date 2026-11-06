---
slice: dossier-medical-dm1-constantes
status: built
date: 2026-11-06
order: 1
context: dossier-medical
sources: [.ddd/safety-net/dossier-medical-dm1-constantes.md, .ddd/safety-net/dossier-medical-inventaire.md, ADR 0007, 0065, 0072, 0080, backend/contexts/dossier-medical/GEL.md]
---

# DM1 — Constantes (lecture, refactoring sur place, ADR 0024)

## Périmètre réel
Le legacy n'a qu'**une** route « constantes » : `GET /api/v1/patients/{patientId}/constantes` (vue longitudinale du volet paramédical par séance, lecture seule). Aucune création, modification ni suppression de constante. Écrivain unique : `PUT /seances/{id}/volet-paramedical` (propriété Séance, SE2) ; effacement : `DELETE /seances/{id}`. FHIR et KDIGO ne lisent pas les constantes et n'y seront pas branchés.

## Fait
- Module `backend/contexts/dossier-medical` repris SUR PLACE (remis dans `backend/pom.xml`, `platform/pom.xml`, `Dockerfile`) ; le contenu gelé d'avant (modèle parallèle DM2 à DM8) est retiré du module, il reste dans l'historique git (542c7571).
- **Domaine** (lecture pure, sans agrégat : DM1 n'a aucun invariant d'écriture) : `Constantes` (valeurs brutes), `ConstantesDeSeance`, `Pagination` (page ≥ 0, taille de 1 à 100), `PageDeConstantes`, port `HistoriqueDesConstantes`.
- **Application** : `ConsulterLesConstantesDuPatient` (garde de centre `SessionCourante` puis lecture).
- **Adapters** : `HistoriqueDesConstantesJdbc` (un JOIN `seances` + `volet_paramedical`, aucune table ni colonne ajoutée), ACL `SessionLegacy` (centre du jeton), `ConstantesControleur` (même URL, mêmes champs JSON, `@PreAuthorize` MEDECIN et ADMIN comme le legacy), `ErreursDesConstantesAdvice` (400, 403).
- **Retiré du legacy** : `ConstantesRestController`, `ConstantesQueryService`, `ConstanteSeanceResponse`.
- **Gardes de sécurité legacy** (seams fermés) : `PUT volet-paramedical` (centre du corps confronté au jeton, KB-MED-01) ; KB-MED-02 : `GET /seances/{id}`, `GET /seances`, `/journal`, `/calendar`, `POST /seances`, `POST /seances/scan`, `PUT /seances/{id}`, `stats/paramedical`, `stats/medical`, `stats/export` (GET et search, centre résolu) : 403 hors centre. Complété le 2026-11-07 : forfait, valider, déverrouiller, signer-médecin, consommables, jours fériés et fermetures, volet médical (ADR 0080 §7) : plus aucune route de `/api/v1/seances` sans garde.

## Écarts de parité (décidés, goldens `_nouveau_code`, ancien comportement archivé dans `characterization/dossiermedical/dm1/_ecarts-legacy`)
| Scénario | Avant | Après |
|---|---|---|
| lecture, `size=1000` | 200 | 400 (taille 1 à 100) |
| lecture, `page=abc`, `size=abc`, `centerId=xx`, identifiant de patient illisible | 500 | 400 |
| `GET /seances/{id}` et `stats/paramedical` avec le `centerId` d'un autre centre | 200 (fuite) | 403 |
| `PUT volet-paramedical` avec un `centerId` du corps étranger au jeton (infirmier A sur centre B ; ADMIN sans centre) | 200 (écriture inter-centres) | 403, rien d'écrit |
Le golden historique `constantes_cross_centre` (403) est inchangé.

## Reproduit à l'identique (« à décider avec les métiers/RGPD », ADR 0080)
Droits (le MEDECIN lit, n'écrit pas : KB-MED-08) ; bornes de durée, débit, UF ; notation cmHg ambiguë (KB-MED-09/10) ; valeurs brutes non normalisées (KB-MED-11) ; diffusion du nom et du prénom du patient sur le temps réel à l'enregistrement du volet (KB-MED-05) ; fuseau JVM des statistiques (KB-MED-14) ; patient inconnu = liste vide, 200 (KB-MED-03).

## Non fait, consigné
- Audit des lectures médicales (KB-MED-04) : le legacy n'audite que `GET .../dossier-medical` ; question pour le RGPD/DPO, rien ajouté.
- `PUT volet-paramedical` reste chez la Séance (SE2).

## Preuves
Voir `.ddd/safety-net/dossier-medical-dm1-constantes.md` (filet) et l'ADR 0080 : contrat du port (double, H2, PostgreSQL 16 via `backend/scripts/dossier-medical-dm1-pg16-rejeu.sh`), ArchUnit, `check-arch.ts`, PIT (`backend/scripts/pit-dm1-module.sh`), build complet.

## Flag / bascule
Refactoring sur place : mêmes URL, mêmes tables ; pas de façade, de routage ni de projection (décision de l'utilisateur, ADR 0024). Retour arrière = revert du commit.
