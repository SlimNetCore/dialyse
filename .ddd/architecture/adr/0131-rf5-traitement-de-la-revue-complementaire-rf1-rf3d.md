---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [utilisateur (lot RF5, « à confirmer <= 2026-12-15 »), architecte]
context-scope: seance, pilotage-direction (ordre des listes publiées), patient-couverture (400 des listes de choix), platform (advice, gardes d'architecture)
related: ADR 0130 (RF4, questions ouvertes), 0127 (RF3b), 0129 (RF3d), 0123 (RF1c), 0125 (RF2c) ; revue `.ddd/migration/reviews/revue-RF1-RF3d-complement.md` ; known-bugs KB-RF5-01 à 05
---

# 0131 - RF5 : traitement de la revue complémentaire RF1-RF3d

Refactoring sur place (ADR 0024) : mêmes tables, mêmes URLs, mêmes rôles, SQL inchangé (pas de rejeu PG16 : seul du Java est modifié). Aucun golden modifié, aucun `_nouveau_code`, aucune archive `_ecarts-legacy` : tous les comportements des goldens sont déjà ceux décidés ci-dessous.

## Décisions
| N° | Constat | Décision | Réalisation |
|---|---|---|---|
| 1 | 1 (minor) | Ordre de sortie GARANTI par un tri final en Java (identique sur H2 et PostgreSQL ; un `ORDER BY` SQL sur un UUID n'est pas identique entre les deux moteurs) : `parCaisse` = centre (texte de l'identifiant) puis code de caisse ; `parMois` = centre, année, mois. Javadoc du port publié corrigée (« aucun tri » devient l'ordre garanti) | `SeancesRealiseesDesCentresJdbc`, `PatientsDesCentresJdbc` ; contrats `PilotageEtRapportsDesSeancesContrat` (3 cas, double en mémoire + JDBC H2 + PG) et `LecturesDuPilotageContrat` (2 cas). Rouge vérifié : 3 échecs du double, 1 échec JDBC H2 (`parCaisse`) ; les cas `parMois` JDBC et Pilotage étaient déjà verts sur H2 par l'ordre incident du `GROUP BY` : ils verrouillent l'ordre sans l'avoir vu rouge |
| 2 | 2 (minor) | L'ancien `MAX(ca.nom)` suivait la collation de la base, que le code ne définissait pas : il n'y a pas de comportement précis à reproduire. On ne change RIEN au comportement (`compareTo`), on corrige la javadoc (« à collation près ») et on consigne KB-RF5-01. Seul changement observable : le résultat ne dépend plus de l'ordre de lecture (le double en mémoire gardait « le dernier lu ») | javadoc ; contrats « nom le plus grand » (rouge vérifié sur le double) |
| 3 | 3 (minor) | Déjà réalisé par RF4 (ADR 0130 n° 3) : `TableDesTransporteursTest` et `TableDesForfaitsTest` utilisent `ButoirsDesGardesDeTables` (date comparée à l'horloge, exclusion `/entity/` remplacée par la liste nominative `ENTITES_JPA_CONNUES`, vide) ; `ForfaitsDeSeanceJdbc` a son butoir 2027-03-31. Vérifié seulement | relecture + exécution des gardes |
| 4 | 4 (minor) | Liste blanche de messages attendus dans les trois advices frères : `Invalid UUID string: <une ligne, 200 caractères au plus>` et `Paramètre obligatoire absent : <nom>` ; tout autre message devient « Requête invalide ». 400 `BAD_REQUEST` inchangé ; les deux messages des goldens sont dans la liste blanche, donc goldens inchangés. Le 400 de `centerId` absent précédant le contrôle de centre est CONSIGNÉ comme inchangé (aucune donnée n'est lue) | `RefusDeParametre` ; `ErreursDesListesDeChoixTest` (rouge vérifié : 2 échecs) |
| 5 | 5 (minor) | Test d'architecture `RoutesDeLAdministrationGeneriqueGardeesTest` : tout contrôleur du classpath dont une route recouvre `/api/v1/admin/referentials/**` (y compris motif large) porte `@PreAuthorize` (méthode ou classe) ; auto-test de la garde sur 5 contrôleurs d'essai sans `@RestController`. Résultat : AUCUNE route sans garde (un seul contrôleur, `ReferentialAdminRestController`, garde de classe). `droitsSuffisants` refuse par défaut (403) sur ces routes (goldens inchangés) | `DatesIllisiblesDeLaDirectionAdviceTest` (rouge vérifié : 2 échecs) |
| 6 | 6 (info) | Motif ancré (`lookingAt`) sur le chemin sans contexte applicatif ; goldens inchangés | test du contexte `/app` et du fragment au milieu du chemin (écrit après le code : cas d'information) |

## Résiduels
| ID | Résiduel | Raison |
|---|---|---|
| RF5-R1 | Nom de caisse « à collation près » (KB-RF5-01) | la collation de la base n'est pas définie par le code ; pas de `Collator` |
| RF5-R2 | Cas `parMois` JDBC et Pilotage jamais vus rouges sur H2 | ordre incident du `GROUP BY` ; verrouillés par le contrat sur H2 ; l'IT PG16 hérite du même contrat mais n'a pas été rejoué (SQL inchangé) |
| RF5-R3 | Le test d'architecture ne couvre que les contrôleurs annotés `@RestController` / `@Controller` du classpath de la plateforme | les routes déclarées autrement (`RouterFunction`) ne sont pas vues |

## Points de la revue NON traités par RF4 ni RF5 (pour la revue globale `/ddd-verify`)
La revue complémentaire s'est arrêtée faute de tours (section « Non traité ») ; ces points restent À REVOIR :
1. Honnêteté des résiduels face au code : RF1-R1 et R2, RF2-R1 à R5, RF3b-R1 à R5, RF3c-J1 et R1 à R4, RF3d-R1 et R2. Seule RF2-R2 recoupée (l'entrée nommée existe).
2. Diffs de code RF1 (0081ed66) et RF2 (dec36883) : domaine pur, ports, ACL, cache et invalidation après commit. Non lus.
3. Lecteur Comptabilité de RF3b (`PayeurJpaContratTest` / `PayeurPostgresContractIT`). Non lu.
4. `TableDesPayeursTest` et §85 de `verifications-production.md` : hors périmètre de la revue complémentaire (RF4 a traité `TableDesPayeursTest`).

## Conséquences
- Retour arrière : revert Git. Aucun seam (`seamAllowed` reste `[]`), aucun flag. Aucune règle de gestion ne change (référentiel et documentation fonctionnelle non régénérés).

## Questions ouvertes
- Confirmation métier, avant le 2026-12-15, du compte d'usage borné au centre (ADR 0130) et des 400 de RF3d (ADR 0129).
- Collation souhaitée pour le nom de caisse en cas de code partagé (KB-RF5-01) : sans réponse, maintenir.
