---
status: in-progress (session 1 sur 2-3 : inventaire, retraits sûrs, ports publiés, présence unique ; PAS `built`)
date: 2026-11-02
sources: roadmap « Dette de P6 / P8 / P9 », ADR 0069, KB-PLN-106, `.ddd/safety-net/seams.md`
---

# Slice planification-p9-retrait-du-legacy

Refactoring sur place (ADR 0024) : ni façade, ni flag, ni routage. Aucun fichier legacy édité (seam non utilisé) ; fichiers supprimés par `rm`.

## Fait (session 1, 2026-11-02)

| Domaine | Fait |
|---|---|
| Mon planning | `SemaineDeLInfirmierDuModule` (compose `ConsulterLaPresence` + `ConsulterLaSemaine`) ; ACL `SemaineDeLInfirmierLegacy` supprimée ; 1 scénario ajouté, 2 goldens (`_base` = ancien comportement archivé depuis le legacy, `_nouveau_code`) ; les 17 autres goldens inchangés |
| Places et calendrier | `PlanningDesPlacesDuCentreJdbc`, `CalendrierDuCentreDuModule` ; ACL `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`, `PlanningDonneesJdbcAdapter`, `PlanningDonneesPort` supprimés ; `MouvementsPatientsIntegrationTest` relu par le port publié |
| Capacité des salles | port publié `CapaciteDesSalles`, `ControlerLaCapaciteDeLaSalle`, `CapaciteDeLaSalle` (+ 5 tests), port `SalleDuCentre` ; ACL GMAO `CapaciteDesSallesDePlanification` ; `SalleVueJdbcAdapter`, `SalleVuePort`, `SalleGenerateursService`, `CapaciteSalleRegle` supprimés |
| Code mort retiré | `PresenceInfirmierService`, `PresenceDonnees*`, `PlanningSemaine*` (service, requête, port, modèle), tout `domain.infirmier` (modèle, ports, adapters `AffectationInfirmierJdbcAdapter`, `RemplacementInfirmierJdbcAdapter`), `domain.planning.model.Planning`, `DeplacementTemporaire`, `DeplacementTemporairePort`, 4 classes de test legacy et `PlanningSeancesRealiseesIntegrationTest` (couvert par le filet P6) |
| Gardes | `FrontieresDesContextesTest` (+2 règles), `TableDeLaPlanificationTest` (3 tests dont le négatif), test RG `mon_planning_documente_que_le_sommeil...` |

## Reste à faire (jalons explicites, rien forcé)

| Code | Raison | Jalon |
|---|---|---|
| `SeanceRestController` (jours de dialyse du tableau de bord) | 1200+ lignes, effectif de Patient | contexte Séance |
| `SeancePlanningJdbcAdapter` (règle de présence recopiée) | exige le port `attenduLe` et retire la règle côté Séance | contexte Séance |
| `ReferentialRepositoryAdapter`, `ReferentialAdminTables` | port « utilisé par un patient ? » (`UtilisePourUnPatient`) à publier par Organisation | contexte Organisation |
| `MouvementPatientJdbcAdapter`, `Patient.libererPlacement` | écrivains admission / changement d'état | contexte Patient et couverture |
| `JourSemaine`, `PlanningParametres(+Port)`, `PlanningParametresDuContexte`, `PlacementDeLaFiche`, `PatientRestController` | fiche patient | contexte Patient et couverture |
| `CapaciteTheoriqueCalculator` + `DirectionCapaciteQueryService` | tableau de bord Direction | Pilotage de la direction |
| Le port `SemaineDeLInfirmier` reste dans `publishedlanguage` alors qu'il est désormais interne | déplacer vers `application` | prochaine session P9 |

## Écarts de parité

1 écart décidé (KB-PLN-106, 1 golden `_nouveau_code`). Aucun autre.

## Plan de bascule

Sans objet (sur place) : même table, mêmes URL. Mesure en recette (`verifications-production.md`) : requête SELECT-only comptant les patients en sommeil placés par case, avant livraison, pour annoncer l'effet sur « Mon planning ».
