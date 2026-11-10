# Revue complémentaire RF1-RF3d (2026-11-10) — lecture seule, partielle

Verdict : pass-with-actions (aucun blocker vérifié). Limite de tours : points (5) et (6) NON traités (voir fin).

## Conformes (vérifiés)
- Bornage RF3b : `PayeursDuCentreJdbc.java:22-24` borne les jointures (`ag.center_id = cp.center_id`, `ca.center_id = cp.center_id`) ; `:34,:41,:48,:56,:67` filtrent `center_id = ?`. ORDER BY déterministes (`code, id` / `cp.code, cp.id`) aux lignes 34, 41, 48, 61. ACL Séance et Pilotage minces, sans logique (`ChaineDesPayeursParLAnnuaire.java:23-37`, `CaissesParLAnnuaireDesPayeurs.java:22-31`).
- §83 et §84 de verifications-production.md (lignes 2653-2684) : SELECT seul, aucun UPDATE, jointures et filtres `center_id <>` justes, `IN (a, b)` ne double pas les lignes, mention honnête « non exécutée en production ».
- Advice 400 : le 403 prioritaire est évalué via `@PreAuthorize` (`DatesIllisiblesDeLaDirectionAdvice.java:89,128-139`) pour l'administration générique et les stats ; messages 400 génériques (pas de fuite de message Spring, `:85,:77`).

## Constats

| # | Sév. | Constat | Preuve | Correction |
|---|---|---|---|---|
| 1 | minor | `parCaisse` (Séance et Pilotage) et `parMois` : `GROUP BY` sans `ORDER BY`. L'ordre de la liste publiée suit l'ordre de groupement du SQL (LinkedHashMap sur l'ordre de lecture), donc non déterministe. RF3b-R1 « ORDER BY déterministes » n'est tenu que côté Patient et couverture. | `SeancesRealiseesDesCentresJdbc.java:72,89-92,98,110` ; `PatientsDesCentresJdbc.java:81-83,90,102` | Trier la liste finale (centre, code) en Java, ou documenter que l'appelant trie. Ajouter un test à 2 caisses insérées dans l'ordre inverse. |
| 2 | minor | Le « plus grand nom » est calculé par `String.compareTo` (UTF-16), alors que le `MAX(ca.nom)` d'avant suivait la collation PostgreSQL. Divergence possible avec accents ou casse, annoncée « comme avant » dans la javadoc. | `SeancesRealiseesDesCentresJdbc.java:82,107` ; `PatientsDesCentresJdbc.java:73,99` | Utiliser un `Collator` fr, ou corriger la javadoc (« à collation près ») et ajouter un golden avec accent. |
| 3 | minor | `TableDesTransporteursTest` et `TableDesForfaitsTest` : même défaut que le constat 3 de la revue partielle. La date du butoir est du texte libre, sans échéance exécutable. L'exclusion `/entity/` laisse passer les entités JPA. L'effectif est figé (0 et 1, `hasSize`), mais l'entrée unique de RF2-R2 n'a pas de butoir. | `TableDesTransporteursTest.java:63,107` ; `TableDesForfaitsTest.java:42,72,116` | Extraire la date par regex et échouer après l'échéance ; restreindre `/entity/` aux entités nommées ; fixer un butoir pour `ForfaitsDeSeanceJdbc`. |
| 4 | minor | `ErreursDesPayeursAdvice` traduit toute `IllegalArgumentException` du contrôleur en 400 avec `getMessage()`. Un message interne (conversion, SQL, domaine) peut sortir tel quel. Pour `centerId` absent, le 400 de `obligatoire()` précède le contrôle de centre (`centreDeLaLecture`) : un utilisateur authentifié reçoit 400 et non 403, sans fuite de données. | `ErreursDesPayeursAdvice.java:20-23` ; `PayeursControleur.java:33-34,53-54` | Utiliser une exception dédiée (ex. `ParametreObligatoireAbsent`) ou un message fixe. |
| 5 | minor | `droitsSuffisants` renvoie `true` (donc 400) si le handler est introuvable ou sans `@PreAuthorize`. Pour l'administration générique, le 403 prioritaire repose donc sur la présence de l'annotation, sans garde-fou. Aucune vérification que tous les contrôleurs de `/admin/referentials/**` portent `@PreAuthorize`. | `DatesIllisiblesDeLaDirectionAdvice.java:129-132` | Ajouter un test d'architecture : tout `HandlerMethod` sous `/admin/referentials` porte `@PreAuthorize`. Ou refuser (403) par défaut si absent sur ces routes. |
| 6 | info | Le motif `ADMINISTRATION_GENERIQUE` est testé avec `find()` sur l'URI complète : un chemin contenant ce fragment ailleurs (ex. contexte applicatif préfixé) serait aussi reconnu. Sans risque connu aujourd'hui. | `DatesIllisiblesDeLaDirectionAdvice.java:54-55,113` | Utiliser `matches` ou `startsWith` après retrait du contextPath. |

## Non traité (limite de tours)
- (5) Honnêteté des résiduels RF1-R1/R2, RF2-R1..R5, RF3b-R1..R5, RF3c-J1/R1..R4, RF3d-R1/R2 face au code. Seule RF2-R2 recoupée : l'entrée nommée existe bien (`TableDesForfaitsTest.java:40-45`).
- (6) Diffs de code RF1 (0081ed66) et RF2 (dec36883) : domaine pur, ports, ACL, cache et invalidation après commit. Non lus.
- (1) Lecteur Comptabilité de RF3b (`PayeurJpaContratTest` / `PayeurPostgresContractIT`) non lu.
- TableDesPayeursTest, §80 : hors périmètre.

## Top 3 actions
1. Constat 1 : rendre l'ordre des listes `parCaisse`/`parMois` déterministe (tri final + test).
2. Constat 3 : butoirs exécutables dans les trois `TableDes*Test`.
3. Constat 5 : garde-fou test sur `@PreAuthorize` des routes `/admin/referentials/**`, puis traiter (5) et (6).
