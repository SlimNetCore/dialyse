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

1. Le sommeil n'empêche pas la libération d'un sorti (reproduit, « à décider »).
2. Le placement d'un patient sorti reste accepté par P5 (KB-PLN-83) : refuser en 422 ?
3. Les centres inactifs ne sont plus libérés : contrôle de recette (requête 1) avant déploiement.
4. Traduction kabyle de `LIBERATION_PLACES_EN_ECHEC` à faire relire par un locuteur.
5. La replanification automatique de 02:30 (P8) garde la zone de la JVM.
6. `docs/client/documentation-fonctionnelle.html` et la maquette sont à régénérer (RG-PLN-108 à 111).

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
