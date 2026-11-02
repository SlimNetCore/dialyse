---
status: accepted
date: 2026-11-02
decision-makers: [utilisateur (décision du 2026-11-02, décision déléguée à l'assistant pour l'acceptation formelle), architecte]
context-scope: pilotage-direction
related: ADR 0024 (refactoring sur place), 0066, 0002, 0072, 0073 et 0074 (même démarche pour le dossier médical, Patient et couverture et la Séance) ; `.ddd/verify/verify-pilotage-direction.md` ; `backend/contexts/pilotage-direction/GEL.md`
---

# 0075 - Pilotage direction : gel du module parallèle et reprise sur place

## Contexte
La vérification indépendante (`verify-pilotage-direction.md`, verdict fail, 3 blockers) a établi que `backend/contexts/pilotage-direction` n'a jamais été branché (`platform/pom.xml` ne le référence pas) : 100 % du trafic passe par le legacy `platform/application/direction` (19 fichiers, `/api/v1/direction/*`, tables `direction_snapshot` et `direction_alert_history`). Le module : crée quatre tables parallèles sans migration Flyway (`tableaux_de_bord`, `instantanes_mensuels`, `alertes_direction`, `exports_synthese`), contraire à « mêmes tables, mêmes URL » ; n'a aucun `@PreAuthorize`, aucune garde de centre (`centreId` du client), aucun masquage ; ne calcule rien (`TableauDeBord` = chaîne opaque) ; n'a aucune caractérisation ni parité.

Audit de production (demande A, 2026-11-02) : depuis `2978e639^`, aucun fichier legacy de production de la Direction n'a été modifié (diff vide sur `application/direction`, les deux contrôleurs, les tests Direction). Aucune table ni colonne legacy ajoutée ; le `schema.sql` H2 de `platform` ne contient aucune table parallèle ; `covered.json` : aucune entrée Direction ajoutée sans golden, rien à retirer. Preuve : `backend/scripts/pilotage-direction-pg16-rejeu.sh` (PostgreSQL 16 jetable : V1..V52, colonnes utilisées par le legacy conformes, quatre tables parallèles absentes).

## Décision
1. **Geler le module** : retiré du réacteur (`backend/pom.xml`) et du `Dockerfile` ; dossier, ADR 0066, fiches, tactique et glossaire conservés (`GEL.md`).
2. **Tables et URL du LEGACY conservées** : `/api/v1/direction/*`, `direction_snapshot`, `direction_alert_history`.
3. **Règle R2 des alertes** (une alerte par centre et par jour, tous types confondus) : non tranchée ; à décider à la reprise sur la base du comportement caractérisé de `DirectionAlertHistoryService` (question consignée dans la fiche PD3).
4. **Contrat client `/api/v1/pilotage/*`** : retiré de la documentation client et de l'annexe de traçabilité ; SDK frontend `pilotage-api.service.ts` (sans consommateur) et son spec supprimés.
5. **Ordre de reprise** : filet de caractérisation du legacy Direction d'abord (manques inventoriés dans `GEL.md`), puis PD1 à PD5 sur place, avec rôle DIRECTION, garde de centre de la session, masquage `AnonymityPolicy`, fuseau du centre.
6. **Désactiver** `LectureSeuleDuPilotageTest` (motif dans `@Disabled`) ; exclure le module du balayage de `ReglesDeGestionDocumentationTest` ; la règle ArchUnit `FrontieresDesContextesTest` (vide) est conservée.
7. **Statuts réels** : PD1 à PD5 `planned`.
8. L'ADR 0066 est **partiellement remplacée** : le contexte unique et le refactoring sur place restent valides ; le module `contexts/pilotage-direction` tel que codé (tables parallèles, URL `/api/v1/pilotage`, aucune sécurité) est abandonné.

## Conséquences
- Plus de risque de câblage d'un module sans sécurité ; le travail gelé reste matière de réflexion.
- Aucune route `/api/v1/pilotage/*` n'a jamais été exposée en production : rien à retirer côté client réel.
