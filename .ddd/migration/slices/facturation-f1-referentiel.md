---
status: approved
date: 2026-10-19
slice: facturation-f1-referentiel
order: 1 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md (F10, § TypeTVA, Q1), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-001 à 003, 010, 011, RG-CPT-006), ADR 0019, ADR 0024, .ddd/safety-net/known-bugs.md (KB-CPT-16 à 21)]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URLs, mêmes DTO JSON ; aucune façade, aucun routage, aucune
> projection, aucune migration de données. Retour arrière = revenir au commit précédent.

# F1 — Référentiel de facturation (types de TVA, paramètres du centre)

**Comportement observable** : l'administrateur gère les types de TVA de son centre (RG-FAC-001 à 003) et ses paramètres
de facturation : modèle de numérotation et regroupement multi-forfait (RG-FAC-010, 011).

| Route                                                                 | Rôles (legacy)                   | Legacy                                                                                                 |
|-----------------------------------------------------------------------|----------------------------------|--------------------------------------------------------------------------------------------------------|
| `GET /api/v1/tva-types` (paginé, cache `tva-types-list`)              | `ADMIN`, `MEDECIN`, `SECRETAIRE` | `TvaTypesRestController` → `TypeTvaApplicationService` → `TypeTvaDomainService` → `TypeTvaJdbcAdapter` |
| `POST /api/v1/tva-types`, `PUT /{id}`, `DELETE /{id}` (désactivation) | `ADMIN`                          | idem                                                                                                   |
| `GET /api/v1/facturation/settings`                                    | `ADMIN`, `MEDECIN`, `SECRETAIRE` | `FacturationRestController` → `FacturationUseCase` → `FacturationSettingsJdbcAdapter`                  |
| `PUT /api/v1/facturation/settings`                                    | `ADMIN`                          | idem                                                                                                   |

Tables : `tva_types` (**partagée** : aussi écrite par `POST /api/v1/comptabilite/regles-tva`, lue par la Facturation à
la
validation, par les Absences et par la Comptabilité via le langage publié `ParametrageFiscal`), `facturation_settings`.

## Décision Q1 (2026-10-19, utilisateur) — ADR 0030

**La Comptabilité reste propriétaire de `tva_types`** (option B). `/api/v1/tva-types` devient une façade (mêmes URL et
JSON) qui délègue au contexte `comptabilite` ; une **seule règle de validation**, union des contrôles : taux 0 à 100 %,
fin égale ou postérieure au début, pas de chevauchement d'une règle active du même centre et du même type. Options
écartées : Facturation propriétaire (A), statu quo (C).

Historique de la question :
**Propriétaire de `tva_types`.** Aujourd'hui deux écrans écrivent la même table avec des règles différentes : la
Comptabilité refuse les dates inversées et les chevauchements (KB-CPT-16) mais pas un taux > 100 (KB-CPT-17) ; la
Facturation borne le taux à 100 % et exige une fin strictement postérieure au début, sans contrôle de chevauchement (à
caractériser). Options :

- **A (recommandée, conforme à la stratégie)** : la Facturation devient seule propriétaire ; `/comptabilite/regles-tva`
  devient une façade qui délègue au contexte Facturation (mêmes URL et JSON), et `ParametrageFiscal` lit le langage
  publié de la Facturation. Une seule règle de validation, à arbitrer (union des contrôles).
- **B** : la Comptabilité reste propriétaire (C2 déjà livrée) ; `/tva-types` devient la façade.
- **C** : statu quo (deux écritures) : reproduit l'incohérence, déconseillé.

## Filet de caractérisation à écrire d'abord (sur le legacy)

1. `/tva-types` : création (bornes 0 et 100, > 100, négatif, décimales), dates (fin = début, fin < début, fin absente),
   chevauchements (non contrôlés ?), désactivation (`DELETE` logique, relecture), modification, pagination, rôles,
   isolement par centre, éviction du cache `tva-types-list` (et effet sur `/comptabilite/regles-tva`, KB-CPT-20).
2. Résolution du taux à une date (règles successives, bornes inclusives, règle désactivée, autre type, absence = 0 %) :
   lue par la simulation de facturation, les Absences et la Comptabilité.
3. `/facturation/settings` : lecture d'un centre sans paramètres (valeurs par défaut `FAC-{YEAR}-{SEQ}` et regroupement
   activé, **écrites en base par la lecture**), modification, modèle invalide ou vide, jetons inconnus, rôles, centres.
4. **PostgreSQL 16** : l'adaptateur des paramètres utilise `MERGE INTO … KEY (center_id)` (syntaxe H2) ; vérifier sur
   le schéma Flyway pur si `GET` (premier accès) et `PUT /settings` échouent en production, comme KB-CPT-21. Constat à
   consigner en KB-FAC.

## Cible

- **Types de TVA → contexte `comptabilite`** (ADR 0030) : `RegleTva` reçoit la modification et la désactivation, et la
  règle de validation unique ; nouveau contrôleur `TypesTvaControleur` (`/tva-types`, même JSON que le legacy) dans le
  contexte `comptabilite` ; `ReglesTvaControleur` applique la même règle.
- **Paramètres → nouveau module `backend/contexts/facturation`** : domaine `ParametresFacturation`,
  `ModeleNumeroFacture` (jetons RG-FAC-010) ; application `ConfigurerLaFacturation` ; interfaces
  `ParametresFacturationControleur` (`/facturation/settings` ; les autres routes de `/facturation` restent au legacy
  jusqu'à F2/F3/F5) ; lecture du taux par le langage publié de la Comptabilité.
- Recette : § 3 de la feuille de route, dont **mise à jour du référentiel (RG-FAC-001 à 003, 010, 011, RG-CPT-006) et
  régénération de la documentation fonctionnelle**.

## Indicateurs de fin

Filet F1 vert sur le legacy puis sur le nouveau code ; écarts décidés tracés (KB-FAC, goldens `_nouveau_code`) ;
`check-arch` 0 ; ArchUnit du module ; `ReglesDeGestionDocumentationTest` vert ; documentation régénérée.

## État au 2026-10-19 : `characterized`

- Filet : 24 scénarios, 76 goldens, verts et stables sur le legacy (`.ddd/safety-net/facturation-f1-referentiel.md`).
- Constats : KB-FAC-01 à 12 (`known-bugs.md`). **Décisions de l'utilisateur** : corriger KB-FAC-01 (isolement par
  centre),
  02 (PUT inconnu = 404), 03 (modification sans réactivation), 04/05 (règle unique, ADR 0030), 07 (PostgreSQL), 08
  (lecture
  sans écriture), 09 (modèle avec jeton de séquence) ; reproduire 06, 10, 11, 12.
- Prochaine étape : `/ddd-slice facturation-f1-referentiel` (construction), puis référentiel des règles et documentation
  fonctionnelle à jour à la livraison.

## État au 2026-10-19 : `built`

**Types de TVA → contexte `comptabilite`** (ADR 0030, Comptabilité propriétaire de `tva_types`) :

- domaine : `RegleTva` reçoit son identité (`RegleTvaId`), son libellé, la **modification** (garde l'état, KB-FAC-03) et
  la **désactivation** ; règle unique de saisie (`TauxDeTva.saisi` 0 à 100 %, fin ≥ début, pas de chevauchement d'une
  autre règle active du même type ; une règle ne se compare pas à elle-même) ; les règles relues en base ne sont pas
  revalidées (un taux > 100 ancien reste lisible) ;
- application : `GererLesTypesDeTva` (lister, créer, modifier → vide si inconnu dans le centre, désactiver idempotent),
  port de lecture `LectureDesTypesDeTva` (page triée début décroissant puis libellé, audit) ;
- adaptateurs : `RegleTvaRepositoryJdbc` (`duCentre(id)`, `ajouter(auteur)`, `remplacer` sans toucher `actif` ni
  `created_by`, `desactiver`), `TypesDeTvaJdbc` ;
- interface : `TypesTvaControleur` (`/api/v1/tva-types`, même JSON que le legacy, garde de centre `SessionCourante`,
  404 sur modification inconnue) ; `ReglesTvaControleur` borne le taux à 100 (`@DecimalMax`) et n'évince plus de cache.

**Paramètres → nouveau module `backend/contexts/facturation`** (hexagonal, ArchUnit) : `ModeleDeNumerotation`
(jetons RG-FAC-010, séquence obligatoire, 120 caractères), `ParametresDeFacturation`, `ConfigurerLaFacturation`
(lecture sans écriture, horloge injectée), `ParametresDeFacturationJdbc` (UPDATE puis INSERT, portable),
`SessionLegacy`, `ParametresFacturationControleur` (`/api/v1/facturation/settings`), composition
`FacturationContexteConfiguration` (beans préfixés, pas de bean `Clock` concurrent).

**Retiré du legacy** : `TvaTypesRestController`, `TvaTypeRequest`, `TypeTvaApplicationService`, `TypeTvaDomainService`,
`TypeTvaUseCase`, `FacturationSettingsUpdateRequest`, les routes `/settings` de `FacturationRestController`, les caches
`tva-types-list` et `tva-active`. **Reste** (simulation et validation, jusqu'à F2/F3) : `TypeTVA`,
`TypeTvaRepositoryPort`
/ `TypeTvaJdbcAdapter.findActiveAt`, `FacturationSettingsRepositoryPort` / `FacturationSettingsJdbcAdapter` — ce dernier
corrigé de la même façon (lecture sans écriture, écriture portable) car la validation le lit aussi.

**Parité** : filet F1 rejoué sur le nouveau code ; écarts décidés verrouillés par les goldens `_nouveau_code`
(KB-FAC-01 à 05, 08, 09) ; C2 : taux > 100 refusé par `/regles-tva` (`taux_*_400_nouveau_code`), liste des types plus
en cache (`la_liste_des_types_de_tva_n_est_plus_en_cache_…_nouveau_code`). Tests du contexte `comptabilite` (règle,
cas d'usage, contrat mémoire/H2, lecture JDBC) et du module `facturation` (modèle, cas d'usage, contrat mémoire/H2,
ArchUnit) verts. Contrat PostgreSQL 16 prêt (`ParametresPostgresContractIT`, `-Dfacturation.pg16.url`) : **rejeu PG16
à faire sur le cluster jetable** pour clore KB-FAC-07.

**Référentiel** : RG-FAC-001 à 003, 010, 011 et RG-CPT-006 réécrites ; annexe 16 (`TypesTvaControleur`,
`ParametresFacturationControleur`) ; documentation fonctionnelle régénérée.

**Limites connues** : deux saisies simultanées de types du même centre ne sont pas sérialisées (comme C2) ; deux
premières écritures simultanées des paramètres d'un centre peuvent se heurter sur la clé (500), sans perte de données.

**KB-FAC-07 clos (2026-10-19)** : rejeu sur PostgreSQL 16 jetable (`backend/scripts/facturation-pg16-rejeu.sh`, schéma
Flyway V1..Vn) : l'ancien `MERGE INTO … KEY (center_id)` échoue (constaté), l'écriture actuelle (UPDATE puis INSERT)
passe ; `ParametresPostgresContractIT` 5/5 et `RegleTvaPostgresContractIT` 15/15 verts (ajout, lecture par identifiant,
remplacement, désactivation).
