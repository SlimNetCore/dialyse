# GEL du module pilotage-direction (2026-11-02)

Statut : **gelé, non branché, hors réacteur Maven** (`backend/pom.xml` ne le liste plus, `backend/Dockerfile` ne copie plus son `pom.xml`, `platform/pom.xml` ne l'a jamais référencé). Le dossier, ses sources, ses tests, l'ADR 0066, les fiches (`.ddd/migration/slices/pilotage-direction-pd*.md`), le tactique (`.ddd/tactical/pilotage-direction.md`) et le glossaire sont conservés pour la reprise. Décision déléguée à l'assistant par l'utilisateur le 2026-11-02. ADR : 0075.

## Pourquoi
- **Modèle parallèle contraire à l'ADR 0024** (refactoring sur place : mêmes tables, mêmes URL) : tables nouvelles `tableaux_de_bord`, `instantanes_mensuels`, `alertes_direction`, `exports_synthese` à côté de `direction_snapshot` et `direction_alert_history` (legacy, V1), sans migration Flyway (prouvé par `backend/scripts/pilotage-direction-pg16-rejeu.sh`). Les règles legacy (`DirectionAlertPolicy`, `AnonymityPolicy`, indicateurs) ne sont pas reprises.
- **Sécurité** : aucun `@PreAuthorize`, `centreId` fourni par le client, aucune garde de centre, aucun masquage Direction (`AnonymityPolicy` non porté) : IDOR inter-centres au câblage.
- **Aucune caractérisation ni parité** : aucun golden `_nouveau_code`, les statuts `built` ne reposaient sur rien d'observable.
- **Modèle factice** : `TableauDeBord` = `{centreId, chaîne opaque fournie par le client}`, aucun calcul ; événements publiés sans consommateur.
- **Règle R2** (une alerte par centre et par jour, tous types confondus, `uq_alerte_centre_jour`) non vérifiée contre le legacy ; unicité par lecture puis écriture (500 sur violation).
- **Technique** : UTC au lieu du fuseau du centre (`centers.fuseau_horaire`), `Instant.now()` dans les contrôleurs, `UUID.randomUUID()` dans l'application, JSON d'export concaténé à la main (`periode` non échappée), entités de domaine sérialisées en HTTP.
- **Contrat client annoncé mais jamais servi** : `/api/v1/pilotage/*` (documentation client, SDK frontend sans consommateur) : retirés au gel.

## Ce qui a été fait au gel
- Audit de production (A) : depuis `2978e639^`, **aucun fichier legacy de production Direction n'a été modifié** (`application/direction`, `DirectionDashboardRestController`, `DirectionSnapshotRestController`, tests Direction : diff vide). Les commits `2978e639`, `21f6ff7a`, `b365a63a` n'ajoutent au legacy aucune table ni colonne pour ce module ; le `schema.sql` (H2) de `platform` ne contient aucune table parallèle (elles ne vivaient que dans le `schema.sql` du module) ; `covered.json` : aucune entrée Direction ajoutée par ces commits, **rien à retirer**. `CacheConfig` : pas de cache du module. Rejeu PostgreSQL 16 jetable : V1..V52 s'appliquent, colonnes lues/écrites en JDBC par le legacy présentes, tables parallèles absentes.
- Réacteur et `Dockerfile` : module retiré. `LectureSeuleDuPilotageTest` désactivé (`@Disabled` motivé) ; module exclu du balayage de `ReglesDeGestionDocumentationTest` ; règle `FrontieresDesContextesTest` conservée (vide, `allowEmptyShould`).
- Documentation : lignes `/api/v1/pilotage/*` retirées de l'annexe de traçabilité et de la documentation client (régénérée) ; SDK frontend `pilotage-api.service.ts` et son spec supprimés (0 usage hors `core/api`).

## Filet de caractérisation du legacy Direction existant (inventaire pour la reprise)
Aucun dossier `characterization/pilotagedirection` : le filet est réparti entre les contextes dont la Direction lit les chiffres (sous `backend/platform/src/test`).
| Domaine | Classes / goldens |
|---|---|
| Capacité des centres (P6) | `characterization/planification/semaine/CapaciteDesCentresCharacterizationIntegrationTest` (`DirectionCapaciteQueryService`, masquage par centre, jours fermés) |
| Absences (A4) | `characterization/absencespatients/consultation/LecturesDeAbsencesParLaDirectionEtLaSeance…IntegrationTest` (`DirectionAbsencesQueryService`) |
| GMAO | `characterization/gmao/pilotage/DirectionGmaoCharacterizationIntegrationTest` (`DirectionGmaoQueryService`) |
| Facturation (F5) | `characterization/facturation/pilotage/LecturesFinancieresDirectionCharacterizationIntegrationTest` (part financière, RG-FAC-026) |
| Stock | `characterization/stock/referentiel/ValorisationDeLaDirectionCharacterizationIntegrationTest`, `DirectionStockGroupesQueryServiceTest` |
| Tests classiques du legacy (hors goldens) | `infrastructure/web/rest/Direction{,Breakdown,Realtime,Report,Snapshot}IntegrationTest`, `application/direction/{DashboardDiff,DirectionAlertHistoryService,DirectionAlertPolicy,DirectionCapaciteIntegration,DirectionPolicies,PatientsSurEquipementIndisponible}Test`, `infrastructure/security/RoleScopeFilterTest` |
Manques (à caractériser avant tout code, `/ddd-safety-net` par slice) : vue d'ensemble, indicateurs et ventilation HTTP (`/overview`, `/indicators`, `/breakdown`) avec masquage `AnonymityPolicy` par centre ; historique des alertes (`/alerts/history`, `DirectionAlertHistoryService` ouverture/résolution) ; instantanés mensuels (`/snapshots/{mois}`, immuabilité, `DirectionSnapshotService`) ; rapport PDF (`/report`, `DirectionReportPdfService`) ; temps réel (`DirectionRealtimeService`).

## Plan de reprise (sur place)
1. **Filet de caractérisation du legacy Direction d'abord** : compléter les manques ci-dessus (HTTP + goldens, masquage compris), `covered.json` mis à jour fichier par fichier.
2. **Slices PD1 à PD5 sur place** : mêmes tables `direction_snapshot` et `direction_alert_history`, mêmes URL `/api/v1/direction/*` (un seul contrôleur par route), `@PreAuthorize` rôle DIRECTION, périmètre société et garde de centre issus de la session (jamais du client), masquage par centre via `AnonymityPolicy`, « aujourd'hui » dans le fuseau du centre, `Clock` injectée, DTO de réponse, sérialiseur JSON, violation de contrainte traduite en code métier.
3. **Règle R2 des alertes à trancher à la reprise** sur la base du comportement caractérisé de `DirectionAlertHistoryService` (le legacy identifie une alerte ouverte par centre et code, pas par jour) : question consignée dans la fiche PD3, non tranchée au gel.
4. Rebrancher le module dans `backend/pom.xml`, `platform/pom.xml`, `Dockerfile` seulement après parité verte ; retrait de `application/direction` par PD5.
