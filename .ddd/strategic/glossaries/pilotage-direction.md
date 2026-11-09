---
status: validated
date: 2026-10-09
context: Pilotage direction (supporting, lecture)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §11]
---
# Glossaire — Pilotage direction

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Direction | Vue de la société sur ses centres, anonymisée | — | `Direction*` | — | aucune écriture dans les autres contextes | proposed |
| Séances réalisées (lues) | Activité des centres comptée par la Direction (vue d'ensemble, période précédente, mois, caisses, taux d'absentéisme) ; lue dans le langage publié de la Séance `SeancesRealiseesDesCentresPubliees` (2026-11-08, SE7e, ADR 0099), jamais en SQL sur `seances` | — (port de la Séance) | requêtes SQL des `Direction*QueryService` (retirées) | « séances validées » pour les trois statuts | RG-SEA-065 ; KB-SEA-83, 84 | proposed |
| Instantané mensuel | Indicateurs d'un mois écoulé, figés, jamais recalculés | `InstantaneMensuel` | `direction_snapshot` | snapshot | RG-DIR-090, RG-DIR-104 | proposed |
| Mois d'instantané | Mois civil `AAAA-MM` d'un instantané ; capturable s'il est écoulé et de moins de cinq ans (60 mois inclus) ; la lecture n'exige qu'un mois lisible | `MoisDInstantane` | `YearMonth` + `MONTH` de `DirectionSnapshotService` | mois fiscal, période | RG-DIR-091, RG-DIR-104 | proposed |
| Contenu figé | Vue d'ensemble + indicateurs + répartitions du mois, sérialisés une fois pour toutes ; jamais interprétés ni corrigés | `ContenuFige` | `payload` | payload, blob | RG-DIR-090 | proposed |
| Capture | Acte de figer un mois (à la demande de la direction ou par la tâche du 1er du mois) ; idempotente. Même concept sous deux formes : le NOM d'acte est « capture » (cas d'usage `Capturer…`), le VERBE est « figer » (langage publié `figer`, `figerLeMoisEcoule`, fabrique `InstantaneMensuel.figer`) ; pas de troisième terme | `CapturerUnInstantaneMensuel`, `CapturerLesInstantanesDuMoisEcoule` | `ensure`, `generateForPreviousMonth` | génération, snapshotting | RG-DIR-090, RG-DIR-104 | proposed |
| Figer | Verbe de la capture : graver le contenu d'un mois écoulé, qui ne sera plus jamais recalculé ni remplacé | `figer`, `figerLeMoisEcoule`, `InstantaneMensuel.figer` | `create` | geler, archiver, clôturer (la clôture mensuelle est un autre concept) | RG-DIR-090 | proposed |
| Instantané figé (relu) | Instantané relu tel que stocké : mois, horodatage de capture, contenu ; `breakdown` nul pour un instantané antérieur aux répartitions | `Instantane` (langage publié), `InstantaneFige` (port) | `Snapshot` | snapshot | RG-DIR-104 | proposed |
| Info d'instantané | Mois et horodatage de capture d'un instantané listé (le contenu n'est pas lu) | `Info`, `InstantaneListe` | `Info` | — | RG-DIR-104 | proposed |
| Alerte de direction | Seuil dépassé (sévérité) | `AlerteDirection` | `DirectionAlertPolicy` | — | à valider | proposed |
| État des clôtures | Avancement des 4 clôtures mensuelles par centre | `EtatClotures` (projection) | — (à créer) | — | ADR 0009 | proposed |
| Perte valorisée | Somme des valorisations d'absences (HT) | projection | — | — | issue d'Absences patients | proposed |
| Société suivie | Société dont la direction suit les centres ; son périmètre borne chaque lecture | `SocieteSuivie` | `SocieteInfo` | — | RG-DIR-001, RG-DIR-103 | proposed |
| Période | Bornes incluses, début avant fin, au plus 5 ans (la période précédente, de même durée, n'est pas re-contrôlée) | `Periode` | `from`/`to` | intervalle | RG-DIR-003 | proposed |
| Seuil d'anonymat | Effectif de 1 à 4 masqué (publié absent), zéro reste zéro ; totaux sur les effectifs bruts (KB-DIR-01, à confirmer DPO) | `SeuilDAnonymat` | `AnonymityPolicy` | k-anonymat (technique) | RG-DIR-010, RG-DIR-103 | proposed |
| Bilan d'un centre | Indicateurs d'un centre sur la période (effectif bruts, séances, factures, finances), ou totaux de la société | `BilanDuCentre` | `CentreStats` | — | RG-DIR-020 | proposed |
| Vue d'ensemble | Bilans par centre, totaux, évolution mensuelle et période précédente | `VueDEnsembleDeLaSociete` | `Overview` | — | RG-DIR-020 | proposed |
| Répartition | Patients par sexe, âge, caisse et traitement de l'anémie, par centre | `RepartitionDeLaSociete` | `Breakdown` | ventilation | RG-DIR-040 | proposed |
| Rapport de synthèse | Document de la période (ou d'un mois figé) : synthèse, alertes, finances, évolution, qualité des soins, stock, répartitions | `Rapport` | `DirectionReportPdfService` (calcul et rendu mêlés) | export | RG-DIR-092, RG-DIR-103 | proposed |
