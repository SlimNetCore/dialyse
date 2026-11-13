---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md (D1, D2, D11, D12, KB-PL11-12, KB-PL11-13), ADR 0024, 0070, 0111, 0122, 0124, 0126, 0128
---

# ADR 0157 - PL11-1 : SPI de la façade d'administration des référentiels, descripteurs de Patient et couverture

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, codes, tables. Le contrôleur `/api/v1/admin/referentials/{kind}` (7 routes) décrivait lui-même, dans l'énumération `ReferentialKind`, huit référentiels dont cinq (transporteurs, forfaits, caisses, agences, centres payeurs) appartiennent déjà à Patient et couverture (RF1 à RF3) et sont servis par des délégués ACL. Il ne restait en plateforme que leur descripteur (champs, alias d'en-têtes, modèle d'import, libellé, ordre), sous un butoir daté 2027-03-31.

## Décision
1. **SPI** : `ReferentialAdminDelegate#descripteurs()` (en plus de `prend`, `list`, `create`, `update`, `delete`, `importRows`). Un `ReferentialCatalogue` assemble les descripteurs directs de la façade et ceux publiés par les fournisseurs ; le contrôleur lui demande la liste (`GET /`) et la résolution du `{kind}`. Un référentiel inconnu garde son refus (400 `Référentiel inconnu : x`).
2. **Langage publié** : `patientcouverture.publishedlanguage.DescripteurDeReferentiel` (+ `Champ`) et `DescripteursDeReferentiels` (les cinq descripteurs, mêmes champs, alias, exemples, libellés, ordre : les modèles d'import ne changent pas d'un octet). L'ACL `ReferentielsDePatientCouverture` les traduit dans le vocabulaire de la façade.
3. `ReferentialKind` passe d'énumération à classe finale (égalité par segment d'URL) qui ne décrit plus que `CRENEAU`, `SALLE`, `MEDECIN` (migrés par PL11-2 à PL11-4). `ReferentialField` porte le libellé de son référentiel cible (`referenceLabel`) au lieu de le résoudre par un registre statique.
4. L'ordre d'affichage historique de l'écran est conservé (ordre d'import, puis forfaits, créneaux, salles, médecins, caisses, agences, centres payeurs, transporteurs).
5. **KB-PL11-12 corrigé** : JSON seul sur les sept routes (`produces`/`consumes`) ; 405 (+ `Allow`), 415, 406 (sans corps), 400 (import sans fichier) propres au lieu de 500, par la classe `MethodesNonSupporteesDeLaDirectionAdvice` étendue au préfixe de l'admin. Le kind inconnu (400) et l'insensibilité à la casse sont reproduits.
6. **Audit enrichi** : l'identifiant de l'élément créé et le type de référentiel (libellé « Administration des référentiels : <action> (<slug>) ») sont enregistrés via les attributs d'audit existants.
7. **KB-PL11-13 reproduit** : SUPERADMIN reste refusé (403 `ROLE_SCOPE`) ; annotation trompeuse `hasAnyRole('ADMIN','SUPERADMIN')` remplacée par `hasRole('ADMIN')` et Javadoc corrigée. À confirmer par les métiers avant le 2026-12-15.

## Butoirs 2027-03-31 soldés (D12 : aucun report)
`DescripteursResiduelsDeLAdministrationGeneriqueTest` supprimé (5 résiduels RF1-R1, RF2-R1, RF3-R1 ; 4 lignes d'ADR 0122, 0124, 0126, 0128 marquées « SOLDÉ par PL11-1 »). Remplacé par la garde anti-retour `LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest` : seules trois constantes publiques dans `ReferentialKind`, aucune référence de production à `ReferentialKind.TRANSPORTEUR/FORFAIT/CAISSE/AGENCE/CENTRE_PAYEUR`. Restent en butoir (hors PL11-1) : les entrées d'`ReferentialAdminJdbcAdapter`/`ReferentialAdminTables` des gardes de tables (PL11-2 à PL11-6).

## Conséquences
- `PatientMigrator`, `HistoryRules` et `ReferentialAdminJdbcAdapter#findAllForMatching` référencent les cinq référentiels par `ReferentielsDePatientCouverture` ; leur appariement par lectures publiées est PL11-5.
- Le moteur générique de références (`REFERENCE`) n'est plus exercé que par le lecteur de fichiers pour les référentiels délégués ; il disparaît avec le moteur (PL11-6).
- Pas de routage Strangler, flag ni projection.
