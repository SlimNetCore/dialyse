---
status: proposed
date: 2026-10-26
decision-makers: [utilisateur (décisions du 2026-10-26), architecte]
context-scope: planification
related: ADR 0024, 0043, 0049, 0053 ; `.ddd/tactical/planification.md` P10-P11 ; KB-PLN-01 à 11
---
# 0055 — Planification P1 : verrou du centre sans création de ligne, lecture publiée du paramétrage par ACL

## Contexte et problème
La slice P1 (paramétrage) est la première du module. Il faut (1) rendre les 4 écritures de paramétrage sérialisées et refusées en 409 en cas de
concurrence (KB-PLN-10), (2) laisser le legacy qui LIT ce paramétrage (présence, optimisation, ordonnanceurs) continuer de fonctionner, (3) fixer
les ports vers Organisation, Patient et infirmiers.

## Décisions
1. **Verrou du centre** (ADR 0049) : `SELECT … FOR UPDATE` de la ligne `planning_parametres` du centre, pris en premier par `ModifierLesParametres`,
   `ModifierLesReglages`, `DefinirLaPreferenceDuPatient`, `DefinirLeProfilDeLInfirmier`. **Variante de l'ADR 0049** : si le centre n'a pas de ligne, rien
   n'est créé (le filet verrouille « aucune ligne écrite » pour les lectures et pour les écritures des autres tables) ; les premières écritures
   simultanées d'une même clé sont départagées par la clé primaire (`DuplicateKeyException` ⇒ 409). Délai 5 s (`SET LOCAL lock_timeout`, ADR 0043).
   **Cette variante ne vaut QUE pour le paramétrage.** `PlacesDuCentre` (P5, placement ; et toute slice qui écrit une place) protège un invariant
   d'ensemble : un centre sans ligne n'aurait aucune exclusion mutuelle (double occupation d'un générateur possible). Cette slice devra donc créer la
   ligne par défaut (`INSERT … ON CONFLICT DO NOTHING` puis `FOR UPDATE`) ou verrouiller autrement ; `VerrouDuCentreJdbc` ne doit pas être réutilisé tel
   quel. Décision à prendre en tête de la fiche de cette slice (rappelée dans `roadmap.md`).
   **Amendement 2026-10-27 (P5, ADR 0059)** : la variante « sans création de ligne » est **limitée au paramétrage** (P1 à P4) ; `PlacesDuCentre` crée la
   ligne par défaut puis la verrouille (`VerrouDesPlacesJdbc`), la perdante d'une création simultanée est refusée en 409.
   **Ordre des verrous** : le verrou du centre est toujours pris en premier ; ensuite seulement `salle`, la table propre, puis (roulement, application
   d'une proposition) les infirmiers par identifiant croissant (ADR 0051). Un autre ordre risque l'interblocage, que `RefusDeVerrou` transformerait en 409.
   **Sémantique** : la seconde écriture d'un centre attend le verrou puis passe (la dernière écrite l'emporte) ; le 409 n'arrive qu'après 5 s d'attente
   ou si la même clé est créée entre-temps.
2. **Remplacement total** de `PUT /parametres` conservé (KB-PLN-05) ; `salle.isolement` est écrit par le port `SallesDuCentre` (ACL Organisation,
   ADR 0053), jamais par le domaine.
3. **Lecture publiée** `publishedlanguage.ParametrageDeLaPlanification` (paramètres, réglages, préférences, profils, centres en replanification) ;
   le legacy la consomme par 4 ACL de `platform/.../infrastructure/acl/planification/` qui implémentent ses ports de lecture restreints (écriture
   supprimée de `PlanningParametresPort`, `PreferencePatientPort`, `ProfilInfirmierPort`, `ReglagesOptimisationPort`). Les modèles `PlanningParametres`,
   `PreferencePatient`, `ProfilInfirmier`, `ReglagesOptimisation` restent dans `bc-seance` : l'optimisation (P7/P8) et la présence (P3/P4) en dépendent.
4. **Audit** : pas de code nouveau, le filtre générique trace déjà les écritures ; un test d'intégration le prouve.
5. Table `planning_parametres` : plus d'entité JPA ; `db/schema.sql` (H2) la crée ; Flyway inchangé (aucune migration).

## Conséquences
+ Écritures sérialisées sans changer l'état observable ; legacy inchangé côté lecture ; 4 ACL temporaires (retrait avec leurs consommateurs, P3-P8).
− Le tout premier enregistrement d'un centre n'est protégé que par la clé primaire (409 pour la perdante, rien n'est corrompu).
