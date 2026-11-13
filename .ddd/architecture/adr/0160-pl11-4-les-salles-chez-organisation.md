---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4, 6), pl11-decisions.md (D3, D4, D8, D9, D10, D11, D12), pl11-0-filet.md, ADR 0024, 0157, 0158, 0159
---

# ADR 0160 - PL11-4 : les salles (table salle) chez l'Organisation

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, codes, table. `/api/v1/admin/referentials/salles` écrivait `salle` par SQL dynamique en plateforme (KB-PL11-1, 2, 3, 7, 8).

## Décision
1. **Cas d'usage dans l'Organisation** : `GererLesSalles` (langage publié `GestionDesSalles`, refus `RefusDeSalle`), VO `SaisieDeSalle` (code 50, nom 255, isolement NON/OUI, capacité 1-999 ; mêmes anomalies qu'avant), agrégat `Salle` enrichi (isolement, capacité, actif). Descripteur publié `DescripteursDeLOrganisation.SALLE` (`DescripteurDeReferentiel.Champ` gagne un genre TEXTE/CHOIX/ENTIER) ; ACL `SallesParOrganisation`. `ReferentialKind.SALLE` et l'entrée de `ReferentialAdminTables` disparaissent : la façade ne décrit plus aucun référentiel en direct.
2. **D3** : le contrôle d'usage additionne les ports `UsageDeLaSalle` de chaque propriétaire : Patient et couverture (patients, mouvement_patient), Séance (seances), Planification (infirmier_affectation, infirmier_remplacement, planification_calendrier_case, deplacement_temporaire), GMAO (équipements, via le langage publié `EquipementsDeLaSalle`, ACL `UsageDeLaSalleParLaGmao`). Compte par identifiant seul (KB-PL11-2 reproduit).
3. **D4** : une salle utilisée est désactivée (`actif=false`, 204) ; non utilisée, supprimée. Migration `V59__salle_desactivation_logique.sql` + `schema.sql`. Les lectures de Planification (`SallesEtCreneauxDuCentreJdbc`) ne proposent plus une salle désactivée (test de contrat d'abord) ; les libellés historiques restent lisibles. Recréer ou réimporter le code d'une salle désactivée la réactive (calque de D6).
4. **D8** : une salle modifiée ou réimportée sans capacité conserve sa capacité ; l'isolement absent est lui aussi conservé. Une capacité `null` explicite vaut « absente » (conservée).
5. **D10** : erreur d'un propriétaire = refus 422 `REFERENTIAL_USAGE_UNVERIFIABLE`, salle conservée.
6. **D9 hors PL11** : l'effet de la fermeture d'une salle (`ouverte`) sur la planification reste inchangé (KB-ORG-07).
7. **PL11-5** : la reprise apparie par le port `AppariementDeReferentiels` (adapter `AppariementParLesContextes`, lectures publiées des contextes) ; `PatientMigrator` et `HistoryRules` ne dépendent plus du port d'administration.

## Questions ouvertes (métiers, avant le 2026-12-15)
- Réactivation d'une salle désactivée de même code : à confirmer.
- L'administration ne peut plus effacer une capacité (null = conservée) : à confirmer.
- D9 : que doit faire la planification d'une salle fermée ?

## Butoirs 2027-03-31 (D12 : aucun report)
Soldés : entrées `ReferentialAdminTables` de `TableDesEquipementsDeLaGmaoTest` et `TableDuPatientCouvertureTest` supprimées ; `TableDesSeancesTest` : 4 -> 2 fichiers. Reste `ReferentialAdminJdbcAdapter` (sans client de production) à retirer en PL11-6.

## Conséquences
Goldens relus un par un : `refus-d-usage-par-dependance`, `erreur-du-controle-d-usage`, `modification-remplacement-complet`, `import-des-salles`, `audit` (suffixe `_nouveau_code`) ; archives `_ecarts-legacy/pl11-0-salles/*_avant_pl11-4.legacy.json`. Pas de routage Strangler.
