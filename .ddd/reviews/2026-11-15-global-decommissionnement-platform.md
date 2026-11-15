---
status: proposed
date: 2026-11-15
scope: décommissionnement de backend/platform (ADR 0162-0202), HEAD 69cb5ecb
method: lecture seule (Grep/Read, pas de test ni build) ; pas de re-vérification du "platform sans SQL de contexte" déjà faite par la coordination
---

# Revue globale de jalon : décommissionnement de platform

Verdict : **pass-with-actions**. Aucun blocker. Aucun SQL de table de contexte dans platform (grep des FROM/JOIN/INTO/UPDATE : seules `app_*`, `auth_refresh_token`, `audit_log`, `license`, `migration_*`, `pg_stat_*`). Aucun cycle Maven : aucun pom de contexte ne dépend de `platform` (grep `<artifactId>platform`).

## Inventaire de platform (backend/platform/src/main/java/com/hemodialyse/backend)
| Paquet | Justification | État |
|---|---|---|
| application/{auth,auth/mfa,audit,license,direction,supervision,setup,erreur}, security, security/license | identité, licence, audit, supervision DB | noyau annoncé |
| infrastructure/websocket (8 fichiers) | transport STOMP | noyau, mais contient du relais métier (M3) |
| infrastructure/stock/RelaiDesInventairesVersWebSocket.java | relais de `InventaireModifie` vers STOMP | résiduel |
| application/referential, infrastructure/referential/admin, acl/{organisation,patientcouverture} (8 fichiers) | façade ADR 0161 / 0192 | noyau |
| infrastructure/migration/{service,model,port}, persistence/adapter/migration, importer, acl/reprise, acl/gmao | outil de reprise | noyau, volumineux (M1) |
| db/migration V1-V59, schema.sql (219 l., 15 tables), seed.sql (89 l.) | Flyway et H2 d'identité | noyau, DDL de contextes (M2) |
| infrastructure/acl/{absences,dossiermedical,seance,pilotagedirection} | dossiers vides | à supprimer (m1) |

## Constats
| Sév. | Constat | Preuve | Action |
|---|---|---|---|
| major M1 | Les migrators de reprise (~1 900 l.) contiennent des règles métier de patient/couverture/historique : `HistoryRules` 325 l., `PatientMigrator` 362 l., `MigrationDomainService` 250 l., `LegacyValueParser` 217 l. La reprise n'écrit plus de table de contexte (ADR 0173), mais ces règles dupliquent le langage de Patient et couverture. | `platform/.../infrastructure/migration/service/*` | ADR explicite : noyau de reprise assumé (outil jetable, retrait daté) ou extraction vers `contexts/reprise`. Poser une date de retrait de l'outil. |
| major M2 | Flyway centralisé : 57 migrations (V1-V59) dont les DDL gmao, stock, facturation, comptabilité, absences, planification (ex. `V5__gmao_tables.sql`, `V13__absences_patients.sql`, `V32__comptabilite_stock_journaux.sql`). L'ADR 0014 prévoit un schéma par contexte. Aucun ADR ne valide la centralisation. | `platform/src/main/resources/db/migration`, ADR 0014, ADR 0184 (parle de Flyway sans le trancher) | Écrire l'ADR : Flyway reste en platform tant qu'il y a une seule base ; interdire par une garde tout nouveau V* hors plateforme. |
| major M3 | Relais métier dans le transport STOMP : `StockEvenementsListener` vide le cache `stock.valorisation.articles` (propriété du Pilotage) en plus de relayer. `WebSocketDirectionPublisher` implémente un port de Pilotage. Les deux sont dans le noyau "transport". | `infrastructure/websocket/StockEvenementsListener.java:19-40`, `WebSocketDirectionPublisher.java:15` | Déplacer l'invalidation du cache vers Pilotage ou Stock. Garder en platform seulement `DiffusionEnTempsReelStomp` et les publishers génériques. Trancher `RelaiDesInventairesVersWebSocket` par le même ADR. |
| major M4 | Le context-map est périmé : il dit "6 extraits" et classe Patient, Séance, Dossier médical, Organisation, Direction, Documents, Notifications "restant". Tous sont extraits. Dernière modification 2026-11-12. | `.ddd/strategic/context-map.md:136-146` | Régénérer le tableau d'extraction et la frontière. Y ajouter les 10 ACL de platform, les relations R-DOC5 (Documents reçoit des jeux de données des contextes) et la sortie du moteur de rendu. |
| major M5 | Risque production DOC5 : tout téléversement contenant `<queryString>` est refusé, y compris le réimport d'un modèle exporté avant DOC5 (les défauts embarqués n'en ont plus, mais les exports et copies existants en ont). `QUERY_MODIFIED` est devenu une garde permanente. Les modèles déjà stockés avec requête restent en base. La requête est ignorée si `LIGNES` est fourni, mais les noms de champs doivent coïncider avec les alias SQL d'origine. | `contexts/documents/.../publishedlanguage/JrxmlSecurityValidator.java:294-301`, `MoteurDeRenduJasper.java:57,204` ; KB-DOC5A..J-1 | Avant mise en production : compter les modèles stockés avec `queryString` et tester un rendu de chacun sur copie de la base. Prévenir les administrateurs que le réimport d'un export ancien est refusé. |
| minor m1 | Dossiers vides dans platform : `infrastructure/acl/absences`, `dossiermedical`, `seance`, `pilotagedirection`. | `ls infrastructure/acl/*` | Supprimer. |
| minor m2 | Des tests d'architecture et de contexte non-HTTP restent dans `platform/src/test` (gardes `TableDe*Test`, `FrontieresDesContextesTest`). Leurs listes blanches citent des fichiers disparus, mais seulement comme verrous négatifs. | `TableDeLOrganisationTest.java:163,165` ; `TableDesAnalysesTest.java:150` | Acceptable comme garde centrale. Déplacer les tests propres à un contexte vers ce contexte, avec un plan. |
| minor m3 | Gardes à butoir encore actives : `TableDeLOrganisationTest` définit `LECTURE_DIRECTE` avec butoir 2027-03-31, mais `LECTEURS_CONNUS` est vide (0 lecteur, ADR 0174). `ButoirsDesGardesDeTables` a un butoir par date réelle (fait échouer le build après la date). Les autres butoirs 2027 sont des commentaires "soldé, non reporté". | `TableDeLOrganisationTest.java:38-39,149-155` ; `ButoirsDesGardesDeTables.java` | Supprimer la constante et les assertions mortes. Le grep des dates 2027 dans `src/test` ne montre aucun butoir vivant. Les "revue 2026-12-15" de `TableDuPatientCouvertureTest.java:175-177` et `TableDesEquipementsDeLaGmaoTest` tombent avec le jalon. |
| minor m4 | Les 10 ACL conservées (ADR 0192) sont justifiées par le cycle Maven réel (`platform -> contexts-*` et un contexte ne peut pas dépendre de platform). Elles n'ont aucune date de retrait propre : "retirés avec la façade et la reprise". | ADR 0192 §Décision "conservés" ; `platform/pom.xml` (13 contextes) | Fixer la condition de retrait : façade ADR 0161 retirée ou outil de reprise retiré. Inscrire la date dans l'ADR. |
| minor m5 | Dérive de nommage : le glossaire Documents (37 lignes) ne contient ni "jeu de données" ni `LIGNES`, alors que ce langage est publié par 7 contextes depuis DOC5a-j. Seul le glossaire Séance contient "jeu de donn". | `.ddd/strategic/glossaries/documents.md` ; `glossaries/seance.md` | Ajouter le terme (statut `proposed`) à Documents, Patient et couverture, Stock, GMAO, Dossier médical, Planification. |
| minor m6 | Dépendances inverses à surveiller : `organisation` dépend de `stock` et `gmao` (pom), alors que Organisation est un référentiel amont. Le pom les limite aux packages publiés (règle ArchUnit). | `contexts/organisation/pom.xml:83-92` | À noter dans le context-map (Conformist ou Customer-Supplier) et à justifier par un ADR court. |
| minor m7 | Le référentiel RG-DOC-008 conserve l'historique DOC5a-j en narration ("11 sur 14", "10 sur 14", …). L'état final n'est que dans le tableau de la ligne 35 et le texte DOC5j. Pas de contradiction. | `docs/reference/regles-de-gestion/14-referentiels-documents-reprise.md:167-190` | Résumer en un seul état final ("0 sur 14, aucun SQL embarqué, ADR 0202"). |
| info | ADR 0162-0202 : 41 fichiers, numérotation continue, aucun doublon, aucun trou, tous au statut accepted. Aucune référence à un ADR au-delà de 0202. | `.ddd/architecture/adr/` | Aucune. |
| info | `covered.json` : 108 chemins, tous existent, `seamAllowed` vide. | `.ddd/safety-net/covered.json` | Aucune. |
| info | Logs `*-full.log` à la racine de `backend/` : ignorés par git (`.gitignore:13`). | `backend/doc5j-full.log` | Nettoyer au disque. |
| info | Tables mortes `user_center_assignment`, `gmao_alertes_maintenance`, `gmao_historique_maintenance`, `stock_parametres` : retirées de H2 et du seed. Aucun DROP en production. | ADR 0184 | Décision de l'utilisateur (voir liste ci-dessous). |
| info | Le moteur de rendu "centre sans modèle" ne faisait plus d'`avecSourceDeDonnees` (régression DOC5f) : corrigé, couvert par les tests DOC5e-f. | `MoteurDeRenduJasper.java:67-69` ; `doc5f-realisation.md:34-37` | Aucune. |

## KB "à confirmer <= 2026-12-15" (consolidée, extraite des rapports de réalisation)
- Documents (DOC5) : KB-DOC5A-1, B-1, C-1, D-1, E-1, F-1, G-1, H-1, I-1, J-1 (`QUERY_MODIFIED`, un seul sujet). KB-DOC5B-2 et KB-DOC5E-2 (collation : tri par caractères / points de code contre collation de la base de production, à confirmer avec l'exploitation). KB-DOC5D-2, KB-DOC5H-2, KB-DOC5I-2 (identifiant illisible : 400 ou 500 historique reproduit). KB-DOC5H-4 (UUID sans tirets refusé par la voie par modèle). KB-DOC5E-3, KB-DOC5F-2/3, KB-DOC5G-2/3, KB-DOC5H-3, KB-DOC5I-3, KB-DOC5J-2/3 (appel direct sans `LIGNES` : page blanche, aucun appelant de production).
- Reprise : KB-S6a-3 (colonnes de place), KB-S6a-6 (refus SUPERADMIN), KB-S6a-8, KB-S6a-9, KB-RP3B-1, KB-RP3B-2.
- Identité et licence : KB-IDN-1, KB-IDN-2, KB-LIC-3, KB-LIC-4.
- Séance, patient et planification : KB-SEA-61, KB-SEA-64 (lectures par lot non bornées au centre) ; tri sans identifiant de `poidsSecCibleKg` (lec-pln-gmao).
- 97 ADR citent la date 2026-12-15 (0082-0202, avec des trous). Prévoir une séance unique de revue avant cette date, un mois après la livraison.

## Liste de décommissionnement à valider par l'utilisateur
1. ACL conservées (10) : `organisation/{Creneaux,Medecins,Salles,Referentiels}`, `patientcouverture/{Transporteurs,Forfaits,Payeurs,Referentiels}`, `reprise/AppariementParLesContextes`, `gmao/GenerateursDeRepriseGmao`. Retrait avec la façade ADR 0161 et l'outil de reprise. Valider la date.
2. Tables à archiver ou supprimer (accord écrit requis, aucun DROP fait) : `user_center_assignment`, `gmao_alertes_maintenance`, `gmao_historique_maintenance`, `stock_parametres` (V60 hypothétique). Fichiers générés `infra/dataset/*.sql` à régénérer.
3. Flags et gardes : butoir 2027-03-31 de `TableDeLOrganisationTest` (mort) ; revues datées 2026-12-15 ; modèles de document stockés avec `queryString` (M5) ; `RelaiDesInventairesVersWebSocket` et `StockEvenementsListener` ; dossiers `acl/*` vides.
4. Outil de reprise : décider conserver ou extraire (M1), avec date de retrait.

## Top 3 actions
1. ADR de tranchage (reprise M1, Flyway M2, STOMP M3) ; fixer les dates de retrait des 10 ACL et de l'outil de reprise.
2. Avant le déploiement : contrôle production DOC5 (modèles avec `queryString`, collation, UUID sans tirets, réimport des exports refusé).
3. Mettre à jour le context-map et les glossaires (M4, m5, m6) ; séance unique de confirmation des KB avant le 2026-12-15.
