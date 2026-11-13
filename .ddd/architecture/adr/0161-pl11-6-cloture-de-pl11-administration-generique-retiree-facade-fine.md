---
status: accepted
date: 2026-11-13
sources: pl11-analyse.md (4 PL11-6, 6), pl11-decisions.md (D12), ADR 0024, 0070, 0157, 0158, 0159, 0160
---

# ADR 0161 - PL11-6 : clôture de PL11, administration générique retirée, façade fine

## Contexte
Refactoring sur place (ADR 0024) : mêmes URL, rôles, codes, JSON. Après PL11-1 à PL11-5, les huit référentiels de `/api/v1/admin/referentials/{kind}` (créneaux, salles, médecins, transporteurs, forfaits, caisses, agences, centres payeurs) sont décrits et administrés par leur contexte propriétaire (Organisation, Patient et couverture). Il ne restait en plateforme que le moteur générique en SQL dynamique, sans plus aucune table décrite.

## Décision
1. **Retirés** : `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`, `ReferentialAdminRepositoryPort` (dont `findAllForMatching`), `ReferentialCacheEvictor` (seul appelant : l'adaptateur ; chaque contexte invalide ses caches), `ReferentialValuesValidator` (moteur de validation générique sans type restant), `InMemoryReferentialAdminRepository` et `ReferentialAdminDomainServiceTest` (test du moteur retiré). `ReferentialKind` perd `directs()`/`direct()`.
2. **Façade fine** : `ReferentialAdminDomainService` ne garde que la normalisation de la pagination, la lecture du fichier d'import (en-têtes, colonnes manquantes, bornes 5000 lignes / 500 anomalies, compte rendu) et la distribution au SPI `ReferentialAdminDelegate` ; son constructeur ne reçoit plus que le délégué. Les cas de la lecture de fichier (fichier sans en-tête, trop long, anomalies tronquées, référentiel sans délégué) sont couverts par `ReferentialAdminDelegationTest`.
3. **Règle permanente sans date** (`FrontieresDesContextesTest`) : aucune classe de `infrastructure.referential.admin..` ni `application.referential..` ne dépend de `org.springframework.jdbc`, `javax.sql` ou `jakarta.persistence` ; `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`, `ReferentialAdminRepositoryPort` et `ReferentialValuesValidator` ne peuvent plus exister. Le test `LaFacadeDesReferentielsNeDecritQueLesReferentielsEncoreDirectsTest` garde l'absence de constantes `ReferentialKind` et des sources retirées.
4. **Butoirs 2027-03-31 liés à PL11 : tous soldés, aucun report** (D12). Les entrées `ReferentialAdminJdbcAdapter` / `ReferentialAdminTables` des tables d'exceptions `TableDeLOrganisationTest`, `TableDuPatientCouvertureTest`, `TableDesEquipementsDeLaGmaoTest`, `TableDuDossierMedicalTest`, `TableDesSeancesTest` avaient déjà été retirées par PL11-1 à PL11-4 ; il n'en reste aucune. Les butoirs sans lien avec PL11 (Identité et accès, Forfaits de séance, lecteurs Séance/Pilotage de Patient et couverture, Planification) ne sont pas touchés.
5. **ADR 0070** passe `superseded` par cet ADR (l'administration générique n'est plus hébergée en plateforme).

## Conséquences
- Aucun changement de comportement : tous les goldens (o2, rf*, pl11-0-*) sont inchangés.
- Une nouvelle administration de référentiel = un contexte qui publie un descripteur et un délégué ; la façade n'est pas modifiée.

## Questions ouvertes
Les questions métiers de PL11-2 à PL11-4 (réactivation, capacité effaçable, fermeture de salle) restent à confirmer avant le 2026-12-15 (ADR 0158 à 0160).
