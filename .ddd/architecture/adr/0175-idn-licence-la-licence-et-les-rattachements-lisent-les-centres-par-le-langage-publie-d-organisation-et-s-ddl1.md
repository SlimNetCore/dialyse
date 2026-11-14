---
status: accepted (S-DDL1 et phase 2 livrées le 2026-11-14 ; KB-LIC-1 et KB-LIC-2 corrigés sur décision de l'utilisateur, KB-LIC-3 et 4 reproduits)
date: 2026-11-14
decision-makers: [utilisateur, architecte]
context-scope: platform (Identité et accès, licence, transitoire) + organisation (langage publié)
related: ADR 0024, 0107, 0174 ; `.ddd/migration/reviews/idn-licence-realisation.md` ; `inventaire-platform-restant-v2.md` lignes 13 et 16
---

# 0175 - IDN-licence et S-DDL1 : DDL H2 des tables d'Identité et d'Organisation, la licence lit les centres par le langage publié

Refactoring sur place (ADR 0024) : mêmes URLs, mêmes tables, mêmes JSON.

## S-DDL1 (livré)
`db/schema.sql` crée seul `societes`, `centers`, `license`, `app_role`, `app_user`, `app_user_role`, `app_user_center`, dans l'ordre des clés étrangères, avec les colonnes, largeurs, nullités, défauts et unicités de Flyway V1 + V12 (`must_change_password`) + V46 (`fuseau_horaire`) + V56 (`centers.actif` défaut TRUE, NOT NULL) ; aucune migration Flyway modifiée. Aucune unicité sur `app_user.username` ni `app_role.code` (comme en production, KB-PLN-24). Les anciens DDL de `app_role`/`app_user` (largeurs 50/80/150, UNIQUE) n'étaient jamais appliqués (Hibernate passe avant) : remplacés. `SchemaH2DeLIdentiteEtDeLOrganisationTest` rejoue le script sur une base vierge et compare la signature (colonne, type, largeur, nullité, défaut), les clés étrangères et les unicités ; la signature a aussi été comparée une fois au DDL généré par Hibernate (identique).

## IDN-licence (phase 2, proposée)
1. **Langage publié minimal** (livré, contrat vert sur JDBC et faux) : `AnnuaireDesCentres.codeDuCentre(centre)` (code signé dans la clé de licence) ; `AnnuaireDesSocietes.centresAvecLeurSociete()` (tous les centres avec nom, société et raison sociale, tri par nom côté Organisation). Le reste est composé : existence d'un centre = `societeDuCentre` ; centres actifs d'une société = `societe(id).centres` filtrés sur `actif` (déjà triés par nom).
2. `LicenseService`, `LicenseRestController`, `DevLicenseBootstrapInitializer` : injection des deux annuaires à la place de `CenterJpaRepository`/`SocieteJpaRepository` ; `DevLicenseBootstrapInitializer` lit `tousAvecNom()`.
3. `AppUserJpaEntity.centers` : `Set<CenterJpaEntity>` devient `Set<UUID> centerIds` (`@ElementCollection` sur la table `app_user_center`, colonne `center_id`) ; `UserRestController.toUserPayload` relit le nom par `nomDuCentre` ; `UserSpecifications` filtre par identifiant, et le filtre « nom de centre contient » résout d'abord les identifiants par `tousAvecNom()` avec la sémantique `LIKE` actuelle (casse ignorée, `%` et `_` joker).
4. Suppression de `CenterJpaEntity`, `SocieteJpaEntity`, `CenterJpaRepository`, `SocieteJpaRepository` (méthodes `findBySocieteIdIn`, `existsOtherWithCode`, `search`, logos : sans appelant) ; `TableDeLOrganisationTest.ENTITES_JPA_CONNUES` vidée. Aucune écriture sur `centers`/`societes` ne subsiste dans platform (toutes dans `CentresJdbc`/`SocietesJdbc`).
5. **Écarts corrigés sur décision de l'utilisateur** (golden `_nouveau_code`, ancien archivé en `_ecarts-legacy/**/*_avant_idn_licence.legacy.json`) : KB-LIC-1 (refus d'activation : 500 sans motif -> 400 avec motif, dans `LicenseRestController.activate`) et KB-LIC-2 (émission avec un corps illisible : 500 -> 403 avant 400, puis 400, dans `DatesIllisiblesDeLaDirectionAdvice`). Trois goldens changés (`emission/acces_par_centre`, `emission/refus_par_societe`, `licence/activation`) ; les 7 autres de `idn-licence` et les 19 de `idn-lecture` sont inchangés. KB-LIC-3 (aucun contrôle d'état du centre/société à l'émission par centre) et KB-LIC-4 (`authority/verify` : 403 sans session) restent **reproduits, à confirmer métiers/DPO <= 2026-12-15**. Restent aussi reproduits : `GET /licenses/authority/verify` sans `jti` et `ADMIN` sans centre sur `/activate` (500).
6. `app_user_center` (collection de valeurs de `app_user`, créée par Hibernate avant `schema.sql`) n'a plus de FK vers `centers` côté Hibernate : `schema.sql` la pose par `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS` (la production l'a depuis Flyway V1). Sans elle, le rattachement à un centre inconnu cessait d'échouer (golden `rattachements`).

## Conséquences
- Le test d'isolation de l'architecture n'a plus aucune entité JPA d'Organisation à tolérer ; le critère d'avancement O4 reste à 0.
- `GET /users` : une lecture d'annuaire de plus par utilisateur et par centre rattaché (`nomDuCentre`) ; volumétrie faible ; si besoin, une lecture `tousAvecNom()` par requête.
