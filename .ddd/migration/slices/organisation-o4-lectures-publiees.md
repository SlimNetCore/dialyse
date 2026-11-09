---
slice: organisation-o4-lectures-publiees
status: in-progress
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

Le critère est le nombre d'entrées de `TableDeLOrganisationTest.LECTEURS_CONNUS` (un test le fige) : **32 avant le lot A, 22 après le lot A, 18 après le lot B, 15 après le lot C**.

## Plan en lots (audit complet : `.ddd/safety-net/organisation-o4-lectures-publiees.md`, section « Audit O4 »)

| Lot | Besoin | Langage publié (organisation.v1) | Lecteurs | Liste blanche |
|---|---|---|---|---|
| **A** (fait 2026-11-09) | centres actifs / tous ; fuseau du centre ; société d'un centre ; sociétés et centres | `AnnuaireDesCentres` (`actifs`, `tous`, `fuseauHoraireDe`), `AnnuaireDesSocietes` (`societesActives`, `societeDuCentre`, `societe`) | 10 : Absences, Dossier médical, Séance (×2), Patient-couverture, Planification (×2), plateforme (jour du centre), Pilotage (×2) | 32 → 22 |
| **B** (fait 2026-11-09) | Stock : centres actifs (sens strict), fuseau (défaut UTC), centres du traitement de nuit, verrou de centre d'inventaire | ports `CentresFournis` et `VerrouDuCentreFourni` déclarés par Stock (cycle Maven : Organisation dépend de Stock) + traducteurs plateforme appelant `AnnuaireDesCentres` (`actifsAuSensStrict`) et `VerrouDuCentre` | 4 | 22 → 18 |
| **C** (fait 2026-11-09) | libellés de salle, de créneau, de médecin | `LibellesDeLOrganisation` (codes de salles, créneaux, noms de salles et libellés de créneaux sans borne, médecins, recherches par terme) | 3 : Séance `LecturesPublieesDesSeancesJdbc`, Patient-couverture (×2) | 18 → 15 |
| D | séries et ressources du planning, salles, verrou du référentiel des infirmiers | `RessourcesDuCentre` (salles, créneaux, séries), commande publiée pour l'isolement des salles (aujourd'hui `UPDATE salle`, écriture), `VerrouDuCentre` | 6 : Planification | 15 → 9 |
| E | identité imprimée (logo, pied de page), admin générique | `IdentiteDeLaSociete`, retour de l'admin générique dans le contexte (ADR 0070) | 2 sur 9 (DocumentIdentityProvider, ReferentialAdminJdbcAdapter) | 9 → 7 |
| Résiduels | comptes, auth, garde d'accès, provisionnement, amorçage dev | — (propriétaire : Identité et accès / Documents, hors Organisation) | 7 nominatifs | 7 |

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

## Résiduels

| N° | Résiduel | Cible |
|---|---|---|
| O4-R1 | Renommer `JourDuCentreLegacy` (plateforme) en `JourDuCentrePublie` : seam à inscrire dans `seamAllowed` après accord de l'utilisateur | utilisateur |
| O4-R2 | Lecteurs comptes / auth / garde d'accès (24-27, 29-31 de l'audit) : propriétaire Identité et accès (ou Documents), aucun test nommé | slice IAM |
| O4-R3 | `centers.actif` NULL : Stock l'exclut (`actif = TRUE`), les autres l'incluent. **Lot B : reproduit chaque comportement** (`actifsAuSensStrict` / `actifs`), KB-ORG-09, contrôle §72 ; à confirmer métiers/DPO au plus tard le 2026-12-15 (0 NULL : le Stock pourra passer à la variante large) | métiers / DPO |
| O4-R4 | `CentresJdbc` (Organisation) lit `actif` par `getBoolean` (NULL = faux) alors que les consommateurs lisent NULL = actif | lot B |
| O4-R5 | Calendrier du centre (`center_holiday`, `center_closure_day`, ADR 0094 R1) : 4 lecteurs à migrer, hors lot A | lot ultérieur, butoir 2027-03-31 |
| O4-R6 | Record `CentresActifs` (publishedlanguage) toujours inutilisé ; événements `SalleOuverte`, `SalleFermee`, `CreneauCree` jamais émis (hors périmètre : pas d'événement en refactoring sur place) | lot D |
| O4-R7 | Les contrats des annuaires (et `VerrouDuCentre`) ne tournent pas encore sur PostgreSQL 16 réel (variante `*PostgresContractIT`) ; non traité aux lots B et C (étendu aux contrats de `LibellesDeLOrganisation`, H2 seulement) | lot D |
| O4-R8 | KB-ORG-10 : le journal des mouvements lit nom de salle et libellé de créneau sans borne de centre (reproduit) ; contrôle §73 ; à confirmer métiers/DPO au plus tard le 2026-12-15 | métiers / DPO |

## Retrait

Pas de retour arrière par flag (ADR 0024) : revert Git par lot. La slice sera `built` quand la liste blanche ne contient plus que les résiduels
nominatifs (7) et que les ports `*Fournis` de Stock existent.
