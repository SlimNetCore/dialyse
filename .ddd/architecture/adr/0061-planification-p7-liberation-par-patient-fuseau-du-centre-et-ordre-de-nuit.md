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
   recette).
3. **Ordre de nuit explicite** : détection des absences 02:30 puis libération 04:00, même fuseau (RG-PLN-111) ; test de
   non-régression
   `OrdreDeNuitDeLaLiberationTest` en plus du golden `ordre_de_nuit`. La replanification automatique de 02:30 (P8) reste
   hors périmètre.
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
   minuscules), le sommeil n'empêche pas la libération d'un sorti (**à décider**), place partielle libérée, centre fermé
   libéré.
8. **Caches** : port `CachesDesPatients` (ACL `CachesDesPatientsLegacy`), éviction APRÈS validation de la transaction.

## Exception documentée à l'ordre global des verrous

À l'enregistrement d'une fiche, la ligne du patient est déjà modifiée (flush JPA) quand le verrou du centre est demandé
(cas « place échue »
seulement). Un job de nuit tenant le verrou du centre et attendant cette ligne produirait un cycle ; il est borné par
`lock_timeout` 5 s (PostgreSQL : erreur 55P03 ; la fiche répond 409, la nuit signale le patient et réessaie). Aucun
cycle sans limite. Même nature que l'exception
de la sérologie (ADR 0059 § 10).

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
