---
status: proposed
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md, pl11-0-filet.md, ADR 0157
---
# PL11-1 : SPI de la façade des référentiels + descripteurs de Patient et couverture

## Fait
- **Patient et couverture (langage publié)** : `DescripteurDeReferentiel` (+ `Champ`), `DescripteursDeReferentiels` (transporteurs, forfaits, caisses, agences, centres payeurs : mêmes champs, alias, exemples, libellés, ordre).
- **Plateforme** : SPI `ReferentialAdminDelegate#descripteurs()` ; `ReferentialCatalogue` (directs + publiés, ordre d'écran historique conservé) ; `ReferentialKind` enum -> classe finale (CRENEAU, SALLE, MEDECIN seulement) ; ACL `ReferentielsDePatientCouverture` ; `ReferentialField.referenceLabel`.
- **KB-PL11-12 corrigé** : JSON seul (`produces`/`consumes`) ; 405 + `Allow`, 406, 415, 400 (import sans fichier) propres ; classes imbriquées de `DatesIllisiblesDeLaDirectionAdvice`.
- **Audit enrichi** : `entity_id` = identifiant créé ; libellé « Administration des référentiels : <action> (<slug>) ».
- **KB-PL11-13 reproduit** : SUPERADMIN refusé ; `hasRole('ADMIN')` + Javadoc corrigée.
- **Butoirs soldés** : `DescripteursResiduelsDeLAdministrationGeneriqueTest` supprimé ; lignes ADR 0122/0124/0126/0128 « SOLDÉ » ; garde anti-retour `LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest`.
- Docs : ADR 0157, KB-PL11-12/13 « CORRIGÉ (2026-11-13) » / « REPRODUIT », RG-REF-001, annexe, glossaire Patient et couverture.

## Goldens (relus un par un depuis .received, jamais approve=true)
| Golden | Écart |
|---|---|
| pl11-0-{salles,creneaux,medecins}/audit -> `audit_nouveau_code` | `entity_id` = id créé au lieu du slug (1 ligne chacun) |
| pl11-0-transverse/formes-illisibles -> `_nouveau_code` | 03 sans type 400->415 ; 09/10 texte et XML 500/400->415 ; 17-26 PATCH/PUT/DELETE sans id/POST sur ressource/GET import 500->405 ; 27-29 Accept XML/texte 200/500->406 ; 33 import sans fichier 500->400 ; 34 non multipart 500->415 |
| pl11-0-transverse/audit-des-refus -> `_nouveau_code` | PATCH 500->405 |
| rf1/rf2 `saisie/creations`, rf3 `saisie-caisses/creations` | « sans corps » (ni corps ni type) 400->415 |
| rf3 `admin-generique/refus-de-forme` | « sans corps » 400->415 (7 cas) dont 3 **secrétaire sans Content-Type : 403->415** (écart collatéral, le refus de `consumes` précède l'autorisation) |
Archives : `organisation/_ecarts-legacy/pl11-0-*/…_avant_pl11-1.legacy.json`, `patientcouverture/_ecarts-legacy/rf{1,2,3}/…`. Tous les autres goldens (rf1/rf2/rf3, o2, pl11-0) inchangés.

## Reste hors PL11-1
Obstacles : aucun. PL11-2 à 4 (créneaux, médecins, salles), PL11-5 (appariement de la reprise), PL11-6 (retrait du moteur ; `REFERENCE` du moteur générique devenu mort).
