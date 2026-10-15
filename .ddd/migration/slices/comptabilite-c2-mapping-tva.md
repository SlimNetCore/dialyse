---
status: approved
date: 2026-10-15
slice: comptabilite-c2-mapping-tva
order: 2 (C1 à C7, voir `.ddd/migration/roadmap.md`)
sources: [.ddd/tactical/comptabilite.md, .ddd/safety-net/comptabilite-c2-mapping-tva.md, .ddd/safety-net/known-bugs.md (KB-CPT-14 à 23), ADR 0024, ADR 0025 (amendement C2)]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URLs, mêmes DTO JSON ; aucune façade, aucun routage, aucune
> projection, aucun drapeau, aucune migration de données. Retour arrière = revenir au commit précédent.

# C2 — Mapping comptable et règles de TVA

**Comportement observable** : l'administrateur (`ADMIN` seul) règle le mapping comptable de son centre (comptes et
journal de chaque opération) et ajoute des règles de TVA comptables, sous `/api/v1/comptabilite` : `GET|PUT /mapping`,
`GET|POST /regles-tva`.

## État final (2026-10-15) : `built`

`./mvnw -o test` complet **vert (BUILD SUCCESS)** : module `contexts/comptabilite` **266 tests**, `platform` **1369
tests** (4 ignorés), GMAO 241, `bc-comptabilite` 51 ; PostgreSQL 16 : 48 tests de contrat verts. **Parité : 88 tests de
caractérisation sur 88 verts contre le nouveau code** (84 d'origine, dont 13 adaptés aux écarts décidés, plus 4
nouveaux : bornes du chevauchement, ce qu'il ignore, données déjà en base, refus qui n'évince pas le cache), 26 goldens
`*_nouveau_code`. C1 (80 tests) et GMAO restent verts. `check-arch.ts` : 0 violation. Test garde des règles de gestion
vert. Frontend non modifié.

## Réalisation (`backend/contexts/comptabilite`)

| Couche               | Contenu                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| domaine              | `mapping/` : `MappingComptable` (comptes, `ComptesDuStock`, journal de chaque `OperationComptable`, valeurs par défaut, `JournalInconnu`), `ComptesDuStockSaisis`, `MappingComptableRepository` ; `tva/` : `RegleTva` (intervalle [début, fin] inclusif, chevauchement, dates inversées), `TauxDeTva`, `RegleTvaRepository`, `DatesDeValiditeInversees`, `RegleTvaEnChevauchement` ; `CompteInconnu` accepte un numéro tel que saisi                           |
| application          | `mapping/` : `ConsulterLeMapping`, `ModifierLeMapping`, `MappingDuCentreVu`, `ConsulterLeMappingDuCentre` (publié) ; `tva/` : `ListerLesReglesDeTva`, `EnregistrerUneRegleDeTva`, `ConsulterLeParametrageFiscal` (publié)                                                                                                                                                                                                                                      |
| langage publié       | `MappingComptableDuCentre` (mapping effectif), `ParametrageFiscal` (règle applicable à une date)                                                                                                                                                                                                                                                                                                                                                               |
| infrastructure       | `persistence/` : `MappingComptableJpa` + `MappingComptableRepositoryJpa` (`mapping_comptable`), `RegleTvaRepositoryJdbc` (`tva_types`, identifiant généré en Java)                                                                                                                                                                                                                                                                                             |
| interfaces           | `MappingControleur`, `ReglesTvaControleur` (`@CacheEvict("tva-types-list")` synchrone), DTO, `ReglesMetierVioleesAdvice` étendu au mapping                                                                                                                                                                                                                                                                                                                     |
| plateforme (câblage) | ACL minimale `infrastructure/acl/comptabilite/` : `MappingComptableDuContexte` (implémente `MappingComptablePort`, lu par `ComptabiliteService` et la comptabilisation du stock), `ParametrageFiscalDuContexte` (implémente `ParametrageFiscalPort`, lu par `ForfaitAbsenceJdbcAdapter`, inchangé)                                                                                                                                                             |
| tests                | domaine (`MappingComptableTest`, `OperationComptableTest`, `RegleTvaTest`), cas d'usage avec doubles en mémoire, contrats communs aux doubles et aux adaptateurs (`Mapping*ContratTest`, `RegleTva*ContratTest` sur H2) et PostgreSQL 16 (`MappingPostgresContractIT`, `RegleTvaPostgresContractIT`), alignement des modèles de persistance (`mapping_comptable`, `tva_types`), traducteurs de l'ACL, filet HTTP (`characterization/comptabilite/mappingtva/`) |

## Écarts de comportement décidés (utilisateur, 2026-10-15)

Détail : `.ddd/safety-net/known-bugs.md`, « Décisions C2 ». Goldens `*_nouveau_code`.

- **KB-CPT-21 corrigé** : l'identifiant d'une règle est généré en Java ; `POST /regles-tva` marche sur H2 et PostgreSQL
  (constat réel PG16 : l'ancien `RANDOM_UUID()` y échouait ; `RegleTvaPostgresContractIT` prouve l'`INSERT` actuel).
- **KB-CPT-16 corrigé en partie** : `POST /regles-tva` refuse en **400** une règle dont la **fin précède le début** et
  une règle qui **chevauche** une règle **active** du même centre et du même type. Définition : intervalles [début, fin]
  **inclusifs**, fin absente = ouvert, type comparé tel que saisi, règles désactivées (par la Facturation) ignorées,
  première règle chevauchée citée dans le message. Règles déjà en base non revalidées (aucune migration, contrôle de
  production n° 15). Toujours : ni modification ni suppression.
- **KB-CPT-20 corrigé** : un ajout réussi évince `tva-types-list` (`@CacheEvict` synchrone du contrôleur ; un refus
  n'évince rien). Écart bénin : un écran de Facturation ouvert garde sa liste jusqu'au rechargement ; en déploiement
  multi-nœuds le cache est local à chaque nœud.
- **Reproduits** : KB-CPT-14, 15 (sauf l'opération citée par `JOURNAL_INCONNU`, maintenant toujours la première dans
  l'ordre des opérations), 17, 18, 19, 22, 23 ; `ADMIN` seul ; `SUPERADMIN` bloqué par `ROLE_SCOPE`. **Reproduits,
  correction à la livraison suivante.** KB-CPT-17 et 19 sont à trancher avec la modélisation de la Facturation.

## Parité et goldens

Les 84 scénarios d'origine tournent contre le nouveau code ; seuls les goldens des écarts décidés changent
(`*_nouveau_code`). Goldens caducs retirés : `chevauchement_2_200`, `chevauchement_3_meme_periode_200`,
`fin_avant_debut_acceptee_200`, `ajouts_successifs_sans_fermeture_etat_base`, `libelle_compose_etat_base`,
`taux_bornes_etat_base`, `taux_decimales_etat_base`, `dates_etat_base`, `exonere_etat_base`, `parcours_4_relecture_200`,
`parcours_6_relecture_inchangee_200`, `parcours_etat_base_final`. Aucun autre golden n'est modifié (comparaison fichier
à fichier avant et après). Les tests qui ajoutaient plusieurs règles **ouvertes** du même type par `POST` reçoivent des
fins de période (les réponses `{"saved":true}` sont inchangées) ; les lecteurs de la règle applicable (Comptabilité,
Facturation, Absences) lisent encore des données chevauchantes posées en SQL (données héritées).

## Mutation et PostgreSQL 16

- **PIT 1.30.0** (`backend/scripts/pit-c2-mapping-tva.sh`, domaine et cas d'usage de C2, 123 tests unitaires) : **102
  mutants, 100 tués (98 %)**, force des tests 98 %, aucun mutant sans couverture, couverture de lignes 209/209. Cible ≥
  70 % atteinte (le script de l'ancien code, 79 %, est remplacé).
- **PostgreSQL 16 réel** (`backend/scripts/comptabilite-pg16-rejeu.sh 55443`, cluster jetable, port 55443, jamais 5432,
  détruit en sortie) : **code de sortie 0**, 48 tests de contrat verts dont `MappingPostgresContractIT` 6 et
  `RegleTvaPostgresContractIT` 10 (ajout d'une règle ouverte sans texte, fermée, exonérée, arrondi du taux, refus de
  1000, colonnes d'audit). **Constat KB-CPT-21** : l'ancien `INSERT` (`RANDOM_UUID()`) échoue (« function random_uuid ()
  does not exist ») ; l'`INSERT` actuel (identifiant généré en Java) réussit. `19.999` devient `20.00`, `19.005` devient
  `19.01`, `1000` : « numeric field overflow » ; contraintes `uq_mapping_center` et clés primaires constatées sur le
  schéma Flyway pur. Le script `comptabilite-c2-pg16-constat.sh` est supprimé (absorbé par le rejeu et les contrats).

## Données et plan de bascule

Mêmes tables (`mapping_comptable`, `tva_types`), aucune migration : pas de plan de bascule (ADR 0024). **À valider avant
livraison** : requêtes SELECT n° 15 de `verifications-production.md` (dates inversées, chevauchements, taux > 100,
fonction `random_uuid`).

## Limites connues (reproduites ou nouvelles)

- Deux `POST /regles-tva` simultanés qui se chevauchent ne sont pas sérialisés (lecture puis insertion, sans verrou) ;
  deux premiers `PUT /mapping` simultanés : la contrainte `uq_mapping_center` fait échouer le second en 500 (KB-CPT-22).
- Les règles qui se chevauchent déjà en base restent lues comme avant (règle commencée le plus récemment) ; seul l'ajout
  est contrôlé.
- `RegleTva` ne porte pas d'identifiant et la liste n'expose ni `id` ni `actif` (comme avant).

## Reste à retirer avec C3 à C7 (conservé car encore lu)

| Conservé                                                                                                                                          | Lu par                                                                                                      | Retrait                                         |
|---------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|-------------------------------------------------|
| `MappingComptable`, `ComptesStock`, `OperationComptable` (VO legacy) et `MappingComptablePort` (lecture seule) + ACL `MappingComptableDuContexte` | `GenerateurEcritureComptable`, `GenerateurEcritureStock`, `ComptabiliteService`, `ComptabiliteStockService` | C5 (ventes, règlements), C6 (stock)             |
| `RegleTVA`, `ParametrageFiscalPort` (lecture seule) + ACL `ParametrageFiscalDuContexte`                                                           | `ForfaitAbsenceJdbcAdapter` (Absences), tests de caractérisation                                            | C7 (Absences lira la lecture publiée de la TVA) |
| `ComptabiliteRestController` (écritures, export, clôture, `/rebuild`, `/stock`)                                                                   | C3, C5, C6                                                                                                  | C3 à C6                                         |
| `ComptabiliteService`, `ComptabiliteUseCase`, `ComptabiliteApplicationService` (génération d'écritures)                                           | C3, C5                                                                                                      | C3, C5                                          |
| `JournalRepositoryPort`, `PlanComptableUseCase` et leurs ACL                                                                                      | `PiecesComptablesService`                                                                                   | C4                                              |

Retirés par C2 : `MappingComptableJpaAdapter/Entity/Repository`, `ParametrageFiscalJdbcAdapter`, `saveMappingComptable`,
`getMappingComptable`, `getReglesTV`, `saveRegleTVA` (cas d'usage legacy), les quatre routes de
`ComptabiliteRestController` et leurs DTO ; méthodes `save` de `MappingComptablePort` et `findAll`, `save` de
`ParametrageFiscalPort`. `TypeTvaJdbcAdapter`, `GET /tva-types` et `ForfaitAbsenceJdbcAdapter` : non modifiés.

## Frontend : ce qui change (aucune modification faite)

- `POST /regles-tva` répond **400** (« Requete invalide », message explicite en français) pour des dates inversées ou
  une règle qui chevauche une règle active du même type. Aucun écran n'appelle aujourd'hui `saveRegle` (l'écran ne lit
  que `getRegles`) ; à afficher quand l'écran d'ajout sera construit.
- Aucun autre changement de contrat (mêmes champs, mêmes statuts pour tout le reste).
