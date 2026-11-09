---
slice: organisation-o4-lectures-publiees
status: built
date: 2026-11-09
order: 4
context: organisation
sources: [.ddd/tactical/organisation.md, ADR 0063, ADR 0094, ADR 0107, ADR 0108, .ddd/safety-net/organisation-o4-lectures-publiees.md]
---

# O4 — Lectures publiées et retrait du SQL direct sur les tables d'Organisation

Refactoring SUR PLACE (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles, mêmes codes ; ni façade, ni flag, ni projection, ni métrique, ni
événement. La preuve de parité est la suite des contextes consommateurs (goldens et tests verts inchangés).

> **Historique.** Constat du 2026-11-02 : `organisation.v1` n'avait aucun consommateur et 30 (en réalité 32) sources hors contexte lisaient
> les tables en SQL ; la slice était `planned` (« constater et consigner »). **Le 2026-11-09 l'utilisateur a demandé explicitement de la
> réaliser** : elle passe `in-progress`, par lots, comme le Pilotage (ADR 0102-0106). Décision : ADR 0107.

Reçoit aussi le calendrier du centre (`center_holiday`, `center_closure_day`) de Séance SE7a (ADR 0094, résidu R1, revue 2026-12-15, butoir
2027-03-31) : à traiter dans un lot ultérieur (O4-R5).

## Mesure d'avancement

Le critère est le nombre d'entrées de `TableDeLOrganisationTest.LECTEURS_CONNUS` (un test le fige) : **32 avant le lot A, 22 après le lot A, 18 après le lot B, 15 après le lot C, 9 après le lot D, 7 après le lot E (état final nominatif)**.

## Plan en lots (audit complet : `.ddd/safety-net/organisation-o4-lectures-publiees.md`, section « Audit O4 »)

| Lot | Besoin | Langage publié (organisation.v1) | Lecteurs | Liste blanche |
|---|---|---|---|---|
| **A** (fait 2026-11-09) | centres actifs / tous ; fuseau du centre ; société d'un centre ; sociétés et centres | `AnnuaireDesCentres` (`actifs`, `tous`, `fuseauHoraireDe`), `AnnuaireDesSocietes` (`societesActives`, `societeDuCentre`, `societe`) | 10 : Absences, Dossier médical, Séance (×2), Patient-couverture, Planification (×2), plateforme (jour du centre), Pilotage (×2) | 32 → 22 |
| **B** (fait 2026-11-09) | Stock : centres actifs (sens strict), fuseau (défaut UTC), centres du traitement de nuit, verrou de centre d'inventaire | ports `CentresFournis` et `VerrouDuCentreFourni` déclarés par Stock (cycle Maven : Organisation dépend de Stock) + traducteurs plateforme appelant `AnnuaireDesCentres` (`actifsAuSensStrict`) et `VerrouDuCentre` | 4 | 22 → 18 |
| **C** (fait 2026-11-09) | libellés de salle, de créneau, de médecin | `LibellesDeLOrganisation` (codes de salles, créneaux, noms de salles et libellés de créneaux sans borne, médecins, recherches par terme) | 3 : Séance `LecturesPublieesDesSeancesJdbc`, Patient-couverture (×2) | 18 → 15 |
| **D** (fait 2026-11-09) | séries et ressources du planning, salles, verrou du référentiel des infirmiers | `SallesEtCreneauxDuCentre` (salles, créneaux, séries), commande `DefinirLesSallesDIsolement` (l'`UPDATE salle` d'isolement), `VerrouDuCentre.reserverLeCentre` | 6 : Planification | 15 → 9 |
| **E** (fait 2026-11-09) | identité imprimée (logo, pied de page, adresse, contacts, mentions légales) ; provisionnement des modèles de documents (tous les centres) ; admin générique ÉVALUÉ, laissé | `IdentiteImprimee` (`duCentre`, `deLaSociete`) ; `AnnuaireDesCentres.tous` (déjà publié au lot A) | 2 : plateforme `DocumentIdentityProvider`, `ModeleDocumentProvisioner` (au lieu de `ReferentialAdminJdbcAdapter`, laissé : O4-R13) | 9 → 7 |
| Résiduels | authentification, comptes, garde d'accès, amorçage dev, admin générique | — (propriétaire : Identité et accès, ou la plateforme pour l'admin générique ; voir O4-R9 à O4-R13) | 7 nominatifs | 7 |

Chaque lot : langage publié + implémentation JDBC (SQL à l'identique, comparé ligne à ligne) + contrat mémoire/JDBC H2 ; traducteur mince
côté consommateur ; lecteur SQL supprimé ; entrée retirée de la liste blanche ; goldens inchangés ; ADR de lot.

## Lot A : fait (2026-11-09)

- Organisation : `publishedlanguage/AnnuaireDesCentres`, `AnnuaireDesSocietes` ; `infrastructure/persistence/AnnuaireDesCentresJdbc`,
  `AnnuaireDesSocietesJdbc` ; beans dans `OrganisationConfiguration`. Contrat `AnnuairesContrat` exécuté sur un faux en mémoire et sur H2 (9 tests
  chacun).
- Consommateurs (traducteurs minces, `infrastructure/acl/organisation/`) : Absences `CentresActifsPublies`, Planification `CentresActifsPublies` et
  `HorlogeDesCentresPublies`, Dossier médical `TousLesCentresPublies`, Séance `CentresARappelerPublies` et `JourDuCentrePublie`,
  Patient-couverture `HorlogeDesCentresPublies`, Pilotage `SocietesEtCentresPublies` et `SocieteDUnCentrePublie`. La plateforme garde le NOM
  `JourDuCentreLegacy` (contenu : plus aucun SQL) : le renommer crée un fichier hors `covered.json`, bloqué par le hook ; décision de l'utilisateur
  requise (O4-R1). `seamAllowed` est resté `[]` du début à la fin.
- Parité : SQL repris à l'identique (voir commentaires des JDBC) ; chaque consommateur garde SON défaut de fuseau (Alger ; Stock UTC au lot B) ;
  aucun `.approved` modifié ; aucun écart de comportement, donc aucun golden `_nouveau_code`, aucune archive `_ecarts-legacy`.
- Obstacle : Stock (2 lecteurs de centres) NON migré au lot A, traité au lot B (cycle Maven + écart `actif = TRUE` strict).
- Réserve : trois des lecteurs migrés n'avaient aucun test direct (Dossier médical, Séance `CentresARappeler` hors H2, Patient-couverture
  horloge) ; leur SQL est identique et le comportement est désormais fixé par `AnnuairesContrat`, mais il n'a pas été exécuté contre le lecteur
  d'origine avant migration.

## Lot B : fait (2026-11-09, ADR 0108)

- Stock : `publishedlanguage/CentresFournis`, `VerrouDuCentreFourni` ; `infrastructure/acl/centres/CentresActifsFournis`, `HorlogeDesCentresFournie` (remplacent
  `acl/legacycentres/*`, supprimés) ; `OperationsDeStockJdbc` et `InventairesJdbc` reçoivent le port en constructeur ; câblage dans `StockContexteConfiguration`.
- Organisation : `AnnuaireDesCentres.actifsAuSensStrict()` ; `publishedlanguage/VerrouDuCentre` + `VerrouDuCentreJdbc` (bean `verrouDuCentre`).
- Plateforme : `infrastructure/acl/stock/CentresDuStockParOrganisation`, `VerrouDuCentreDuStockParOrganisation` (aucun SQL). Aucun `pom.xml` modifié.
- O4-R3 (décision déléguée) : reproduit, sans uniformiser ; variante stricte nommée ; KB-ORG-09 ; contrôle §72. O4-R4 reste ouvert.
- Parité : caractérisation écrite et verte sur les lecteurs SQL AVANT migration (cas NULL, tous les centres, verrou) ; mêmes assertions après ; aucun
  `.approved` modifié, aucun golden `_nouveau_code`.
- Réserve : le contrat de `VerrouDuCentre` n'est exécuté que sur H2 (O4-R7) ; la sérialisation réelle de deux clôtures concurrentes est couverte par
  `InventaireConcurrentIntegrationTest` (plateforme).

## Lot C : fait (2026-11-09, ADR 0109)

- Organisation : `publishedlanguage/LibellesDeLOrganisation`, `infrastructure/persistence/LibellesDeLOrganisationJdbc`, bean `libellesDeLOrganisation`. Contrat
  `LibellesContrat` exécuté sur un faux en mémoire et sur H2 (8 tests chacun).
- Consommateurs : `LecturesPublieesDesSeancesJdbc` (Séance), `LectureDesMouvementsJdbc` et `LectureDesPatientsJdbc` (Patient-couverture) reçoivent le port en
  constructeur ; plus aucun SQL sur `salle`, `position_creneau`, `medecin`. Aucun `pom.xml`, aucun fichier de plateforme, aucun seam.
- Les 3 lecteurs migrent : aucune jointure n'est restée (les `LEFT JOIN` du journal sont sur clé primaire, ni tri, ni filtre, ni pagination sur ces colonnes).
  Le journal lit désormais salle et créneau en un lot chacun par page ; pas de N+1.
- Parité : caractérisation `LibellesDeLOrganisationDansLesLecturesTest` écrite et VERTE sur les lecteurs SQL AVANT migration (8 tests : médecin NULL, inconnu,
  autre centre, désactivé V52, prénom blanc ; créneau autre centre/inconnu ; filtres par libellé et par identifiant ; journal sans borne de centre), mêmes
  assertions après. Goldens et tests des consommateurs inchangés ; aucun `.approved` modifié, aucun golden `_nouveau_code`.
- KB-ORG-10 : le journal des mouvements n'est pas borné au centre (reproduit) ; contrôle §73 ; à confirmer métiers/DPO au plus tard le 2026-12-15.

## Lot D : fait (2026-11-09, ADR 0110)

- Organisation : `publishedlanguage/SallesEtCreneauxDuCentre`, `DefinirLesSallesDIsolement`, `VerrouDuCentre.reserverLeCentre` ; `SallesEtCreneauxDuCentreJdbc`
  (lectures), `IsolementDesSallesJdbc` (commande), `VerrouDuCentreJdbc` ; beans `sallesEtCreneauxDuCentre`, `definirLesSallesDIsolement`. Contrat `SallesEtCreneauxContrat` (faux en
  mémoire et H2, 7 tests chacun) + 2 tests JDBC.
- Planification : `infrastructure/acl/organisation/` `SallesDuCentrePubliees`, `CreneauxDuCentrePublies`, `SeriesDuCentrePubliees`, `LectureDesRessourcesPubliees`, `VueDesSallesPubliees`,
  `VerrouDuReferentielDesInfirmiersParOrganisation` (les 6 lecteurs SQL et `acl/legacyorganisation` supprimés). Aucun `pom.xml`, aucun seam, aucun cycle.
- Écriture d'isolement : plus aucun `UPDATE salle` dans la Planification ; commande d'Organisation avec le SQL du legacy, exécutée DANS la transaction de
  l'appelant (pas de `@Transactional`, pas de `REQUIRES_NEW`) ; annulation avec la transaction prouvée (Planification et Organisation).
- Verrou : porte sur `centers` ; `FOR NO KEY UPDATE` conservé via une méthode distincte de `verrouiller` (`FOR UPDATE`). Aucun résiduel.
- Parité : caractérisation `LecturesDOrganisationDansLaPlanificationTest` (9 tests) écrite et VERTE sur les lecteurs d'origine AVANT migration ; mêmes assertions
  après. Goldens et tests de Planification inchangés ; aucun `.approved` modifié, aucun golden `_nouveau_code`, aucun écart.

## Lot E : fait (2026-11-09, ADR 0111)

- Organisation : `publishedlanguage/IdentiteImprimee` (`duCentre`, `deLaSociete`, records `IdentiteDuCentre`, `IdentiteDeLaSociete`) ; `infrastructure/persistence/IdentiteImprimeeJdbc` ;
  bean `identiteImprimee`. Seules les données lues sont publiées ; la mise en forme (ligne d'adresse, contact, mentions légales, flux Jasper, `data:` URI, échappement) reste à
  la plateforme. Contrat `IdentiteImprimeeContrat` exécuté sur un faux en mémoire et sur H2 (5 tests chacun).
- Plateforme : `DocumentIdentityProvider` (constructeur `IdentiteImprimee`, API publique inchangée : `paramsFor`, `societeIdentity`, `decorateHtml`, `stripReservedParams`) et
  `ModeleDocumentProvisioner` (constructeur `AnnuaireDesCentres`, `tous()`) ne contiennent plus de SQL sur `centers` ni `societes`. Aucun nouveau fichier de plateforme, aucun seam
  (`seamAllowed` resté `[]` ; `ModeleDocumentProvisioner` inscrit dans `covered.json` parce que le filet des cas limites ci-dessous le couvre désormais).
- Admin générique (`ReferentialAdminJdbcAdapter`) : ÉVALUÉ, NON migré (O4-R13, raison dans l'ADR 0111).
- Parité : `IdentiteImprimeeCasLimitesCharacterizationIntegrationTest` (7 tests) écrit et VERT sur les lecteurs SQL d'origine AVANT migration : société nue, société inactive imprimée
  avec textes rognés et logo JPEG, logo vide et pied blanc, ville = wilaya, société inconnue, provisionnement de tous les centres même inactifs (idempotent), modèle désactivé conservé.
  Mêmes assertions après. Les 4 goldens d'identité imprimée (`o1-logo-documents`) et le reste de la suite d'Organisation inchangés ; aucun `.approved` modifié, aucun golden
  `_nouveau_code`, aucune archive `_ecarts-legacy`, aucun KB nouveau. Constat : le centre sans société est impossible (`centers.societe_id NOT NULL`) ; la jointure externe est conservée.

## État final de O4 (après le lot E)

Liste blanche : **7 lecteurs nominatifs** (aucun ne relève du langage d'Organisation) ; ports `*Fournis` de Stock en place ; annuaires, libellés, salles et créneaux, identité imprimée,
isolement et verrous publiés. Revue indépendante des lots B à E faite (`.ddd/verify/verify-organisation-o4-lots-b-e.md` : pass-with-actions, 0 blocker, 0 major). Statut de la slice : `built` (2026-11-09), avec les résiduels ci-dessous.

**Conditions du retrait du legacy (revue des lots B à E, m1 et m2) :** (1) exécuter les contrats de `VerrouDuCentre` et de `reserverLeCentre` sur PostgreSQL 16 réel (variante `*PostgresContractIT`, O4-R7), car `FOR NO KEY UPDATE`, `COALESCE` et la casse peuvent différer de H2 ; (2) confirmation métiers / DPO de KB-ORG-09 et KB-ORG-10 au plus tard le 2026-12-15. Tant que ces deux conditions ne sont pas levées, `built` ne vaut pas « legacy retirable en production ».

## Résiduels

| N° | Résiduel | Cible |
|---|---|---|
| O4-R1 | Renommer `JourDuCentreLegacy` (plateforme) en `JourDuCentrePublie` : seam à inscrire dans `seamAllowed` après accord de l'utilisateur | utilisateur |
| O4-R2 | Lecteurs comptes / auth / garde d'accès (24-27, 29-31 de l'audit) : propriétaire Identité et accès (ou Documents), aucun test nommé | slice IAM |
| O4-R3 | **CORRIGÉ le 2026-11-09 (L7, ADR 0115, V56)** : un seul `actifs()`, `actifsAuSensStrict` retiré, `centers.actif` NOT NULL DEFAULT TRUE ; les centres sans état entrent dans le contrôle de nuit du Stock ; §72.1 AVANT déploiement ; à confirmer métiers Stock au plus tard le 2026-12-15. (Avant : Stock l'excluait, `actif = TRUE`, les autres l'incluaient, KB-ORG-09.) | métiers / DPO |
| O4-R4 | **Résolu par V56 (ADR 0115)** : `CentresJdbc` lit `actif` par `getBoolean` (NULL = faux) : la colonne n'est plus jamais NULL, la lecture est équivalente à celle des consommateurs ; documenté dans la classe | clos |
| O4-R5 | Calendrier du centre (`center_holiday`, `center_closure_day`, ADR 0094 R1) : 4 lecteurs à migrer, hors lot A | lot ultérieur, butoir 2027-03-31 |
| O4-R6 | Record `CentresActifs` (publishedlanguage) toujours inutilisé ; événements `SalleOuverte`, `SalleFermee`, `CreneauCree` jamais émis (hors périmètre : pas d'événement en refactoring sur place) | lot E |
| O4-R7 | Les contrats des annuaires (et `VerrouDuCentre`) ne tournent pas encore sur PostgreSQL 16 réel (variante `*PostgresContractIT`) ; non traité aux lots B, C et D (étendu aux contrats de `LibellesDeLOrganisation`, `SallesEtCreneauxDuCentre`, `reserverLeCentre`, H2 seulement ; les `*PostgresContractIT` de Planification les exercent quand Docker est là) | lot E |
| O4-R8 | **CORRIGÉ le 2026-11-09 (L7, ADR 0115)** : le journal des mouvements lit nom de salle et libellé de créneau BORNÉS au centre du mouvement (libellé vide sinon) ; aucun transfert de patient entre centres n'écrit de mouvement dans ce code (preuves dans l'ADR) ; §73.1 AVANT déploiement ; à confirmer métiers/DPO au plus tard le 2026-12-15. (Avant : sans borne, KB-ORG-10.) | métiers / DPO |
| O4-R9 | Authentification : `AuthService` lit `centers`/`societes` (nom du centre du jeton, centres et sociétés accessibles au choix de connexion, société active) : jointures avec les comptes (`app_user_*`). Propriétaire cible : Identité et accès (le contexte d'Organisation ne possède pas l'authentification) ; à migrer vers `AnnuaireDesCentres` / `AnnuaireDesSocietes` à l'ouverture de la slice IAM ; butoir 2027-03-31. Aucun test nommé sur ces lectures | slice IAM |
| O4-R10 | Comptes : `DirectionAccountService`, `SocieteAdminAccountService`, `UserRestController` (existence d'une société, centres d'une société, centres d'un compte : jointures `app_user_center` / `centers`). Propriétaire cible : Identité et accès ; mêmes annuaires publiés suffisent pour la lecture ; butoir 2027-03-31 | slice IAM |
| O4-R11 | Garde d'accès : `DirectionAccessGuard` (sociétés accessibles à un compte direction). Propriétaire cible : Identité et accès ; lecture couverte par `AnnuaireDesSocietes` ; butoir 2027-03-31 | slice IAM |
| O4-R12 | Amorçage de développement : `DevSuperAdminBootstrapInitializer` (profil dev : lit la liste des centres, `SELECT id FROM centers`, pour rattacher le super-administrateur de démonstration ; `AnnuaireDesCentres.tous` suffit). Propriétaire : plateforme (outillage de dev) ; à migrer avec la slice IAM ; butoir 2027-03-31 | plateforme / IAM |
| O4-R13 | Admin générique (`ReferentialAdminJdbcAdapter`, ADR 0070) : SQL dynamique sur `salle`, `position_creneau`, `medecin` (et d'autres tables d'autres propriétaires), cache `ref.*` invalidé après commit, désactivation du médecin. Évalué au lot E et laissé (ADR 0111) : propriétaire cible Organisation pour ses trois tables (commandes d'administration des référentiels de salle, créneau, médecin) ; jalon : revue de l'ADR 0070 avec la slice qui découpera l'admin générique par propriétaire ; butoir 2027-03-31 | Organisation / plateforme |

O4-R6 et O4-R7 restent ouverts au lot E (non traités, voir leurs lignes) ; O4-R2 est précisé par O4-R9 à O4-R12.

## Retrait

Pas de retour arrière par flag (ADR 0024) : revert Git par lot. La slice sera `built` quand la liste blanche ne contient plus que les résiduels
nominatifs (7, O4-R9 à O4-R13) et que les ports `*Fournis` de Stock existent : c'est le cas depuis le lot E ; le passage à `built` attend la revue finale.
