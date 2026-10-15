---
status: approved
date: 2026-10-15
decision-makers: [utilisateur (décisions du 2026-10-14)]
context-scope: comptabilite (slice C1, puis C2 à C7)
amends: [0024]
research: []
---

# 0025 — Module `contexts/comptabilite` : refonte sur place de C1 (référentiel comptable)

## Contexte

Le contexte Comptabilité (modèle tactique approuvé) est refait par slices C1 à C7, comme GMAO, selon l'ADR 0024 : mêmes
tables, mêmes URLs, filet de caractérisation puis livraison en une fois. C1 couvre le plan comptable, les journaux et le
compte client de chaque payeur.

## Décision

1. **Nouveau module Maven `contexts-comptabilite`** (`backend/contexts/comptabilite`), calqué sur `contexts/gmao` :
   `domain`, `application`, `infrastructure`, `interfaces/http`, `configuration`, `publishedlanguage`, ArchUnit. Les
   règles `.ddd/arch-rules.json` (génériques `backend/contexts/**`) s'y appliquent sans changement.
2. **Données** : tables `comptes_comptables`, `journaux_comptables`, `comptes_payeurs` (propriété de C1) et
   `centre_payeur` (lue en JDBC). Aucune migration.
3. **Contrôleurs partagés** : C1 retire ses six routes de `ComptabiliteParametrageRestController` (partagé avec C4) et
   de `ComptabiliteRestController` (partagé avec C2, C3, C5, C6) par une modification minimale, avec accord de
   l'utilisateur.
4. **Langage publié** : `PlanComptableEnLecture`, `JournauxDuCentre`, `ComptesClientsDesPayeurs`. Le legacy encore en
   place (`ComptabiliteService`, `PiecesComptablesService`) les lit par trois adaptateurs ACL minimes de la plateforme
   (`infrastructure/acl/comptabilite`), temporaires (retirés avec C2 à C6).
5. **Écarts décidés** (ADR 0019) : KB-CPT-01 (`actif` obligatoire : 400), KB-CPT-04 (numéros de compte en majuscules),
   KB-CPT-13a (un compte d'article est « utilisé », lu par un adaptateur JDBC de C1, sans dépendance au module des
   articles). Les autres KB-CPT sont reproduits.
6. **Événements** : aucun publié en C1 (aucun consommateur, outbox de l'ADR 0013 inchangée).

## Conséquences

- `bc-comptabilite` rétrécit (services, VO et adaptateurs de C1 retirés) mais reste jusqu'à C7 pour C2 à C6.
- Le mapping et les pièces du legacy comparent toujours les numéros de compte en casse exacte : un numéro en minuscules
  y reste refusé.
- Contrôle de production avant déploiement : numéros en minuscules et doublons après normalisation
  (`.ddd/migration/verifications-production.md`, n° 14).

## Amendement C2 (2026-10-15) : mapping comptable et règles de TVA

Même module, mêmes tables (`mapping_comptable`, `tva_types`), mêmes URLs et mêmes DTO JSON : `GET|PUT /mapping`,
`GET|POST /regles-tva`.

1. **Domaine** : `MappingComptable` (comptes et journal de chaque `OperationComptable`, `ComptesDuStock`, valeurs par
   défaut) et `RegleTva` (taux d'un type de prestation à partir d'une date, intervalle [début, fin] inclusif). Le
   mapping lit le plan (`PlanDuCentre`) et les journaux de C1 directement, dans le même module (aucune ACL entre C1 et
   C2).
2. **Langage publié** : `MappingComptableDuCentre` (mapping effectif) et `ParametrageFiscal` (règle de TVA applicable à
   une date). Le legacy encore en place les lit par deux adaptateurs ACL minimes de la plateforme
   (`MappingComptableDuContexte` pour `ComptabiliteService` et la comptabilisation du stock ;
   `ParametrageFiscalDuContexte` pour `ForfaitAbsenceJdbcAdapter`, inchangé). Les ports legacy `MappingComptablePort` et
   `ParametrageFiscalPort` n'ont plus que leur lecture ; `MappingComptable`, `ComptesStock`, `OperationComptable`
   (générateurs d'écritures, C5 et C6) et `RegleTVA` (Absences, C7) restent tant qu'ils sont lus.
3. **`tva_types`** reste une table partagée avec la Facturation : SQL direct (`RegleTvaRepositoryJdbc`),
   `TypeTvaJdbcAdapter` et `GET /tva-types` non modifiés ; le propriétaire reste à trancher avec la modélisation de la
   Facturation.
4. **Écarts décidés** (utilisateur, 2026-10-15, ADR 0019) : KB-CPT-21 corrigé (identifiant généré en Java : la route
   marche sur H2 et PostgreSQL) ; KB-CPT-16 en partie corrigé (400 sur dates inversées et sur chevauchement d'une règle
   active du même centre et du même type ; pas de modification ni de suppression) ; KB-CPT-20 corrigé (`@CacheEvict`
   synchrone de `tva-types-list` dans `ReglesTvaControleur`, comme `EquipementControleur` en GMAO). KB-CPT-14, 15, 17,
   18, 19, 22, 23 reproduits (correction à la livraison suivante).
5. **Aucune migration** ; contrôle de production en SELECT seulement (`verifications-production.md`, n° 15).
