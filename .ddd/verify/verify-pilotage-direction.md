---
status: proposed
date: 2026-11-02
scope: contexte pilotage-direction (PD1–PD5)
sources: [check-arch.ts, state.ts show, git 2978e639/21f6ff7a/b365a63a, backend/contexts/pilotage-direction, backend/platform/application/direction, .ddd/migration/slices/pilotage-direction-*]
reviewer: relecteur indépendant (lecture seule, aucun build ni test exécuté)
---

# Vérification — contexte Pilotage-Direction (PD1–PD5)

## 0. Contrôles automatiques
`check-arch.ts` : 0 violation. ArchUnit du contexte et `FrontieresDesContextesTest:195-199` : règles présentes, non exécutées.

## 1. Vérité du code
| Slice | Code présent | Filet | Legacy |
|---|---|---|---|
| PD1 Tableau de bord | `TableauDeBord`. Les `indicateurs` sont une **chaîne opaque fournie par le client** (`POST`) ; aucun calcul et aucune consommation des langages publiés | aucun (`characterization/pilotagedirection` absent ; fiche safety-net sans golden) | `DirectionDashboardQueryService` et 18 autres classes `application/direction` **toujours en service** |
| PD2 Instantanés | `InstantaneMensuel`, table **nouvelle** `instantanes_mensuels` | aucun | legacy `direction_snapshot` (`DirectionSnapshotService`) toujours actif |
| PD3 Alertes | `AlerteDirection`, table **nouvelle** `alertes_direction`, une seule alerte **tous types confondus** par centre et par jour | aucun | `direction_alert_history` + `DirectionAlertPolicy` actifs |
| PD4 Export | JSON assemblé par concaténation ; pas de rendu PDF/HTML | aucun | `DirectionReportPdfService` actif |
| PD5 Lectures publiées / retrait | 3 événements publiés sans consommateur ; **aucun retrait** | — | `platform/application/direction` intact (19 fichiers) |

**Le module n'est pas déployé** : `contexts-pilotage-direction` n'est pas une dépendance de `platform/pom.xml` (seuls absences, stock, planification et organisation le sont, `:60-80`). Le module est donc du code mort côté exécution. Ses tables (`db/schema.sql` du module) n'ont aucune migration Flyway.

## 2. Constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| blocker | legacy/stratégique | **Ce n'est pas une migration du legacy mais un second modèle parallèle.** Nouvelles tables à côté de `direction_snapshot` et `direction_alert_history`, sans reprise des règles legacy (politique d'alertes, anonymisation, indicateurs). C'est contraire à la décision « refactoring sur place, mêmes tables et URLs, pas de projection » | `pilotage-direction/src/main/resources/db/schema.sql` ; glossaire `pilotage-direction.md:12` (`direction_snapshot`) ; `platform/.../application/direction/*` | Décision utilisateur : jeter ce squelette, ou le réécrire sur les tables et le comportement legacy caractérisés |
| blocker | archi/sécurité | Contrôleurs sans `@PreAuthorize`, avec `centreId` fourni par le client et sans garde de centre. N'importe qui pourrait écrire un tableau de bord ou une alerte pour n'importe quel centre. **Aucun masquage Direction** (`AnonymityPolicy` non porté). C'est dormant aujourd'hui, mais **bloquant au câblage** | `AlertesDirectionControleur.java:27-58` ; `TableauxDeBordControleur.java:82-93` ; grep `anonym|masqu` = 0 | Rôle DIRECTION + périmètre société (`CenterAccessGuard`) ; porter `AnonymityPolicy` |
| blocker | tests | Aucune caractérisation ni parité. Les statuts `built` ne reposent sur rien d'observable | absence de `characterization/pilotagedirection` ; aucun `*.approved.json` | Caractériser `DirectionIntegrationTest`, `DirectionSnapshotIntegrationTest` et `DirectionReport*` (déjà présents en platform) |
| major | tactique | Modèle anémique ou factice : `TableauDeBord` = `{centreId, String}`. L'invariant R2 « une alerte par centre et par jour » empêche deux types d'alerte le même jour (contrainte `uq_alerte_centre_jour`). La règle legacy n'est pas vérifiée | `TableauDeBord.java:13-35` (fichier) ; `schema.sql` (`uq_alerte_centre_jour`) ; `AlerteDirectionService.java:39-41` | Revoir R2 avec le métier (centre, type, jour ?) |
| major | archi | Fuseau horaire : tous les instants sont en `ZoneOffset.UTC` et la date d'alerte est fournie par le client. Le fuseau du centre (`centers.fuseau_horaire`, V46) est ignoré | `AlerteDirectionService.java:43,57` ; `TableauDeBordService.java:34` | Horloge du centre injectée (comme `HorlogeDesCentresLegacy`) |
| major | archi | `Instant.now()` dans les contrôleurs, `UUID.randomUUID()` pour l'identifiant d'événement dans l'application, JSON construit à la main avec `periode` non échappée (injection JSON) | `ExportsSyntheseControleur.java:38` ; `ExportSyntheseService.java:55-56,77-80` | `Clock` injectée ; sérialiseur JSON |
| major | tactique | Unicité contrôlée par lecture puis écriture, protégée seulement par la contrainte en base. Elle remontera en `DataIntegrityViolation` → 500 | `AlerteDirectionService.java:39-46` | Traduire la violation de contrainte en code métier ; test concurrent |
| major | archi | Entités de domaine renvoyées en JSON (`ResponseEntity<AlerteDirection>`, `<TableauDeBord>`) | `AlertesDirectionControleur.java:28,36,53` | DTO de réponse |
| major | legacy | Documentation client et SDK frontend annoncent `/api/v1/pilotage/*`, **non servis**. Le SDK n'est utilisé par aucun composant | `docs/client/documentation-fonctionnelle.html:5044-5056` ; `frontend/src/app/core/api/pilotage-api.service.ts` (0 usage hors `core/api`) | Retirer de la doc client tant que ce n'est pas déployé |
| minor | legacy | Fiches PD1–PD5 avec texte corrompu (UTF-8 lu en Latin-1 : `AgrÃ©gat`) | `slices/pilotage-direction-pd1-tableau-bord.md:12` | Ré-encoder |
| minor | tests | `.gitkeep` restants dans des paquets non vides | `pilotagedirection/{application,domain,…}/.gitkeep` | Supprimer |
| minor | stratégique | 5 termes `proposed` (Direction, Instantané mensuel, Alerte, État des clôtures, Perte valorisée) ; tactique `proposed` | `glossaries/pilotage-direction.md:11-15` ; `tactical/pilotage-direction.md:2` | Validation métier |

Non trouvés : tests dépendants de la date dans `pilotage-direction/src/test` (0). Aucune lecture SQL des tables d'autres contextes par le module (son SQL ne touche que ses 4 tables). Les tables `direction_*` sont lues uniquement par `platform/application/direction`.

## 3. Cohérence d'état
| Source | Dit | Réalité |
|---|---|---|
| `state.json` PD1–PD5 | `built` | Squelette non câblé, sans filet, legacy intact → **faux** |
| roadmap `:337-341` | `planned` | **Exact** |
| fiches PD1–PD5 | `built` | faux |
| `ADR 0066` | `accepted` | Il fonde le module, pas les tables parallèles : aucun ADR pour celles-ci |

**État réel proposé** : PD1–PD5 → `planned`. Le code existant est un brouillon à requalifier ou à supprimer. Contexte : `architected`.

## 4. Contrôles de production (ordre)
Aucun aujourd'hui, car le module n'est pas déployé. Pour le jour du câblage :
1. Migration Flyway des 4 tables (ou réutilisation de `direction_snapshot` et `direction_alert_history`) validée sur une copie de la base.
2. 403 pour un rôle non DIRECTION ; refus d'un `centreId` hors de la société de l'utilisateur.
3. Masquage : aucune donnée nominative patient dans les réponses.
4. Parité des indicateurs avec `/api/v1/direction/*` sur 2 centres et 2 mois.
5. Instantané d'un mois clôturé figé (non recalculé) ; alerte du jour à la date du **fuseau du centre**.
6. Export PDF identique au legacy (`DirectionReportPdfService`).
7. Retrait de la doc client et du SDK, ou leur alignement.

## 5. Validations restantes pour l'utilisateur
- Décision structurante : réutiliser les tables legacy `direction_*` (refactoring sur place) ou accepter un nouveau stockage. Dans le second cas, un ADR est nécessaire, et c'est contraire à la consigne actuelle.
- Règle R2 (unicité des alertes) : par centre et jour, ou par centre, type et jour.
- Termes du glossaire et tactique `proposed`.
- Reposer l'état PD1–PD5 à `planned`.

## Verdict : **fail**
Top 3 actions :
1. Reposer PD1–PD5 à `planned` et décider du sort du squelette (tables parallèles).
2. Caractériser le legacy `application/direction` (HTTP + goldens), y compris le masquage.
3. Avant tout câblage : sécurité, garde de centre, fuseau du centre, DTO et Flyway.
