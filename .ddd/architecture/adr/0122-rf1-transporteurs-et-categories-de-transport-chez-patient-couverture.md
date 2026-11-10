---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [architecte (mise en oeuvre de l'ADR 0121, décision 4)]
context-scope: patient-couverture (propriétaire), platform (administration générique des référentiels, ACL), organisation (lectures `/referentials` retirées)
related: ADR 0121 (propriété), 0024 et 0075 (refactoring sur place), 0079 (`patientcouverture.v1`), 0070 (admin générique) ; fiche `.ddd/migration/slices/patient-couverture-rf1-transporteurs.md` ; known-bugs KB-RF1-01 à 04
---

# 0122 - RF1 : transporteurs et catégories de transport chez Patient & couverture

Refactoring SUR PLACE : mêmes tables (`transporteur`, `categorie_transport`), mêmes URLs, mêmes rôles, mêmes codes d'erreur, même JSON ; ni façade, ni flag, ni projection, ni événement. Parité : les 27 goldens de caractérisation HTTP écrits sur le legacy AVANT tout changement de production (16 tests) passent sans une ligne modifiée ; aucun golden `_nouveau_code`.

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Domaine pur `Transporteur` (nom obligatoire <= 255, téléphone facultatif <= 50 au format `[+0-9 ().-/]{6,}`, clé naturelle = nom sans casse ni espaces répétés, unicité par centre), port `CarnetDeTransporteurs`, cas d'usage `GererLesTransporteurs` (créer, modifier, supprimer, lister, importer tout-ou-rien), `ConsulterLesTransports` | règles auparavant dans le SQL dynamique et le validateur générique ; messages et codes repris à la lettre |
| 2 | Langage publié `patientcouverture.v1` : `Transporteurs` (lister, nom, existe), `CategoriesDeTransport` (lister), `GestionDesTransporteurs` + `RefusDeTransporteur` (motifs SAISIE_INVALIDE, DEJA_EXISTANT, INTROUVABLE, UTILISE) | lecteurs hors PC sans SQL sur ses tables ; l'administration générique parle au contexte par un port publié |
| 3 | Une seule classe JDBC `TransporteursJdbc` pour le carnet, la page d'administration et la lecture publiée ; `CategoriesDeTransportJdbc` en lecture seule. Contrat de tests UNIQUE passé par le double en mémoire, H2 et PostgreSQL 16 (11 tests, rejeu `backend/scripts/patient-couverture-rf1-pg16-rejeu.sh`, vert) | le fake et l'adapter réel ne peuvent pas diverger |
| 4 | L'administration générique GARDE la lecture du fichier (en-têtes et alias, bornes 5000 lignes, 500 anomalies, compte rendu, modèles CSV/Excel) et DÉLÈGUE les écritures, la liste, la suppression et les lignes importées au port sortant `ReferentialAdminDelegate`, implémenté par l'ACL `TransporteursParPatientCouverture` (traduit les refus du contexte en `ReferentialValidationException` / `BusinessException` : 400/404/409 inchangés). La transaction reste portée par le service applicatif de la plateforme (`UniteDeTravail` de PC la rejoint) : import tout-ou-rien conservé | seul le propriétaire écrit ses tables ; pas de duplication du lecteur de fichiers pour 8 autres référentiels |
| 5 | Lectures `/api/v1/referentials/{transporteurs,categories-transport}` (`centerId` obligatoire) et `/api/v1/organisation/referentiels/...` (`centreId` facultatif) servies par `TransportsControleur` de PC ; code Organisation retiré (contrôleurs, cas d'usage, port, JDBC, test) ; mêmes caches `ref.transporteurs` / `ref.categoriesTransport` (clé = identifiant du centre) | garde de centre exercée AVANT le cache ; réponse 400 `BAD_REQUEST` d'un `centerId` illisible reproduite par un advice borné |
| 6 | Invalidation de `ref.transporteurs` APRÈS commit à chaque écriture (`InvalidationDesListesDeTransport`, clé exacte du centre), `ref.categoriesTransport` jamais invalidé (aucun écrivain) | comportement identique (KB-RF1-02) |
| 7 | La reprise de patients (`PatientMigrator`, via `ReferentialAdminRepositoryPort.findAllForMatching`) retrouve les transporteurs par `Transporteurs.lister` ; `ReferentialAdminTables` n'a plus d'entrée transporteur (`of` échoue pour un référentiel non mappé) | plus aucun SQL sur `transporteur` hors PC |
| 8 | Aucun lecteur SQL direct hors PC : garde `TableDesTransporteursTest` (liste blanche vide, effectif asserté 0, échec sur entrée morte ; détecte `FROM/JOIN/INTO/UPDATE/TABLE transporteur|categorie_transport` et `new Table("...")`) | modèle `TableDeLOrganisationTest` |

## Écarts de parité
Aucun. Les défauts du legacy sont REPRODUITS et consignés (KB-RF1-01 : 500 sur paramètres/corps illisibles ; KB-RF1-02 : `categorie_transport` sans écrivain, cache jamais invalidé ; KB-RF1-03 : compte d'usage de tous les centres ; KB-RF1-04 : comparaison au premier homonyme). Aucune décision « corriger » n'est prise sans accord de l'utilisateur.

## Résiduels nominatifs
| ID | Résiduel | Raison / retrait |
|---|---|---|
| RF1-R1 | `ReferentialKind.TRANSPORTEUR` (descripteur : champs, alias d'en-têtes, modèle d'import, libellé) reste dans l'administration générique | le lecteur de fichiers et les modèles sont communs aux neuf référentiels ; retiré avec l'admin générique (butoir : fin de RF3) |
| RF1-R2 | `ReferentialAdminDomainService` conserve le chemin générique pour les autres référentiels | forfaits (RF2) et payeurs (RF3) |
| RF1-R3 | Les modules `migration`/`PatientMigrator` lisent les transporteurs par le langage publié | aucun SQL ; rien à retirer |

## Conséquences
- Fichiers de la plateforme touchés (filet RF1 vert, inscrits dans `covered.json`) : `ReferentialAdminDomainService`, `ReferentialAdminDelegate` (nouveau), `TransporteursParPatientCouverture` (nouveau), `DomainServiceConfig`, `ReferentialAdminJdbcAdapter`, `ReferentialAdminTables`. Aucun seam.
- Planification `ContraintesDesPatientsJdbc` lit les transporteurs via `patients` (pas la table `transporteur`) : hors périmètre RF1.
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier de la propriété (ADR 0121, décision 4) avant le 2026-12-15.
- Faut-il corriger KB-RF1-01 (500 -> 400) et KB-RF1-03 (compte d'usage borné au centre) ? Comportement actuel conservé.
- Les doublons historiques de `lower(trim(nom))` par centre existent-ils en production ? (KB-RF1-04.)
