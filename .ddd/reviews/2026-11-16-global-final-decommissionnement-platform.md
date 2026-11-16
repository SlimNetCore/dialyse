---
status: proposed
date: 2026-11-16
scope: décommissionnement de backend/platform, HEAD 366ce7b0 (ACL(c), DOC5a-j, CLOT1 ADR 0203-0206, CLOT2, CLOT3)
method: lecture seule ; check-arch.ts (0 violation) ; state.ts show ; aucun test ni build lancé (résultats lus dans les rapports de réalisation)
---

# Revue globale finale : décommissionnement de platform

Verdict : **pass-with-actions**. Aucun blocker. Les 5 majors et 7 minors du 2026-11-15 sont soldés ou justifiés par ADR ; restent un contrôle de production (M5 ancien) et quelques minors.

## 1. Actions de la revue précédente
| Ancien | État | Preuve |
|---|---|---|
| M1 migrators de reprise | Justifié : ADR 0204, noyau de l'outil, retrait à l'événement « fin de la reprise de production » (aucune date) | `.ddd/architecture/adr/0204-*.md:20-22` |
| M2 Flyway centralisé | Justifié : ADR 0205, ADR 0014 amendé, aucun V60, tout nouveau V* reste en platform | `adr/0205-*.md` §Décision 1-4 |
| M3 relais métier dans STOMP | Soldé : `StockEvenementsListener` ne fait que relayer ; invalidation du cache déplacée dans pilotage-direction | `platform/.../websocket/StockEvenementsListener.java:12-31` ; `pilotage-direction/.../infrastructure/acl/stock/StockDesGroupesParLeStock.java` (référence au cache) ; `clot1-realisation.md` action A |
| M4 context-map périmé | Soldé : 13 extraits, R-DOC5, R-STK-PIL, R-ORG-AVAL, R-REPRISE, état de platform, 10 ACL | `.ddd/strategic/context-map.md:127-152` |
| M5 risque production `queryString` | **Non soldé, c'est un acte de déploiement** (voir M-1 ci-dessous) | `JrxmlSecurityValidator.java:298` |
| m1 dossiers vides `acl/*` | Soldé : aucun dossier vide sous `platform/src/main/java` | `find -type d -empty` : vide |
| m3 butoirs | Soldé (CLOT2/CLOT3) : voir §3 | |
| m4 retrait des 10 ACL | Soldé par condition (événement, pas de date) | ADR 0204 ; context-map:143 |
| m5 glossaires `jeu de données` | **Partiel** (voir m-1) | `glossaries/documents.md:34`, `gmao.md` |
| m6 Organisation -> Stock/GMAO | Soldé : ADR 0206, R-ORG-AVAL | context-map:150 |
| m7 narration RG-DOC-008 | Non revérifié (hors périmètre de ce jalon, faible) | |

## 2. Résidus dans backend/platform/src/main (hors noyau délibéré)
| Chemin | Raison | Statut |
|---|---|---|
| `infrastructure/stock/RelaiDesInventairesVersWebSocket.java` | Relais Stock vers STOMP rangé dans un paquet « stock » de platform | Assumé noyau de transport (ADR 0203 §2) ; seul fichier encore nommé d'après un contexte |
| `infrastructure/websocket/WebSocketDirectionPublisher.java`, `WebSocketInventaireEventPublisher.java`, `WebSocketStockEventPublisher.java`, `StockEvenementsListener.java` | Implémentent des ports de Pilotage, Stock et Séance | Assumé ADR 0203 (topics et payloads figés) |
| `infrastructure/config/CacheConfig.java:26,60` | Enregistre `stock.valorisation.articles` (cache propriété du Pilotage) et son TTL | Résidu mineur, non traité par ADR 0203 |
| `application/migration/MigrationApplicationService.java`, `infrastructure/{migration,importer}`, `config/MigrationConfig.java` | Outil de reprise | Noyau (ADR 0204) |
| `infrastructure/acl/{organisation(4),patientcouverture(4),reprise(1),gmao(1)}` | 10 ACL conservées (cycle Maven) | Noyau transitoire (ADR 0192) |
| `db/migration` V1-V59 (57 fichiers), `db/schema.sql`, `db/seed.sql`, `db/seed-production.sql` | Flyway central et H2 | Noyau (ADR 0205) |
| `infrastructure/config/{DevLicenseBootstrapInitializer, DevSuperAdminBootstrapInitializer, SeedPasswordInitializer, DomainServiceConfig}` | Amorçage identité et licence | Noyau |

Aucun autre résidu : pas de dossier vide, pas de paquet de contexte (patient, séance, planning, reporting, notification).

## 3. Contrôles
| Contrôle | Résultat | Preuve |
|---|---|---|
| check-arch.ts | 0 violation (0 baseline) | exécution du 2026-11-16 |
| state.ts show | pipeline `verify` done ; slices built, S1-S3 `verified` ; aucune slice `built` hors du pipeline legacy | `state.ts show` |
| Butoirs et échéances dans les gardes | Aucun. Grep `butoir|REVUE|LocalDate.of(20` sur `architecture/**` : 1 seule occurrence, texte « Aucune exception, aucun butoir » ; `ButoirsDesGardesDeTables` supprimé ; `LECTURE_DIRECTE` sans butoir | `LaRepriseDeDonneesNeTouchePlusLesTablesDesContextesTest.java:24` ; `TableDeLOrganisationTest.java:38` |
| `<queryString` dans les jrxml de `src/main` | 0 fichier | grep count |
| `seamAllowed` | `[]` | `.ddd/safety-net/covered.json:112` |
| covered.json | 108 chemins, tous existent | script de contrôle |
| ADR | 0001-0206, 206 fichiers, aucun doublon ni trou ; 0203-0206 acceptés | `.ddd/architecture/adr/` |
| Gardes `Table*Test` | 13 gardes de tables (Organisation, Planification, Absences, Analyses, Equipements GMAO, Forfaits, Payeurs, Transporteurs, Séances, Dossier médical, Patient, Pilotage, Stock) + `LaRepriseDeDonnees...` ; aucune horloge (CLOT3) | `platform/src/test/.../architecture/` |
| Glossaires | 13 glossaires, un par contexte, mis à jour jusqu'au 2026-11-15 | `.ddd/strategic/glossaries/` |

## 4. Constats
| Sév. | Constat | Preuve | Action |
|---|---|---|---|
| major M-1 | Risque de production DOC5 inchangé : tout téléversement avec `<queryString>` est refusé, y compris le réimport d'un export ancien ; les modèles déjà stockés avec requête restent en base. | `contexts/documents/.../JrxmlSecurityValidator.java:298` | Avant mise en production : compter les modèles stockés avec `queryString`, tester un rendu de chacun sur copie, prévenir les administrateurs. |
| minor m-1 | Terme « jeu de données » / `LIGNES` encore absent des glossaires de Patient et couverture, Stock, Dossier médical, Planification, Séance (présent : Documents, GMAO). | `glossaries/documents.md:34` ; grep : 2 fichiers sur 13 | Ajouter le terme (statut `proposed`) dans les 5 glossaires. |
| minor m-2 | Les ADR 0204 et 0205 posent une revue « <= 2026-12-15 » ; 95 ADR et 17 fichiers Java citent cette date (KB métiers/DPO). Ce n'est pas une garde vivante, mais c'est une échéance de gouvernance en commentaire. | `adr/0204-*.md:2,22` ; `GererLesModeles.java:19` | Tenir la séance unique de revue des KB, puis remplacer la date par le statut confirmé. |
| minor m-3 | `CacheConfig` de platform porte le cache de valorisation du Pilotage (propriété de contexte) : dernier reste de M3. | `CacheConfig.java:26,60` | Déplacer l'enregistrement vers pilotage-direction, ou le consigner dans l'ADR 0203. |
| minor m-4 | Le build complet du 2026-11-16 (CLOT3) montre 14 échecs hors gardes, tous liés à la date du jour (périodes « jusqu'à aujourd'hui », scans juste après minuit) ou connus (`InventairePl7`). Tests dépendants de l'horloge, non déterministes. | `clot3-realisation.md` §Vérification | Figer l'horloge dans ces tests ; confirmer la liste des échecs par un gate de jour. |
| minor m-5 | Gardes `Table*Test` encore en platform, liées par `platform -> contexts-*`. | `platform/src/test/.../architecture/` | Acceptable ; à déplacer à l'événement « fin de la reprise ». |
| info | `RelaiDesInventairesVersWebSocket` dans un paquet `infrastructure/stock` : renommer en `infrastructure/websocket` pour lisibilité. | chemin ci-dessus | Optionnel. |

## 5. Progression
Platform est réduite au noyau : identité (auth, MFA, sécurité), licence, audit, supervision DB, transport STOMP, façade ADR 0161, outil de reprise, Flyway V1-V59, 10 ACL. 13 contextes extraits. Plus aucun SQL de table de contexte dans platform, aucun SQL embarqué dans les gabarits (ADR 0202), plus d'échéance datée dans les gardes (CLOT2, CLOT3). Dossiers vides supprimés. Les deux décisions déléguées (ADR 0204 et ADR 0205) sont explicites.

## 6. Liste de décommissionnement (proposition, rien supprimé)
1. **ACL (10)** : `organisation/{Creneaux,Medecins,Salles,Referentiels}`, `patientcouverture/{Transporteurs,Forfaits,Payeurs,Referentiels}`, `reprise/AppariementParLesContextes`, `gmao/GenerateursDeRepriseGmao`. Condition de retrait : retrait de la façade ADR 0161 et événement « fin de la reprise de production » (ADR 0204). Sans date.
2. **Tables à archiver ou supprimer (accord écrit requis, aucun DROP fait)** : `user_center_assignment`, `gmao_alertes_maintenance`, `gmao_historique_maintenance`, `stock_parametres`. Traces encore présentes dans `V1__baseline_schema.sql`, `V5__gmao_tables.sql`, `V45__stock_sortie_pour_seances.sql`, `comptabilite-schema.sql` et `stock-schema.sql` (H2 des contextes) ; `TableDuStockTest.java`. Un V60 de retrait reste à écrire après accord ; régénérer ensuite `infra/dataset/*.sql`.
3. **Outil de reprise** : retrait entier (service, model, port, persistance `migration_*`, importer, `acl/reprise`, `acl/gmao`) à l'événement ADR 0204.
4. **Relais STOMP** : conservés (ADR 0203) ; réexamen seulement si un port d'émission générique est créé.
5. **Modèles de document stockés avec `queryString`** : à lister avant déploiement (M-1).

## Top 3 actions
1. Contrôle de production DOC5 : modèles avec `queryString`, rendu sur copie, communication aux administrateurs (M-1).
2. Séance unique de confirmation des KB métiers/DPO (ADR 0204, 95 ADR) ; puis accord écrit pour la liste de décommissionnement n° 2.
3. Glossaires : ajouter « jeu de données » aux 5 glossaires manquants (m-1) ; déplacer le cache de valorisation vers pilotage-direction (m-3) ; figer l'horloge des 14 tests datés (m-4).
