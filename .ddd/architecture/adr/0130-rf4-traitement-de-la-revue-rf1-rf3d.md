---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (décision du 2026-11-10 sur le compte d'usage, « à confirmer métiers <= 2026-12-15 »), architecte]
context-scope: patient-couverture (compte d'usage des payeurs), comptabilite (tri des payeurs), platform (impression de la synthèse mensuelle, gardes d'architecture)
related: ADR 0126 (RF3a, addendum), 0127, 0128 (RF3c-J1), 0129 (RF3d), 0123 (RF1c), 0125 (RF2c) ; revue `.ddd/migration/reviews/revue-RF1-RF3d.md` ; known-bugs KB-RF3-05, KB-RF4-01 à 03
---

# 0130 - RF4 : traitement de la revue indépendante RF1 à RF3d

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles. Un seul comportement DÉCIDÉ change (compte d'usage borné au centre, modèle RF1c / RF2c) ; les autres points sont des durcissements sans effet sur un cas valide.

## Décisions
| N° | Constat | Décision | Réalisation |
|---|---|---|---|
| 1 | 1 (major) | Le code de caisse n'est plus du texte du gabarit JRXML dynamique : chaque code (colonne de la synthèse, agence -> caisse) est un PARAMÈTRE Jasper lié (`CODE_CAISSE_i`, `CODE_AGENCE_i`, `CAST($P{..} AS VARCHAR(255))`). Un code contenant `]]>`, `$P!{..}`, `$P{..}`, `'`, un retour à la ligne, `$`, `{` ou `\` n'empêche plus l'impression | `FacturationSyntheseMensuelleReportService` ; test `CodeDeCaissePiegeSyntheseMensuelleTest` (rouge vérifié : `SAXParseException` sur `]]>`) ; parité : caractérisation de la synthèse imprimée inchangée (23 tests) ; contrat PG16 `SyntheseMensuelleSqlPostgresContractIT` adapté (paramètres liés) et vert |
| 2 | 2 | Le gabarit statique `synthese_mensuelle_facturation.jrxml` (jointures `agence` / `caisse_assurance`, RF3c-J1) est inscrit dans la liste blanche NOMINATIVE et DATÉE (butoir 2027-03-31) ; la garde `TableDesPayeursTest` scanne aussi les `.jrxml` ; effectif asserté 4 -> 5 | `TableDesPayeursTest` |
| 3 | 3 | Les butoirs sont des DATES lues dans la raison (`Butoir : … AAAA-MM-JJ`) et comparées à l'horloge : la garde échoue le lendemain du butoir ; l'entrée unique de `TableDesForfaitsTest` reçoit un butoir (2027-03-31, revue de l'ADR 0124) ; l'exclusion du dossier `/entity/` entier est remplacée par une liste NOMINATIVE (3 entités JPA des payeurs ; vide pour transporteurs et forfaits) | `ButoirsDesGardesDeTables` (outil commun) ; tests de la garde : échec après la date et pas avant, entité inconnue scannée |
| 4 | 4 | Comptabilité : le tri Java des payeurs est départagé par identifiant après nom puis code (ordre indépendant de l'annuaire). Le chargement de l'annuaire entier à chaque page est CONSIGNÉ, pas changé (KB-RF4-03) | `PayeursParLAnnuaire` ; test `PayeursParLAnnuaireOrdreTest` (rouge vérifié) |
| 5 | 5, 6 | Dérogation `-Dcharacterization.approve=true` de RF3a tracée ; renvoi vers l'ADR 0129 pour « aucun `_nouveau_code` » | addendum de l'ADR 0126 |
| 6 | 7 | **Compte d'usage des payeurs borné au centre** (`center_id = ?` sur les agences d'une caisse, les centres payeurs d'une agence, les patients d'un centre payeur) ; 422 `REFERENTIAL_IN_USE` inchangé pour le reste (même centre). KB-RF3-05 CORRIGÉ. Décision prise par cohérence avec RF1c / RF2c, À CONFIRMER par les métiers avant le 2026-12-15 | port `UsageDesPayeurs.nombreDeReferences(centre, niveau, id)` ; `LectureDesPayeursJdbc` ; contrat `PayeursContrat` / `PayeursJdbcContrat` (rouge vérifié : 2 échecs JDBC, double et H2 et PG16 identiques) ; détection §85 de `verifications-production.md` |

## Parité
Aucun golden n'a changé : les goldens `saisie-*/suppressions` (données d'un seul centre) restent verts sans modification ; aucun `_nouveau_code`, aucune archive `_ecarts-legacy` supplémentaire. Le cas d'un payeur référencé par un autre centre n'avait pas de golden (donnée anormale) : il est couvert par le contrat JDBC.

## Résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF4-R1 | Le gabarit statique de la synthèse lit encore `agence` / `caisse_assurance` (KB-RF4-02, butoir 2027-03-31) | RF3c-J1 : classement CNAS / CASNOS / CAMSSP par préfixe du code en SQL |
| RF4-R2 | `PayeursParLAnnuaire` charge l'annuaire du centre entier à chaque page, volume maximal non mesuré (KB-RF4-03) | à revoir si le nombre de centres payeurs par centre croît |

## Conséquences
- Retour arrière : revert Git. Aucun seam (`seamAllowed` reste `[]`), aucun flag.
- Le port `UsageDesPayeurs` change de signature (un argument de plus) ; ses deux implémentations (JDBC, double en mémoire) suivent.

## Questions ouvertes
- Confirmation métier du compte d'usage borné au centre avant le 2026-12-15.
- Revue complémentaire `revue-RF1-RF3d-complement.md` : constats 1 (ordre de `parCaisse` / `parMois`), 2 (collation), 4 et 5 (advice des payeurs, `@PreAuthorize`) non traités par RF4 : TRAITÉS par RF5 (ADR 0131, qui liste aussi les points de la revue restés non traités).
