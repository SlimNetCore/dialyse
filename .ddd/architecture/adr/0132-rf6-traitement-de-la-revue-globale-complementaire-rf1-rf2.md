---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (« à confirmer <= 2026-12-15 »), architecte]
context-scope: patient-couverture (domaine Forfait, persistance, 400 des listes de choix), facturation (compte d'usage du forfait), platform (gardes d'architecture)
related: ADR 0131 (RF5), 0124 (RF2), 0122 (RF1), 0125 (RF2c) ; revue `.ddd/reviews/2026-11-10-global-complement-RF1-RF2.md`
---

# 0132 - RF6 : traitement de la revue globale complémentaire RF1-RF2

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles. Aucun golden modifié, aucune migration. Aucun KB ne change.

## Décisions
| Constat | Décision | Réalisation |
|---|---|---|
| 1 (major) prix `1e999999999` | Le texte du prix est borné à 32 caractères et l'échelle du nombre lu à +/-32 avant tout `setScale` ; au-delà, anomalie `INVALID_NUMBER` (400 existant). `1e3` et `1e5` restent acceptés (parité), `1e9` reste refusé `NUMBER_TOO_LARGE` | `Forfait.controlerLePrix` ; `ForfaitTest` (rouge vu : `ArithmeticException`) |
| 2 (minor) `catch DataAccessException` | Seule `BadSqlGrammarException` (table absente d'un schéma allégé) est ignorée ; tout autre échec SQL se propage (jamais « 0 référence ») | `TransporteursJdbc`, `UsageDuForfaitParLesPec`, `UsageDuForfaitParLesFactures` ; `ComptesDUsageEnEchecTest` (rouge vu, 2 échecs) et `UsageDuForfaitParLesFacturesEnEchecTest` (voir procédure) |
| 3 (minor) `duCentre` sans ordre | `ORDER BY nom, id` (transporteurs) et `code, id` (forfaits) ; l'import garde `putIfAbsent`, désormais déterministe : en cas de doublon historique, c'est l'identité la plus petite qui gagne | `TransporteursJdbc`, `ForfaitsJdbc` ; cas « homonymes » des contrats (double en mémoire et H2 ; rouge vu) |
| 4 (minor) départage et clé dupliquée | `, id` ajouté aux listes publiées (transporteurs, forfaits, catégories de transport) ; `parCode` appelle `Forfait.cleDe(code)` au lieu de réimplémenter la clé | `ForfaitsJdbc`, `TransporteursJdbc`, `CategoriesDeTransportJdbc`, `Forfait.cleDe` |
| 6 (minor) 400 « paramètre absent » | Exception typée `ParametreObligatoireAbsent` (reconnue par son type) ; la liste blanche par expression régulière ne garde que le message de conversion `Invalid UUID string`. Réponses inchangées | `ParametreObligatoireAbsent`, `RefusDeParametre`, 3 contrôleurs ; `ErreursDesListesDeChoixTest` (rouge : compilation) |
| 7 (minor) jointure par virgule | Les gardes `TableDesForfaitsTest` et `TableDesTransporteursTest` détectent `FROM a, forfait` (et `, transporteur`, `, categorie_transport`) ; cas ajouté à leur test de détection | gardes d'architecture |

## Résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF6-R1 | Message `UTILISE` plus large que le contrôle réel (« patients, prises en charge, séances ou autres référentiels », constat 8) | texte générique de l'administration, figé dans 8 goldens existants (suppressions RF1/RF2/RF3, caches, séances SE7e) : l'aligner modifierait des goldens. Décision métier à prendre ; sans réponse, maintenir |
| RF6-R2 | Unicité du nom (transporteur) et du code (forfait) sans contrainte en base (constat 5) : deux créations concurrentes d'un même centre peuvent créer un doublon (KB-RF1-04, KB-RF2-03) | pas de migration dans ce lot ; poser un index unique `(center_id, lower(trim(nom)))` / `code` après audit des doublons en production |
| RF6-R3 | Les gardes de tables ne voient pas un nom de table assemblé par concaténation, ni une jointure par virgule dont le premier nom n'est pas dans le même littéral | l'effectif « 1 lecteur » est une borne basse ; une analyse SQL complète est disproportionnée |
| RF6-R4 | Un prix en notation scientifique à exposant entre 33 et le maximum est refusé `INVALID_NUMBER` (et non `NUMBER_TOO_LARGE`) | la notation scientifique n'a pas de sens métier au-delà ; décision à confirmer |

## Conséquences
- Retour arrière : revert Git. Aucun seam, aucun flag.

## Questions ouvertes
- À confirmer <= 2026-12-15 : message `UTILISE` (RF6-R1), échéance de l'index unique (RF6-R2), acceptation de la notation scientifique du prix (parité legacy conservée par `1e3`).
