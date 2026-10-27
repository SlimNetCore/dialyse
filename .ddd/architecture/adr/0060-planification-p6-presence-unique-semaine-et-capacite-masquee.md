---
status: proposed
date: 2026-10-27
decision-makers: [utilisateur (décisions du 2026-10-27, communiquées pour la slice P6), architecte]
context-scope: planification
related: ADR 0024, 0049, 0052, 0053, 0054, 0059 ; `.ddd/tactical/planification.md` P6 ; KB-PLN-67 à 76 ; RG-PLN-036, 040, 045, 070 à 074 ; `.ddd/safety-net/seams.md`
---

# 0060 — Planification P6 : une seule présence des patients, semaine dans le module, capacité de la Direction masquée par centre

## Contexte et problème

La slice P6 déplace dans `contexts/planification` la lecture du planning de la semaine (`GET /api/v1/planning/semaine`) et remplace les
lectures SQL des colonnes de place et de l'état du patient par des langages publiés. Le filet (79 scénarios, KB-PLN-67 à 76) a montré trois
calculs de « qui est là » qui divergeaient : la grille gardait le patient en sommeil, les Absences et la file active de la Direction l'excluaient ;
un jour férié ne retirait pas la capacité d'une case ; la capacité de la Direction comptait des générateurs hors service et son total
consolidé permettait de retrouver, par différence, une file masquée.

## Décisions (utilisateur, 2026-10-27)

1. **Entrées** : 400 `BAD_REQUEST` pour `date` / `centerId` illisibles de `/planning/semaine` et pour `from` / `to` illisibles de `/direction/capacite`
   (comme P1 à P5). La date par défaut de la semaine est « aujourd'hui » au **fuseau du centre** (`HorlogeDesCentres`) ; la période par défaut de la
   capacité (société, plusieurs centres) au fuseau par défaut de la planification (`app.planification.fuseau-par-defaut`). Méthodes et chemins inconnus
   (500) et anonyme (403) : **inchangés** (traitement global, hors périmètre), droits inchangés.
2. **Une seule présence** : `SejourDuPatient` (domaine) répond à trois questions nommées : la place est tenue, le patient figure au planning (place tenue
   et pas en sommeil), une séance est attendue (présent et pas parti ce jour-là). Exposée par le port publié `PresenceDesPatients` (`attendusLe`,
   `fileActive`) à Absences et à la Direction. **Un patient en sommeil est exclu de la grille** (et de ses conflits) ; sa place reste tenue pour P5
   (éviter un double placement à son retour).
3. **Jour fermé = capacité 0** : un jour férié ou une fermeture exceptionnelle met la capacité de la case à 0 comme la fermeture hebdomadaire. Les
   conflits restent jugés sur la capacité réelle des générateurs. Jours d'ouverture stockés en minuscules : **reproduit** (400), documenté.
4. **Capacité de la Direction** : un centre dont la file active est de 1 à 4 est **masqué et exclu du total consolidé** (champ `centresMasques`,
   `AnonymityPolicy` des Absences, KB-ABS-07) ; seuls les générateurs `EN_SERVICE` sont comptés (`GenerateursInstalles.enServiceParCentre`, GMAO) ; séries,
   patients par poste et file active viennent du port publié `BaseDeLaCapaciteTheorique`.
   **Masquage complet (revue P6)** : pour un centre masqué, `capacite`, `niveau` et `atteinte` sont masqués avec la file et le taux (null), car ils la
   bornent (capacité 2 + `ATTEINTE` = file de 2 à 4) ; les équipements (générateurs, postes, séries, patients par poste) restent publiés car ils ne sont
   pas des données patient (la capacité reste donc recalculable à la main, mais sans niveau ni « atteinte » elle ne borne plus la file). Un centre à 0 est
   publié. **Total = centres publiés** (choix prudent, confirmé comme décision) : le total exclut aussi la capacité et les générateurs des centres masqués,
   sinon capacité du total moins capacités publiées redonnerait la capacité du centre masqué ; le chiffre « capacité de la société » est donc celui des seuls
   centres publiés, et l'écran le dit (`centresMasques`). La coupure des générateurs installés est la fin de la période au fuseau par défaut de la
   planification (et non minuit UTC) : un générateur installé la dernière heure locale du dernier jour n'est plus compté (la fixture « limite » du test GMAO passe de 23:59Z à 22:59Z ; golden inchangé).
5. **Déplacement temporaire et place figée** (KB-PLN-73, 74) : **reproduits en lecture** (tolérance silencieuse d'une cible inconnue, « déjà réalisée le … »
   sur les jours habituels, place figée non contrôlée). Leur refus à l'écriture est l'affaire de P8.
6. **Seams** : édition minimale des lecteurs SQL hors module, autorisée par l'utilisateur, inscrite dans `seams.md` / `covered.json` avant chaque édition.

## Conséquences

- La règle de sortie « le jour du départ n'attend plus de séance » (transféré, guéri : KB-ABS-24) reste distincte de « la place est tenue jusqu'au
  dernier jour inclus » (RG-PLN-010) : deux noms (`estAttenduLe`, `occupeSaPlaceLe`) d'un même séjour, pas deux calculs.
- L'ordre des patients attendus suit l'ordre de l'écriture hexadécimale des identifiants (= ordre binaire de PostgreSQL), et non `UUID.compareTo`.
- Ordre des patients d'un conflit : tri par prénom, nom puis identifiant (le legacy n'avait pas d'`ORDER BY`).
- La vue de la semaine de l'infirmier (`SemaineDeLInfirmierLegacy`) et la présence des infirmiers du legacy gardent l'ancien calcul jusqu'à leur retrait
  (dette consignée dans la fiche de la slice).
- 12 goldens P6 changent volontairement (`*_nouveau_code`, anciens archivés sous `p6-semaine/_ecarts-legacy`, plus les états intermédiaires `*_avant_masquage_complet`) et
  2 goldens GMAO de la capacité (modifiés sur place faute de convention `_nouveau_code` dans ce support de test, anciens archivés sous `gmao/pilotage/_ecarts-legacy`).
- Un patient en sommeil disparaît de la grille mais **tient sa place** : la grille peut montrer une case apparemment libre que le placement (P5) refuse
  encore ; documenté (RG-PLN-046, fiche), un compteur « places tenues » est une évolution possible (P8).
- Le chiffre « patients sur équipement indisponible » garde la liste des statuts de l'ancienne jointure SQL (le port GMAO `IndisponibilitesDesGenerateurs`
  parle d'interventions datées, pas de statuts) et compte tous les états ; seul écart : un patient rattaché au générateur d'un AUTRE centre ne compte plus.
- Les comptages (présence, file active, patients d'un générateur) ne lisent plus le dossier médical (`duCentreSansRisque`).

## Alternatives écartées

- Faire de la Direction le propriétaire de la file active : la règle de séjour est celle de la planification (RG-PLN-010), la dupliquer recréerait la divergence.
- Retirer la place d'un patient en sommeil : change P5 et le placement ; non demandé.
