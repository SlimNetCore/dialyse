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
| Instantané mensuel | Indicateurs d'un mois écoulé, figés, jamais recalculés | `InstantaneMensuel` | `direction_snapshot` | snapshot | RG-DIR-090 | proposed |
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
