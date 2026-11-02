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

1. **« Mon planning » passe sur la règle de présence de P4/P6** (décision de l'utilisateur, 2026-11-02, KB-PLN-106) : le module compose
   lui-même `ConsulterLaPresence` et `ConsulterLaSemaine` (`SemaineDeLInfirmierDuModule`). Un patient en sommeil disparaît des
   occupants des cases. Le nombre `patients` de la case (présence des infirmiers, le sommeil GARDE la place) est inchangé : **écart
   à valider** (une case peut afficher `patients: 2` pour 1 occupant). Goldens : `planning_patient_en_sommeil_base` (ancien
   comportement, archivé depuis le legacy avant la réécriture) et `..._nouveau_code`.
2. **Le module possède ses lectures de places et de calendrier** : `PlanningDesPlacesDuCentreJdbc` (mêmes règles que l'ancien
   `PlanningDonneesJdbcAdapter` : sorti garde sa place jusqu'à son dernier jour, sommeil garde la sienne) et `CalendrierDuCentreDuModule`.
   Les ACL legacy `PlanningDesPlacesLegacy`, `CalendrierDuCentreLegacy`, `SemaineDeLInfirmierLegacy` sont supprimées.
3. **Port publié `CapaciteDesSalles`** (RG-PLN-061) : fourni par la Planification (`ControlerLaCapaciteDeLaSalle`, règle de domaine
   `CapaciteDeLaSalle`), consommé par GMAO par l'ACL `CapaciteDesSallesDePlanification`, qui traduit le refus en 422
   `SALLE_CAPACITE_DEPASSEE` (même message). Aucun changement de comportement.
4. **Retraits** : tout `domain.infirmier` et, dans `domain.planning`, `PlanningSemaine*`, `PlanningDonnees*`, `SalleVue*`,
   `CapaciteSalleRegle`, `Planning`, `DeplacementTemporaire*`, ainsi que les adapters et services qui les portaient.
5. **Gardes** : `FrontieresDesContextesTest` (plus aucune dépendance à `domain.infirmier` ; `domain.planning` limité à une liste fermée de
   consommateurs) et `TableDeLaPlanificationTest` (pas de SQL sur les tables de la Planification hors contexte, liste blanche
   documentée, garde testée par un test négatif).

## Conséquences et dette restante (jalons : roadmap, « Dette de P9 »)

`JourSemaine`, `PlanningParametres(+Port)`, `CapaciteTheoriqueCalculator` restent dans `domain.planning` (fiche patient, tableau de
bord Direction). Séance : `SeancePlanningJdbcAdapter` (règle de présence recopiée) et `SeanceRestController`. Organisation :
`ReferentialRepositoryAdapter` / `ReferentialAdminTables`. Patient : `MouvementPatientJdbcAdapter`. Aucun fichier legacy de ces
listes n'a été édité (seam non utilisé).

## Questions ouvertes

- Faut-il aussi retirer le sommeil du compte `patients` de la case de « Mon planning » (cohérence avec les occupants) ? Cela changerait
  la présence de P4 (hors périmètre).
