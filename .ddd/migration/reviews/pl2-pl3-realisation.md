---
status: draft
date: 2026-11-12
sources:
  - .ddd/migration/reviews/inventaire-platform-restant.md (B3, B4, §3.1)
  - .ddd/safety-net/pl2-pl3-export-greffe-compteurs.md
  - ADR 0024, 0138
---

# PL2 (export greffe) et PL3 (tableau de bord) : réalisation

## Résumé
| Slice | État |
|---|---|
| PL3 (2 routes, compteurs + jour du centre) | **Terminée** : `contexts/patient-couverture`, KB-PL3-1 à 4 corrigés (KB-PL3-3 étendu aux attestations le 2026-11-12), KB-PL3-5 reproduit, ADR 0138 |
| PL2 (1 route, export PDF greffe) | **Terminée** après déblocage du hook (ACL de plateforme inscrite dans `covered.json` par l'utilisateur) : `contexts/dossier-medical` |

## PL3 : fichiers
Créés (`contexts/patient-couverture`) : `domain/DelaiDEcheance`, `domain/MoisDuTableauDeBord`, `application/ConsulterLeTableauDeBordAdministratif`, `application/TableauDeBordAdministratif`, `interfaces/http/TableauDeBordAdministratifControleur` (suffixe « Administratif » : `TableauDeBordControleur` existe déjà dans la Facturation, conflit de bean constaté).
Modifiés : `publishedlanguage/CompteursDeCouverture` (PEC et attestations à échéance : `(centre, aujourdhui, seuil)`), `infrastructure/persistence/CompteursDeCouvertureJdbc`, `configuration/PatientCouvertureConfiguration`, `interfaces/http/ErreursDeLaFicheAdvice`.
Supprimés (plateforme, `git rm`) : `DashboardRestController`, `JourDuCentre`, `JourDuCentreLegacy`, `DashboardSearchRequest`, `JourDuCentreLegacyTest`. Le jour du centre est le port existant `HorlogeDesCentres` (adapter `HorlogeDesCentresPublies`).

## PL2 : fichiers
Créés (`contexts/dossier-medical`) : `application/ExporterLeDossierDeGreffe`, `CartesDesPatients`, `ImpressionDuDossierDeGreffe` ; `publishedlanguage/PapierEnTeteDuCentre` (port fourni par la plateforme, `UUID` car la règle d'architecture interdit le noyau partagé dans un langage publié ; placé là car la règle `le_legacy_n_utilise_le_dossier_medical_que_par_son_langage_publie` interdit à la plateforme d'implémenter un port d'`application`) ; `infrastructure/impression/DossierDeGreffeEnPdf` (HTML identique) ; `infrastructure/acl/patientcouverture/CartesDesPatientsDeLaFiche` ; `interfaces/http/GreffeExportControleur`. Modifiés : `LecturesPublieesConfiguration` (beans), `ErreursDuDossierMedicalAdvice`, `DossierDeGreffePublie` (javadoc), `pom.xml` (openhtmltopdf-pdfbox 1.0.10, comme la plateforme et la Séance).
Créé (plateforme, couvert) : `infrastructure/acl/dossiermedical/PapierEnTeteDuCentreParLaPlateforme` (`DocumentIdentityProvider.decorateHtml`).
Supprimés (plateforme, `git rm`) : `GreffeExportRestController`, `GreffePdfService`, `GreffePdfServiceTest` (remplacé par `DossierDeGreffeEnPdfTest`). `grep -w` préalable : seules références restantes = `FrontieresDesContextesTest` (règle réduite à `PatientStatsQueryService`) et `TableDuDossierMedicalTest` (entrée retirée), docs. `SousLaGardeDuDossierMedical` n'a plus d'implémenteur en plateforme (marqueur de langage publié conservé, encore cité par l'advice du contexte). Annexe de traçabilité des règles de gestion mise à jour (`TableauDeBordAdministratifControleur`, `GreffeExportControleur`). `covered.json` : 5 entrées retirées (JourDuCentre, JourDuCentreLegacy, DashboardRestController, GreffeExportRestController, GreffePdfService).

## Parité PL2
Goldens DM7 export PDF (`LesEvaluationsKdigoEtLExportPdf...`) et droits (`LesDroitsLeCloisonnement...`, 14 profils, anonyme, centre B) : verts SANS modification de golden. Aucun écart de code ni de message 403 entre `CenterAccessGuard` et la garde du contexte.

## Goldens modifiés PL3 (protocole `*_nouveau_code`, relus un à un sur le `.received`)
| Golden | Changement | Archive |
|---|---|---|
| `tableau_de_bord/le_centre_de_la_requete_est_compare_/centre_de_session` | 5 réponses 200 deviennent 403 `ACCESS_DENIED` (KB-PL3-1) | `_ecarts-legacy/tableau_de_bord/…/centre_de_session_avant_pl3.legacy.json` |
| `…/les_droits_du_tableau_de_bord_par_pr/droits_par_profil` | seuls « GET/POST ADMIN sans centre » : 200 devient 403 `ACCESS_DENIED` ; KB-PL3-5 reproduit | `droits_par_profil_avant_pl3.legacy.json` |
| `…/les_requetes_mal_formees_sont_refuse/requetes_mal_formees` | 500 deviennent 400 `BAD_REQUEST`, négatif 400, mois illisible 400 ; `{}` et `centerId:null` inchangés | `requetes_mal_formees_avant_pl3.legacy.json` |
| `…/les_compteurs_de_prises_en_charge_d_/compteurs_riches` | `pecExpiring` -1 (PEC échue exclue), mois `2026-1` et `n-importe-quoi` 400 | `compteurs_riches_avant_pl3.legacy.json` |
| `effets/les_compteurs_du_tableau_de_bord_lis/compteurs` | `expirationDays=-1` 400 ; attestation échue exclue de `attestationExpiring` (3 devient 2, 1 devient 0 pour `expirationDays=0`, etc.) | `compteurs_avant_pl3.legacy.json` (état d'origine ; `nouveau_code` réapprouvé après l'extension aux attestations) |
Inchangés : `centre_vide`, `JourDuCentre` (3 tests). Les anciens `approved` de `tableau_de_bord` n'étaient pas suivis par git (`rm`), celui de `effets` l'était (`git rm`). Archives : `backend/platform/src/test/resources/characterization/patientcouverture/pc4/_ecarts-legacy/`. Aucun `-Dcharacterization.approve=true`.

## Tests
`DelaiDEcheanceTest` (6), `ConsulterLeTableauDeBordAdministratifTest` (6, horloge fixe), `HorlogeDesCentresPubliesTest` (4, ex `JourDuCentreLegacyTest`), contrat `LecturesPublieesContrat` (PEC et attestations, fake et JDBC), `ExporterLeDossierDeGreffeTest` (4), `DossierDeGreffeEnPdfTest` (4), `DashboardRestControllerMonthFilterTest` adapté (sécurité réelle ; mois illisible 400 ; autre centre 403).

## Points ouverts
1. À confirmer avec les métiers au plus tard le **2026-12-15** : exclusion des PEC et attestations échues des compteurs « à échéance » (ADR 0138, KB-PL3-3).
2. Horloge : le filet HTTP garde `supposerLeJourDeLaJvmEgalAuJourDuCentre` (la configuration Spring garde `Clock.systemUTC()`) ; le déterminisme est obtenu au niveau du cas d'usage via le port `HorlogeDesCentres`.
3. Procédure : `DelaiDEcheance` et `MoisDuTableauDeBord` ont été écrits avant d'avoir vu leur test rouge (rouge constaté par compilation seulement pour le reste).
4. `ReglesDeGestionDocumentationTest` a échoué au premier build complet (annexe de traçabilité) : corrigé.

## Vérifications
check-arch : 0 violation. `seamAllowed` : []. 3 passes ciblées vertes (dont `-Dsurefire.runOrder=random`) : filet tableau de bord, JourDuCentre, Effets, MonthFilter, cas d'usage, contrat, DM7 export (KDIGO, droits), `FrontieresDesContextesTest`, `ArchitectureHexagonaleTest`, `TableDuPatientCouvertureTest`, `TableDuDossierMedicalTest`, `ReglesDeGestionDocumentationTest`, tests PL2. Build complet `./mvnw -o test -fae` : voir réponse finale (journal scratchpad `full2.log`).
