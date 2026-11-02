---
status: proposed
date: 2026-11-02
decision-makers: [utilisateur (décision du 2026-11-02 sur « Mon planning » ; à valider), architecte]
context-scope: planification
related: ADR 0024, 0053, 0059, 0060, 0068 ; `.ddd/migration/slices/planification-p9-retrait-du-legacy.md` ; KB-PLN-106 ; `.ddd/safety-net/seams.md`
---

# 0069 — Planification P9 : retrait du legacy, une seule règle de présence dans « Mon planning », capacité des salles publiée

## Contexte

P1 à P8 ont refait la Planification sur place. Restait du legacy consommé par trois ports de la Planification (la semaine de
l'infirmier, le planning des places, le calendrier du centre) et par GMAO (capacité des salles). Refactoring sur place (ADR 0024) :
mêmes tables, mêmes URL, pas de façade ni de routage.

## Décisions

1. **Un seul calcul de présence, le patient en sommeil est exclu partout** (décision de l'utilisateur, 2026-11-02, M1 de la vérification,
   KB-PLN-50 et KB-PLN-106) : le module compose lui-même `ConsulterLaPresence` et `ConsulterLaSemaine` (`SemaineDeLInfirmierDuModule`),
   et la lecture des places de la présence (`PlanningDesPlacesDuCentreJdbc`) ne lit plus les patients en sommeil. Le patient en sommeil
   n'est donc compté ni dans les occupants, ni dans le nombre `patients` de la case, ni dans l'effectif requis, ni dans les alertes de
   sous-effectif et de sur-effectif, ni dans la charge, ni dans « Mon planning ». **Correction d'une prémisse inexacte** : la première
   version de cette décision affirmait que la présence P4 écartait déjà le sommeil ; ce n'était pas le cas (le legacy le comptait dans
   le requis et les alertes, KB-PLN-50 « non décidé »). L'écart est donc plus large que « Mon planning » : il touche les cases de P4.
   La place du patient en sommeil reste tenue sur sa fiche (RG-PLN-021, `PlacesDuCentreJdbc`), pour éviter un double placement à son
   retour. Goldens (ancien comportement archivé avant réécriture dans `_ecarts-legacy`) : « Mon planning » `planning_patient_en_sommeil`
   (legacy rejoué sur un worktree jetable de `de7fcd20~1`, identique octet à octet) et P4 `patients_pris_en_compte`, tous deux en
   `_nouveau_code`. Mesure SELECT seule en recette : `verifications-production.md`, §35, contrôle 12. Reste `proposed`.
2. **Le module possède ses lectures de places et de calendrier** : `PlanningDesPlacesDuCentreJdbc` (mêmes règles que l'ancien
   `PlanningDonneesJdbcAdapter` : sorti garde sa place jusqu'à son dernier jour, **sauf le sommeil, désormais exclu**) et `CalendrierDuCentreDuModule`.
   Les ACL legacy `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`, `SemaineDeLInfirmierLegacy` sont supprimées.
3. **Port publié `CapaciteDesSalles`** (RG-PLN-061) : fourni par la Planification (`ControlerLaCapaciteDeLaSalle`, règle de domaine
   `CapaciteDeLaSalle`), consommé par GMAO par l'ACL `CapaciteDesSallesDePlanification`, qui traduit le refus en 422
   `SALLE_CAPACITE_DEPASSEE` (même message). Aucun changement de comportement.
4. **Retraits** : tout `domain.infirmier` et, dans `domain.planning`, `PlanningSemaine*`, `PlanningDonnees*`, `SalleVue*`,
   `CapaciteSalleRegle`, `Planning`, `DeplacementTemporaire*`, ainsi que les adapters et services qui les portaient.
5. **Gardes** : `FrontieresDesContextesTest` (plus aucune dépendance à `domain.infirmier` ; `domain.planning` limité à une liste fermée de
   consommateurs) et `TableDeLaPlanificationTest` (pas de SQL sur les tables de la Planification hors contexte ; **aucune liste
   blanche** : les quatre entrées de la première version ne lisaient aucune table de la Planification, et l'une désignait un fichier
   inexistant ; un test échoue si une entrée est ajoutée sans correspondre à un lecteur réel).

## Conséquences et dette restante (jalons : roadmap, « Dette de P9 »)

`JourSemaine`, `PlanningParametres(+Port)`, `CapaciteTheoriqueCalculator` restent dans `domain.planning` (fiche patient, tableau de
bord Direction). Séance : `SeancePlanningJdbcAdapter` (règle de présence recopiée ; lit les colonnes de place de `patients`) et
`SeanceRestController`. Organisation : `ReferentialAdminJdbcAdapter` / `ReferentialAdminTables` (« utilisé par un patient ? », lit les
colonnes de place de `patients`). Patient : `MouvementPatientJdbcAdapter`. Cette dette porte sur les colonnes de place de `patients`
(Patient et couverture) et sur des classes Java de `domain.planning`, pas sur les tables de la Planification. Aucun fichier legacy de
ces listes n'a été édité (seam non utilisé).

## Questions ouvertes

- Aucune sur le sommeil (tranché le 2026-11-02). L'ADR reste `proposed` jusqu'à validation.
