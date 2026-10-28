---
status: proposed
date: 2026-10-27
decision-makers: [utilisateur (décisions du 2026-10-27, communiquées pour la slice P7), architecte]
context-scope: planification
related: ADR 0024, 0049, 0052, 0054, 0056, 0059, 0060 ; `.ddd/tactical/planification.md` P7 ; KB-PLN-78 à 88 ; `.ddd/safety-net/seams.md`
---

# 0061 — Planification P7 : libération par patient, fuseau du centre, ordre de nuit explicite,
`PLACE_LIBEREE` sans doublon

## Contexte et problème

Le job de 04:00 libère les places des patients sortis ou dont le séjour est fini. Le filet (29 scénarios, KB-PLN-78 à
88) a montré : une
transaction pour tout le centre (un patient en échec annulait toutes les libérations), un cron sans fuseau, une fiche
entière
réécrite, aucune condition de centre, un mouvement daté J-1 à l'enregistrement et écrit deux fois si la fiche est
renvoyée.

## Décisions

1. **Une transaction par patient** (`LibererUnePlace`), dans l'ordre global des verrous (ADR 0056) : verrou du centre
   (`VerrouDesPlaces`, ligne `planning_parametres` par défaut, `lock_timeout` 5 s posé au premier verrou), puis ligne de
   la fiche (`SELECT … FOR UPDATE`), relue sous verrou. Un patient en échec est journalisé et signalé (notification
   `LIBERATION_PLACES_EN_ECHEC`,
   ADMIN, clé déterministe type + centre + jour du centre) ; les autres sont libérés ; le job est idempotent et réessaie
   la nuit suivante.
2. **Fuseau du centre** :
   `@Scheduled(cron = "0 0 4 * * *", zone = "${app.planification.fuseau-par-defaut:Africa/Algiers}")` ; le jour de
   libération est celui du centre (`HorlogeDesCentres`) ; seuls les centres ACTIFS sont libérés (les autres : requête de
   recette ; définition de « actif » : décision 10).
3. **Ordre de nuit explicite** : détection des absences 02:30 puis libération 04:00, même fuseau (RG-PLN-111) ; test de
   non-régression
   `OrdreDeNuitDeLaLiberationTest` en plus du golden `ordre_de_nuit`. La zone de la replanification automatique de 02:30
   est alignée (décision 11) ; son calcul reste en P8.
4. **`PLACE_LIBEREE`** : daté du jour réel de la libération (jour du centre), jamais deux fois de suite pour la même
   place (dernier
   mouvement du patient comparé : état et place), écrit dans la MÊME transaction (`MouvementsDesPatientsJdbc`, ACL) ;
   aucune notification
   pour une libération normale ; l'évènement `PlaceLiberee` v1 reste DIFFÉRÉ (ADR 0052, Patient et couverture legacy).
5. **Écriture limitée aux colonnes de place** (`PlacesTenuesJdbc`) : plus de réécriture de la fiche (`en_sommeil` NULL
   reste NULL).
6. **Port publié `LiberationDesPlaces.siEchue(centre, patient)`** pour l'enregistrement d'une fiche : échéance jugée à
   la VEILLE, mouvement
   daté du jour du centre, verrou pris seulement s'il y a quelque chose à libérer (409 sinon
   `PlacementDesPatients.EcritureConcurrente`).
   L'ACL `LiberationALEnregistrementDeLaFiche` vide le contexte JPA avant l'appel, relit la fiche gérée et aligne la
   fiche renvoyée.
7. **Reproduit tel quel** : asymétrie du jour d'évènement, sortis sans date libérés tout de suite, états jamais libérés
   (permanent, inconnu,
   minuscules), le sommeil n'empêche pas la libération d'un sorti (**voulu, décision du 28/10**), place partielle
   libérée, centre fermé libéré.
8. **Caches** : port `CachesDesPatients` (publishedlanguage, ACL `CachesDesPatientsLegacy`), éviction APRÈS validation de la transaction.

### Décisions du 28/10 (suite de la revue P7)

9. **Patient sorti** : une NOUVELLE place pour un patient sorti est refusée en 422 `PLACEMENT_PATIENT_SORTI` (P5, `PlacementDeLaFiche`
   → `PlacesDuCentre`), jugé à l'état À VENIR de la fiche (la reprise `PERMANENT` dans la même saisie reste possible) ; place inchangée,
   fiche sans place : acceptées. RG-PLN-050 amendée. Le dédoublonnage de `PLACE_LIBEREE` ne peut donc plus masquer une vraie seconde libération
   d'un sorti replacé.
10. **« Centre actif » unique** : `COALESCE(centers.actif, TRUE)` (Planification comme Absences) ; un centre à `actif` NULL est libéré (comme
    le legacy). Requête 1 de recette = PRÉREQUIS BLOQUANT avant déploiement (à lancer par l'utilisateur).
11. **Une seule clé de fuseau** `app.planification.fuseau-par-defaut` pour les trois jobs de nuit (détection des absences, replanification
    automatique, libération) : seam minimal `ReplanificationAutomatiqueScheduler` (zone) ; `app.absences.fuseau-par-defaut` n'est qu'une
    surcharge facultative. Test : `OrdreDeNuitDeLaLiberationTest` (valeurs résolues).
12. **Dates d'un même enregistrement** : le mouvement de changement d'état est daté du jour du centre (`LiberationDesPlaces.aujourdhui`), comme `PLACE_LIBEREE`.

## Ordre global des verrous (exception levée le 28/10)

À l'enregistrement d'une fiche qui tient une place échue, l'ACL `LiberationALEnregistrementDeLaFiche` prend le verrou du centre AVANT le
vidage du contexte de persistance (qui verrouille la ligne du patient) : ordre centre → fiche, identique à la nuit. Jugement prudent sur la
fiche en mémoire (jour UTC ≥ veille du centre). Test PG16 de non-deadlock (fiche et nuit en parallèle, 8 tours). Limite : si un
auto-flush JPA antérieur dans la même requête a déjà verrouillé la ligne, le cycle redevient possible mais reste borné par `lock_timeout`
5 s (409 côté fiche, signalement côté nuit) ; le contrôle de placement de la fiche (P5) prend déjà le verrou du centre en premier dès
qu'une place est saisie, ce qui est le cas de toute place échue encore portée par la fiche.

## À valider par l'utilisateur

- **KB-PLN-84** : l'écriture limitée aux colonnes de place (au lieu de la fiche entière, `en_sommeil` NULL conservé) ne figure pas dans la
  liste des décisions du 27/10 ; elle est conséquence du choix « transaction par patient » et du verrou de relecture.
- Statut de l'ADR : reste `proposed` jusqu'à validation.

## Conséquences

+ Un patient en échec ne bloque plus le centre ; plus de mise à jour perdue sur la fiche ; dates des mouvements
  cohérentes ; ordre de nuit documenté et testé.
  − Un centre inactif n'est plus libéré (contrôle SELECT en recette) ; une ligne `planning_parametres` par défaut est
  créée pour un centre qui n'en avait pas.
  − `Patient.libererPlacement` et `MouvementPatientJdbcAdapter` restent (alignement de la fiche renvoyée ; mouvements
  d'admission et de changement d'état) jusqu'à l'extraction de Patient et couverture.
  − `PatientRepositoryPort.findWithAssignment` / `findCentersWithAssignments`, `MouvementsPatient.placeLiberee` /
  `placeAReprendre` supprimés (code mort).

## Alternatives écartées

- Libérer par colonnes dans la transaction de la fiche sans verrou : écrasement possible par le job de nuit (KB-PLN-84).
- Dédoublonner par `date_effet` : la date n'identifie pas la libération ; la comparaison état + place + dernier
  mouvement est exacte.
