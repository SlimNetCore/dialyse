# Filet de non-régression — O5 retrait `bc-referential`

Slice : `organisation-o5-retrait-bc-referential`. Contexte : `organisation`.
Date : 2026-11-01. Phase : caractérisation avant bascule (aucune modification du legacy).

## Point d'observation

- HTTP : `SocieteRestController` (`/api/v1/societes`), `ReferentialRestController`
  (`/api/v1/referentials/*`), `ReferentialAdminRestController`
  (`/api/v1/admin/referentials/*`) + MockMvc.
- SQL : `societes`, `centers`, `salle`, `position_creneau`, tables référentielles (garde `TableDeLOrganisationTest`
  verte).

## Tests existants couvrant la slice (doivent rester verts pendant toute la bascule)

- `SocieteIntegrationTest` (platform) : CRUD société/centre, transfert, pagination, scoping.
- `ReferentialAdminIntegrationTest` (platform) : CRUD générique, import/export, templates.
- `TabularFileReaderTest` : lecture des fichiers d'import.
- `EquipementHttpCharacterizationIntegrationTest`, `GenerateurIndisponibleCharacterizationIntegrationTest`,
  `EvictionCacheGenerateursSynchroneIntegrationTest` : consommateurs GMAO des référentiels.
- Garde `TableDeLOrganisationTest` (3/3) + `check-arch` (0 violation).

## Scénarios verrouillés (multi-centre)

1. CRUD société/centre réservé SUPERADMIN, isolation par `center_id`.
2. Au moins un centre actif par société (specification legacy).
3. Import/export référentiels : validation des valeurs, rapport d'import.
4. Cache référentiels invalidé à chaque écriture, clé incluant `centerId`.

## Vérification 2026-11-01 : filet VERT

`SocieteIntegrationTest` + `ReferentialAdminIntegrationTest` + `TabularFileReaderTest` :
BUILD SUCCESS (contexte Spring complet, H2).

**Bug pré-existant trouvé et corrigé pour faire passer le filet** (aucun rapport avec
la migration) : `LigneDemandeExamenJpaEntity` ne mappait pas `center_id` alors que
`db/schema.sql` l'exige `NOT NULL` — avec `ddl-auto: update`, Hibernate créait la table
sans la colonne puis `CREATE INDEX ... (center_id)` échouait, cassant **tout** le
contexte Spring des tests platform. Correctif : champ `centerId` ajouté à l'entité et
renseigné dans `DemandeExamenRepositoryAdapter` depuis le centre de la demande mère (règle multi-centre §2). Sans ce
correctif, aucun test d'intégration platform ne peut
tourner.

## Points d'attention

- Ne pas démarrer la bascule avant comblement des écarts de parité (§ slice O5) :
  logos, activer/désactiver, transfert, référentiels non couverts, admin générique.
- Le retrait du module exige la mise à jour de `backend/pom.xml`, `DomainServiceConfig`,
  `MigrationConfig`, et le retrait des codes d'erreur orphelins dans
  `docs/reference/regles-de-gestion` (`ReglesDeGestionDocumentationTest`).
