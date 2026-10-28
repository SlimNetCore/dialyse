---
slice: planification-p7-liberation-places
status: built (2026-10-27, session unique) ; ADR 0061 proposed ; build complet `./mvnw -o test` vert (2482 tests, 0 échec, 4 ignorés), check-arch 0 violation, rejeu PostgreSQL 16 vert (14/14)
date: 2026-10-27
order: 7
context: planification
sources: [.ddd/safety-net/planification-p7-liberation-places.md, .ddd/tactical/planification.md (P7), ADR 0049 à 0061, KB-PLN-78 à 88, RG-PLN-108 à 111, RG-PAT-033/034]
---

# P7 — Libération des places

Refonte sur place (ADR 0024) : ni façade, ni drapeau, ni projection ; mêmes tables et mêmes URLs. Décisions utilisateur
du 2026-10-27 : voir ADR 0061.

## Fait

| Couche                     | Contenu                                                                                                                                                                                                                                                                                                                                                                  |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Domaine                    | `PlaceTenue` (place tenue + dernier jour de place, `liberer(échéance jugée le, date d'effet, instant)`), `PlaceQuittee`, `PlaceLiberee`                                                                                                                                                                                                                                  |
| Application                | `LibererLesPlacesEchues` (job : centres actifs, jour du centre, un patient par transaction, alerte), `LibererLaPlaceDeLaFiche` (port publié `LiberationDesPlaces`, échéance à la veille), `LibererUnePlace` (verrou du centre, fiche relue sous verrou, mouvement puis effacement, caches) ; ports sortants `PlacesTenues`, `MouvementsDesPatients`, `CachesDesPatients` |
| Adapters sortants          | `PlacesTenuesJdbc` (+ `FinDOccupation`), `MouvementsDesPatientsJdbc` (ACL `legacypatient`) ; platform : `CachesDesPatientsLegacy`, `NotificationsDeLaLiberationLegacy`, `LiberationALEnregistrementDeLaFiche`                                                                                                                                                            |
| Adapter entrant            | `LiberationDesPlacesDe0400` (cron `0 0 4 * * *`, zone `Africa/Algiers`) ; câblage `PlanificationConfiguration`                                                                                                                                                                                                                                                           |
| Legacy édité (seam, fermé) | `PatientApplicationService` (appel ligne 107), `NotificationService` (`notifyLiberationDesPlacesEnEchec`, type durable) ; supprimés : `LiberationPlacesService`, `LiberationPlacesScheduler`, `PatientRepositoryPort.find…WithAssignment` (+ adapter, JPA, 3 fakes de test), `MouvementsPatient.placeLiberee` / `placeAReprendre`                                        |
| Frontend                   | `LIBERATION_PLACES_EN_ECHEC` (présentation de la notification, titre et message fr/en/ar/kab, spec 16 tests)                                                                                                                                                                                                                                                             |
| Docs                       | RG-PLN-108 à 111, RG-PAT-033, notifications (15), annexe (16), glossaire (6 termes proposed), KB-PLN-78 à 88 décidés, `verifications-production.md` (P7, 8 requêtes SELECT), roadmap                                                                                                                                                                                     |

## Parité

| Scénarios du filet | Verts | Écarts décidés                                                                                                                                           |
|--------------------|-------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| 29                 | 29    | 23 goldens `_nouveau_code` (anciens archivés octet pour octet depuis `git show HEAD:` dans `p7-liberation/_ecarts-legacy/{nuit,centres,enregistrement}`) |

Détail des 23 : 19 goldens `*_patients` où seule la colonne `en_sommeil` change (NULL reste NULL, KB-PLN-84) ;
`calendrier_de_nuit` (zone Algiers) ; `centre_inactif_patients`, `centre_inactif_mouvements` (centre inactif non
libéré) ;
`passages_en_echec`, `passages_repetes`, `apres_les_echecs_patients`, `apres_reprise_patients` (un patient par
transaction) ; `verrou_de_placement` (la libération prend le verrou) ;
`enregistrement_mouvements`, `avant_la_nuit_mouvements`, `apres_la_nuit_mouvements` (date J au lieu de J-1) ;
`deux_enregistrements_mouvements` (pas de second mouvement). (Certains goldens cumulent plusieurs causes.)
Modification du harnais (pas des attentes) : `LiberationCharacterizationSupport` appelle le nouveau job
(`scheduler.libererPlaces()` conservé comme alias) ; le scénario « un patient en échec » remet le centre A à son état de
départ entre les passages (la contrainte d'échec ne peut plus se poser sur un patient qui a déjà son mouvement) ; le
scénario « service par centre » appelle `LibererLesPlacesEchues.liberer(centre)`.

## Preuves

Domaine (`PlaceTenueTest`, 10), application (`LiberationDesPlacesApplicationTest`, 13, monde en mémoire
`LiberationEnMemoire`), contrat JDBC (`LiberationJdbcContratTest` H2, 12 ; `LiberationJdbcPostgresContractIT` PostgreSQL
16 via
`backend/scripts/planification-p7-pg16-rejeu.sh`, port 55467 : 14/14 dont échec d'un patient dans le lot, verrou du
centre tenu ailleurs, course libération contre fiche), ACL notification (`NotificationsDeLaLiberationLegacyTest`),
ordre de nuit (`OrdreDeNuitDeLaLiberationTest`), `check-arch.ts` 0 violation, frontend
`notification-presentation.util.spec.ts` 16 verts.

## Points à valider (utilisateur)

Décisions de l'utilisateur du 28/10 (appliquées, voir « Corrections de la revue P7 » plus bas) : sommeil et libération d'un sorti = VOULU
(« à décider » levé) ; placement d'un sorti refusé en 422 `PLACEMENT_PATIENT_SORTI` ; requête 1 de recette = prérequis bloquant ;
ordre des verrous ; zone de la replanification et clé unique ; documents clients régénérés.

Reste à valider / à faire :
1. **KB-PLN-84** : écriture limitée aux colonnes de place (absente de la liste des décisions du 27/10). ADR 0061 reste `proposed`.
2. **Prérequis bloquant avant déploiement** : l'utilisateur lance la requête 1 de `verifications-production.md` (centres inactifs avec places échues).
3. **Relecture kabyle par un locuteur** (textes laissés en anglais ou approximatifs) : `NOTIFICATION.TITRE.LIBERATION_PLACES_EN_ECHEC`,
   `NOTIFICATION.LIBERATION_PLACES_EN_ECHEC` (kab traduit, à relire) et `PLANNING.PLACEMENT_ERR.PLACEMENT_PATIENT_SORTI` (kab EN ANGLAIS) ;
   dettes P6 déjà consignées : `MASKED_NOTE`, `VERDICT_MASQUE`, `CARTE_MASQUEE`.
4. La replanification automatique garde `LocalDate.now(UTC)` pour son calcul (P8).

## Corrections de la revue P7 (`.ddd/verify/review-planification-p7.md`, 2026-10-28)

| Constat | Correction |
|---|---|
| major 1 : scénario `fuseau` dépendant de l'heure | garde 23-24 UTC (`aujourdhui()`, scénario ignoré dans la fenêtre) ; renommé « …est_celle_du_jour_du_centre » ; calendrier de nuit renommé « …lisent_la_meme_cle_de_fuseau » |
| major 2 : « centre actif » divergent | `CentresActifsLegacy` : `COALESCE(actif, TRUE)` (aussi pour le contrôle de présence 07:15) ; test H2 `should_consider_a_centre_with_a_null_active_flag…` ; requêtes 1, 2, 8 de recette alignées |
| minor : écrasement inverse | refus 422 `PLACEMENT_PATIENT_SORTI` (voir ci-dessous) + test de contrat H2/PG16 `should_see_as_left_the_patient_freed_by_the_night…` ; risque résiduel consigné (KB-PLN-84) |
| minor : `apres_les_echecs_patients` | photographié AVANT la remise à zéro : `apres_echec_de_A1/A2/A3_patients` (3 goldens `_nouveau_code`) ; l'ancien golden retiré (archive `_ecarts-legacy/centres/apres_les_echecs_patients.legacy.json` conservée) ; `centresAvecPlaces` est une requête du harnais (n'observe pas le système) |
| minor : dates incohérentes | le mouvement de changement d'état est daté du jour du centre (`LiberationDesPlaces.aujourdhui`, seam `PatientApplicationService`) |
| minor : application dépend d'une ACL | accepté comme seam, tracé dans `seams.md` |
| minor : dédoublonnage vs replacement | levé par `PLACEMENT_PATIENT_SORTI` (un sorti ne peut plus être replacé) |
| minor : deux clés de fuseau | UNE clé `app.planification.fuseau-par-defaut` pour absences 02:30, replanification 02:30 (seam `ReplanificationAutomatiqueScheduler`) et libération 04:00 ; test sur les valeurs résolues |
| minor : ADR `proposed` | reste `proposed` ; KB-PLN-84 inscrit dans les décisions à valider |
| nits | non traités (alerte sur échec de lecture d'un centre, VO `DernierJourDePlace`) ; requêtes de recette : décalage `current_date` signalé |

Décisions du 28/10 : (1) sommeil = voulu (« à décider » levé partout, aucun code) ; (2) `PLACEMENT_PATIENT_SORTI` (P5 : `PlacementDesPatients` + `PlacerLePatient` + `PlacesDuCentre` + `estSorti`; goldens P5 `sorti_nouvelle_place` et `sorti_nouvelle_place_etat` `_nouveau_code`, scénario AJOUTÉ donc sans golden HEAD à archiver ; RG-PLN-050 amendée ; i18n fr/en/ar/kab) ;
(3) requête 1 de recette en tête de section, prérequis bloquant ; (4) ordre des verrous : le verrou du centre est pris AVANT le vidage JPA (ACL de la fiche) quand la fiche tient une place échue, test PG16 de non-deadlock ; (5) zone de la replanification ; (6) `docs/client/documentation-fonctionnelle.{html,pdf,docx}` et `maquette-commerciale.{html,pdf,docx}` régénérés par `docs/client/tools/build-*.cjs` (584 règles).

Build après corrections (2026-10-28) : `./mvnw -o test` COMPLET 2484 tests, 0 échec, 4 ignorés ; `check-arch.ts` 0 violation ; PostgreSQL 16 jetable 16/16 (2 exécutions, dont non-deadlock fiche/nuit) ; filets P5 (24 scénarios fiche) et P7 (29) verts.

## Plan de bascule

Sans objet (refonte sur place, pas de drapeau). Retour arrière : revert du lot ; aucun schéma modifié. Observation après
déploiement : `verifications-production.md` (Slice P7).

## État du build

`./mvnw -o test` COMPLET : 2482 tests, 0 échec, 0 erreur, 4 ignorés (1er passage : 1 rouge,
`FrontieresDesContextesTest` — le port `CachesDesPatients` était dans `application` ; déplacé dans `publishedlanguage`,
2e passage vert).
Filet P7 : 29/29, 3 exécutions (ordre par défaut + 2 aléatoires) sans flaky. `check-arch.ts` 0 violation. ArchUnit du
module et des frontières vert. PostgreSQL 16 jetable (`planification-p7-pg16-rejeu.sh 55467`) : 8 requêtes de recette
exécutables, 14/14.
PIT du module (`backend/scripts/pit-p7-module.sh`, domaine + application) : 1205 mutants, 1083 tués (90 %), 40 sans
couverture, force des tests 93 %, couverture de lignes 95 %.
Non fait : régénération de `docs/client/documentation-fonctionnelle.html` et de la maquette ; pas de test frontend de la
bascule d'écran (aucun écran touché, seulement la cloche).
