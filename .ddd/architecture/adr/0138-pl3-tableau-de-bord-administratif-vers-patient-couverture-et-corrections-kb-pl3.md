---
status: accepted
date: 2026-11-11
decision-makers: [utilisateur, architecte]
context-scope: patient-couverture, platform
related: ADR 0024, 0078, 0079, 0107 ; `.ddd/safety-net/pl2-pl3-export-greffe-compteurs.md` ; `.ddd/safety-net/known-bugs.md` KB-PL3-1 à 5
---

# 0138 - PL3 : le tableau de bord administratif passe à Patient et couverture ; corrections KB-PL3-1 à 4

Refactoring sur place (ADR 0024) : mêmes URLs (`GET /api/v1/dashboard/stats`, `POST /api/v1/dashboard/stats/search`), mêmes tables,
même JSON, mêmes rôles. Pas de façade ni de routage.

## Décision de placement
`DashboardRestController` (plateforme) devient `TableauDeBordAdministratifControleur` + cas d'usage `ConsulterLeTableauDeBordAdministratif`
dans `contexts/patient-couverture` (ports : `SessionCourante`, `HorlogeDesCentres`, langage publié `CompteursDeCouverture`). Domaine :
`DelaiDEcheance`, `MoisDuTableauDeBord`. Le « jour du centre » est déjà `HorlogeDesCentres` (adapter `HorlogeDesCentresPublies`, langage
publié d'Organisation `AnnuaireDesCentres.fuseauHoraireDe`) : `JourDuCentre` et `JourDuCentreLegacy` (plateforme) disparaissent, ainsi que
`DashboardSearchRequest`. L'horloge est un port : les scénarios du cas d'usage sont déterministes (`ConsulterLeTableauDeBordAdministratifTest`).

## Écarts décidés par l'utilisateur (2026-11-11)
| KB | Décision |
|---|---|
| KB-PL3-1 | Garde de centre de la session, comme les autres routes du contexte (`SessionLegacy`) : centre demandé différent du centre de session, ou session sans centre = 403 `ACCESS_DENIED`. ADMIN et SUPERADMIN : le SUPERADMIN est de toute façon refusé en amont (403 `ROLE_SCOPE`, KB-PL3-5), la branche « accès transverse » de la garde n'est donc pas atteignable ici |
| KB-PL3-2 | `centerId` absent ou illisible, `expirationDays` non numérique, corps POST absent ou illisible = 400 `BAD_REQUEST` ; `{}` et `centerId:null` restent 400 `VALIDATION_ERROR` (inchangé) |
| KB-PL3-3 | `pecExpiring` (puis `attestationExpiring`, voir plus bas) exclut les lignes déjà échues : fin entre le jour du centre et le seuil, bornes incluses. **À confirmer avec les métiers au plus tard le 2026-12-15.** Le port publié `CompteursDeCouverture.prisesEnChargeValideesAEcheance` prend désormais `(centre, aujourdhui, seuil)` (seul consommateur : ce cas d'usage) |
| KB-PL3-4 | Mois illisible = 400 `BAD_REQUEST` ; `expirationDays` négatif = 400 `BAD_REQUEST` (zéro admis) |
| KB-PL3-5 | Reproduit : DIRECTION et SUPERADMIN refusés en 403 `ROLE_SCOPE` (filtre de portée des rôles, hors contexte) |

## Extension de KB-PL3-3 aux attestations (décision de l'utilisateur, 2026-11-12)
La décision initiale disait « comme `attestationExpiring` », or `attestationExpiring` comptait lui aussi les attestations déjà échues
(`date_fin <= seuil`). L'utilisateur a décidé d'exclure AUSSI les attestations échues, pour la cohérence avec `pecExpiring` :
`attestationExpiring` compte les attestations dont la fin est entre le jour du centre et le seuil, bornes incluses. Le port publié
`CompteursDeCouverture.attestationsAEcheance` prend `(centre, aujourdhui, seuil)`. **À confirmer avec les métiers au plus tard le
2026-12-15** (les deux compteurs). Golden `effets/compteurs_nouveau_code` mis à jour.

## PL2 (même PR)
L'export PDF du dossier de greffe passe de la plateforme à `contexts/dossier-medical` : `GreffeExportControleur`, cas d'usage
`ExporterLeDossierDeGreffe` (garde du centre de session, puis du patient, puis carte), ports `CartesDesPatients` (ACL vers
`FichesDesPatients`) et `ImpressionDuDossierDeGreffe` (`DossierDeGreffeEnPdf`, HTML repris à l'identique). Le papier à en-tête du centre
est le port publié `PapierEnTeteDuCentre`, fourni par l'ACL de plateforme `PapierEnTeteDuCentreParLaPlateforme` (moteur de documents,
génerique A8). Mêmes URL, rôle (MEDECIN), en-têtes et contenu ; `GreffeExportRestController`, `GreffePdfService` supprimés.

## Preuves
Goldens `*_nouveau_code.approved.json` (relus un à un sur le `.received`), anciens archivés en `_ecarts-legacy/**/*_avant_pl3.legacy.json`.
Contrat `LecturesPublieesContrat#should_compter_les_pec_validees_a_echeance_echues_exclues` (fake et JDBC), `DelaiDEcheanceTest`,
`HorlogeDesCentresPubliesTest` (ex `JourDuCentreLegacyTest`).
