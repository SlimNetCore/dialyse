---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md (D3, D5, D6, D7, D10, D11, D12), pl11-0-filet.md, ADR 0024, 0157, 0158
---

# ADR 0159 - PL11-3 : les médecins (table medecin) chez l'Organisation

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, codes, table. L'administration générique `/api/v1/admin/referentials/medecins` écrivait `medecin` par SQL dynamique en plateforme, avec des règles différentes de celles de la route d'Organisation `/organisation/referentiels/medecins` (KB-PL11-4, 5, 6).

## Décision
1. **Cas d'usage dans l'Organisation** : `GererLesMedecins` (langage publié `GestionDesMedecins`, refus `RefusDeMedecin`) : créer, modifier, désactiver, lister, importer. `SaisieDeMedecin` (VO : nettoyage des espaces, nom obligatoire, 255 caractères par champ) et `Medecin.verifierLUnicite` portent les règles ; mêmes anomalies, codes et messages qu'avant (goldens PL11-0 identiques pour création, modification, validations, lecture, import, modèle d'import).
2. **Descripteur publié** : `DescripteursDeLOrganisation.MEDECIN` ; la façade le reçoit par le SPI de l'ADR 0157 (`MedecinsParOrganisation`, ACL `acl/organisation`). `ReferentialKind.MEDECIN` et l'entrée `ReferentialAdminTables` disparaissent ; la façade ne décrit plus en direct que les salles (PL11-4).
3. **D7, une seule règle d'unicité par centre**, la même pour l'administration et la route d'Organisation. Clé exacte : `nom|prénom`, chacun avec espaces de bord supprimés, espaces internes réduits à un, minuscules (accents conservés) ; un prénom absent ou vide vaut chaîne vide ; comparée parmi les médecins ACTIFS du centre. Cas limites conservés : « Benali » sans prénom et « Benali Karim » sont deux médecins ; un import sans colonne prénom ne retrouve pas un médecin qui a un prénom (un « Benali » sans prénom est créé) ; le même nom dans un autre centre est permis. Refus : administration 400 `ALREADY_EXISTS` (inchangé) ; route d'Organisation 422 `MEDECIN_DEJA_EXISTANT` (nouveau code, avant : doublon accepté).
4. **D5** : désactiver un médecin utilisé (patients, prescriptions, ordonnances, séances) est autorisé, dans l'administration (204 au lieu de 422 `REFERENTIAL_IN_USE`) comme dans la route d'Organisation (inchangé). 2e désactivation : administration 422 `REFERENTIAL_NOT_FOUND` (inchangé), Organisation 200 (inchangé).
5. **D6** : créer ou réimporter un médecin de même clé qu'un médecin désactivé le RÉACTIVE (la saisie remplace nom, prénom, spécialité ; même identifiant) au lieu de créer un doublon, dans l'administration et la route d'Organisation.
6. **D3 / D4 / D10 : pas de ports `UsageDuMedecin`.** Il n'existe aucune suppression physique de médecin (façade et route d'Organisation désactivent, V52, KB-ORG-03) ; D5 interdit tout refus fondé sur l'usage ; un comptage n'aurait donc aucun effet et aucune erreur de contrôle d'usage n'est possible (D10 sans objet). Si une suppression physique des médecins non utilisés était voulue un jour, il faudrait alors compter chez chaque propriétaire, y compris `ordonnances.medecin_id` (varchar).
7. **Cache `ref.medecins`** : éviction PAR CENTRE après commit, pour toutes les écritures (`MedecinsJdbc` suit les pages `centre:page:taille` mises en cache). Parité avec l'ancienne administration ; la route d'Organisation, qui videait avant le cache de tous les centres (KB-PL11-9), n'évince plus que son centre (golden `cache-des-medecins_nouveau_code`).
8. **Appariement de la reprise** : `findAllForMatching(MEDECIN)` lit `GestionDesMedecins#proposes` ; `PatientMigrator` référence `ReferentielsDeLOrganisation.MEDECIN`. `HistoryRules` n'apparie aucun médecin. PL11-5 reste à finir pour les salles.

## Questions ouvertes (métiers, avant le 2026-12-15)
- Les valeurs saisies par la route d'Organisation sont désormais nettoyées (espaces de bord, vide = absent) comme dans l'administration (avant : stockées brutes). Accepté ; à confirmer.
- Réactivation d'un médecin désactivé qui remplace sa spécialité par la saisie : à confirmer (D6 « à valider métiers »).

## Butoirs 2027-03-31 (D12 : aucun report)
Soldé par PL11-3 : l'entrée `ReferentialAdminTables.java` de `TableDuDossierMedicalTest` (prescriptions d'un médecin) est supprimée ; la garde anti-retour de l'ADR 0157 interdit `MEDECIN`, `medecin_*` et le SQL `medecin` dans l'adaptateur et la table de correspondance. Les autres entrées de `ReferentialAdminTables`/`ReferentialAdminJdbcAdapter` ne concernent plus que la salle (PL11-4, retrait PL11-6). Aucune date repoussée.

## Conséquences
- Goldens relus un par un (écarts décidés uniquement) : `refus-d-usage_nouveau_code`, `desactivation-d-un-medecin-utilise_nouveau_code` (D5), `medecin-desactive-recreation-et-reimport_nouveau_code` (D6), `unicite-organisation-et-administration_nouveau_code` (D7), `audit_nouveau_code` (D5/D7), `cache-des-medecins_nouveau_code` (éviction par centre) ; archives `_ecarts-legacy/pl11-0-medecins/*_avant_pl11-3.legacy.json`.
- Pas de routage Strangler, flag ni projection.
