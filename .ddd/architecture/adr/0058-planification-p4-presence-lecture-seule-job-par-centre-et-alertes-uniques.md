---
status: proposed
date: 2026-10-27
decision-makers: [utilisateur (décisions du 2026-10-27, communiquées pour la slice P4), architecte]
context-scope: planification
related: ADR 0024, 0049, 0053, 0054, 0057 ; `.ddd/tactical/planification.md` P17 à P19 ; KB-PLN-42 à 51
---

# 0058 — Planification P4 : présence en lecture seule, contrôle de 07:15 par centre, alertes uniques

## Contexte et problème

La slice P4 déplace dans le contexte `planification` les cinq routes de lecture de la présence des infirmiers (`semaine`,
`alertes`, `remplacants`, `charge`, `imprimer`) et le contrôle quotidien de 07:15. Le filet de caractérisation (75 scénarios) a
montré onze comportements douteux (KB-PLN-42 à 51) : 500 sur les entrées illisibles, jour UTC, centres sans infirmier jamais
contrôlés, notifications dupliquées à chaque exécution, ratio illisible en base qui faisait échouer toutes les lectures,
remplaçants proposés pour des cases inexistantes, charge non triée par nom de famille, sur-effectif invisible par HTTP, formats
d'impression servis en PDF sans erreur, modèle de document créé par une lecture.

## Décisions

1. **Tout est lecture, rien n'est écrit sauf la notification.** Domaine pur (`PresenceDeLaSemaine`, `ClassementDesRemplacants`,
   `ChargeDesInfirmiers`, `OuvertureDuCentre`, `RatioDeLaPresence`), cinq cas d'usage de lecture et `ControlerLaPresence`. Aucun
   verrou n'est pris : l'ordre global des verrous (ADR 0049, 0056, 0057 : centre → fiches `infirmier` par identifiant → `app_user`)
   est inchangé, les lectures ne s'y insèrent pas ; `lock_timeout` 5 s et 409 ne s'appliquent qu'aux écritures de P1 à P3.
2. **Données.** Le personnel (fiches actives, roulement des actifs, absences, remplacements, ratio) est lu par l'adapter
   `PersonnelDeLaPresenceJdbc` (tables du contexte, via la lecture publiée du roulement) ; les places, salles, créneaux, jours
   d'ouverture et fermetures viennent du port consommé `PlanningDesPlaces`, fourni par une ACL du legacy (`PlanningDesPlacesLegacy`,
   retirée avec la slice P5). Le port consommé `SurEffectifDuRoulement` (avertissement d'une affectation, P3) est désormais calculé
   par la présence du contexte ; `SemaineDeLInfirmier` (« Mon planning ») garde sa source legacy (données du planning, P5).
3. **Entrées.** Paramètre illisible ou manquant : 400 `BAD_REQUEST` (`MissingServletRequestParameterException` ajoutée à l'advice du
   module). « Aujourd'hui » et le mois par défaut sont lus au fuseau du centre. Pagination : page négative 400, `size` plafonnée à
   100, taille effective renvoyée.
4. **Remplaçants proposés.** La case est validée comme à l'affectation d'un remplaçant (mêmes codes 422).
5. **Charge.** Tri par nom de famille puis prénom ; un remplacement un jour d'absence du remplaçant ou de fermeture n'est pas compté.
   Les jours d'absence comptent toujours les jours fermés (non décidé, voir questions).
6. **Ratio.** L'écriture refuse hors 1-20 (RG-PLN-003, P1, déjà en place). La **lecture** d'une valeur héritée hors 1-20 vaut le
   défaut 4 et est journalisée (`ParametresDuPlanningJdbc` et `PersonnelDeLaPresenceJdbc`) : aucune lecture ni aucun contrôle ne
   s'arrête sur cette donnée. **Décision de l'utilisateur du 27/10/2026 : on garde 1-20** (cohérent avec P1), amendant la consigne
   initiale « 1-99 » ; une valeur héritée de 21 à 99, que le legacy utilisait telle quelle, est donc lue comme 4 : les centres concernés
   sont à traiter à la main AVANT le déploiement (SELECT n° 1 de `verifications-production.md`). La borne de lecture est celle de l'écriture, car aucune route n'a pu
   écrire entre 21 et 99 ; la requête de `verifications-production.md` liste les centres concernés.
7. **Contrôle de 07:15.** Cron `0 15 7 * * *`, zone `app.planification.fuseau-par-defaut` (Africa/Algiers). Tous les centres
   **actifs** (même sans infirmier), jamais un centre désactivé. « Aujourd'hui » par centre. Les deux contrôles (sous-effectif,
   sur-effectif) et chaque centre sont isolés : l'incident est journalisé et rendu dans le résultat.
8. **Alertes uniques.** Identifiant déterministe = UUID v3 de « type | centre | jour du centre » (modèle des Absences et du Stock,
   `idImpose`) : une alerte par type, centre et jour ; une seconde exécution (ou instance) ne renvoie ni ne duplique. Chaque
   exécution suivante le même jour ne reprend donc pas un changement de situation du jour (assumé).
9. **Sur-effectif visible.** Nouvelle route `GET …/presence/alertes-sureffectif` : cases en sur-effectif et infirmiers prévus dans
   deux salles au même créneau (KB-PLN-50). La double affectation reste comptée présente dans chaque salle (couverture
   potentiellement surestimée, comportement conservé) et signalée en conflit dans la semaine.
10. **Impression.** Formats `PDF` (défaut), `HTML` (PDF base64), `EXCEL`/`XLS`/`XLSX` (xlsx) ; `CSV`, `RTF`, inconnu : 400. La lecture
    ne crée plus le modèle de document : sans modèle, le modèle livré (PDF) est utilisé en mémoire ; modèle désactivé : 422.
11. **Droits inchangés** (annotation et filtre de périmètre) ; les règles de gestion qui divergeaient sont corrigées (RG-INF-044).

## Conséquences

- Le legacy de P4 est retiré (contrôleur, service de requête, rapport, scheduler, services de charge et de remplaçants, ACL
  `SurEffectifDuRoulementLegacy`). Restent, pour l'optimisation (P8) et « Mon planning » : `PresenceDonneesPort`,
  `PresenceDonneesJdbcAdapter`, `Presence`, `PresenceInfirmierService.construire/creneauxDe/requis`, `CalendrierCentre` : **dette de
  P4 vers P8** (le calcul de présence existe donc deux fois jusqu'à P8, et la double affectation y garde son ancien comportement).
  **Retrait** : au plus tard avec la slice P8 (avant le retrait du legacy, P9) ; critère : plus aucun lecteur de `PresenceDonneesPort`.
  Aucune date calendaire n'est arrêtée dans la feuille de route : à fixer au gate du plan de P8.
- Un seul cron à 07:15 Africa/Algiers sert tous les centres : un centre d'un autre fuseau est contrôlé à une autre heure locale (son
  « jour » reste celui de son fuseau). Un cron horaire filtré sur l'heure locale ne serait utile qu'avec un vrai multi-fuseau.
- Une nouvelle route publique (`alertes-sureffectif`) ; aucune migration de schéma.
- Trois ports consommés (fournis par le legacy, ACL dans `platform/.../acl/planification/`) : `PlanningDesPlaces`,
  `NotificationsDeLaPresence`, `ImpressionDeLaPresence`.

## Questions ouvertes

- Les jours d'absence doivent-ils compter les jours fermés dans la charge ? (conservé).
- La double affectation doit-elle déclencher une notification à 07:15, et ne plus compter l'infirmier dans la seconde salle ?
- Un changement de situation après la première exécution du jour doit-il refaire une alerte (clé du jour) ?
- Un patient « en sommeil » compte encore dans l'effectif requis (conservé, à décider).
