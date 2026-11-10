---
status: proposed
date: 2026-11-10
sources: git show --stat / diffs ciblés des commits 0081ed66, d2a8fb5f, dec36883, fd242fc2, d9b0b9f6, 61707171, e4175177, a2e900b0 ; ADR 0126-0129 ; fiche patient-couverture-rf3-payeurs.md
---

# Revue indépendante RF1 à RF3d (patient-couverture) — lecture seule, aucun test/build/scan lancé

Verdict : **pass-with-actions** (aucun blocker vérifié). Revue PARTIELLE : voir « Non vérifié ».

## Points vérifiés conformes
- **Parité des goldens (2)** : `git show --name-status` des 8 commits : aucun golden `.approved.json` en statut M. Les seuls changements de golden sont des renommages R100 vers `_ecarts-legacy/..._avant_rf1c|rf2c|rf3d.legacy.json` (d2a8fb5f, fd242fc2, a2e900b0). RF1, RF2, RF3a, RF3b, RF3c : aucun golden modifié.
- **Sur place (1)** : les JDBC de RF3a interrogent les mêmes tables (`caisse_assurance`, `agence`, `centre_payeur`). Aucune façade, aucun flag, aucune projection dans les fichiers ajoutés.
- **Échantillon RF3a vs legacy** : tri `code, id` (legacy `t.code, t.id`, ReferentialAdminJdbcAdapter:143 à d9b0b9f6^) ; comptes d'usage identiques (legacy `Usage(agence,caisse_id)`, `Usage(centre_payeur,agence_id)`, `Usage(patients,centre_payeur_id)` contre `LectureDesPayeursJdbc.java:87-89`) ; message d'import « créez-le d'abord » identique (ReferentialValuesValidator:102 legacy) ; messages « 1 référence(s) : patients, prises en charge… » cohérents avec les goldens `saisie-caisses/suppressions`.
- **Hexagonal** : aucun import spring/jakarta/jdbc/infrastructure dans `domain`, `application` ou `publishedlanguage` de patient-couverture (grep). Langages publiés présents (`PayeursDuCentre`, `ForfaitsDuCentre`, `Transporteurs`). ACL minces dans les contextes consommateurs (`infrastructure/acl/patientcouverture/*`).
- **Maven** : pas de cycle. PC dépend de shared-kernel, organisation et planification (organisation dépend de stock et gmao ; planification d'organisation) ; comptabilite, facturation, seance et pilotage-direction dépendent de PC ; PC ne dépend d'aucun d'eux (poms).
- **Bornage centre dans les JDBC de PC** : `LectureDesPayeursJdbc.java:44,51,59` : jointure parent sur `r.center_id = t.center_id` et `WHERE t.center_id = ?`.

## Constats

| # | Sév. | Constat | Preuve | Correction proposée |
|---|---|---|---|---|
| 1 | major | Gabarit CASE dynamique : seul `'` est doublé dans le code de caisse. Le code est ensuite inséré dans un `<![CDATA[...]]>` JRXML. `ControleDeSaisieDePayeur` n'impose aucun jeu de caractères sur le code (50 car. max). Un code contenant `]]>` casse le XML ; un code contenant `$P{x}` ou `$P!{x}` est interprété par Jasper. L'impression de la synthèse (tout le centre) peut donc être rendue indisponible par un code saisi par un administrateur. Les jointures d'avant n'avaient pas cette surface. L'UUID est typé (`UUID.toString`), il est sûr. | `FacturationSyntheseMensuelleReportService.java:87-95,126` (échappement `'` seul) ; `ControleDeSaisieDePayeur.java` (`texte`, pas de motif) | Passer les codes en paramètres Jasper (`$P{...}` alimentés par la map) ou échapper `]]>`, `$` et `\` ; ajouter un test avec code `a]]>b` et `$P!{CENTER_ID}`. |
| 2 | minor | Le gabarit statique `reports/synthese_mensuelle_facturation.jrxml` (utilisé par `ModeleDocumentCatalog:36`) joint encore `agence` et `caisse_assurance`. `TableDesPayeursTest` ne scanne que les `.java` : ce lecteur n'est ni nommé ni daté, et l'ADR 0127 point 7 ne le note que comme « inchangé ». | `reports/synthese_mensuelle_facturation.jrxml:37-38` ; `TableDesPayeursTest.java:88` (fichiers `.java` seulement) | Ajouter le jrxml à `LECTEURS_CONNUS` avec raison et butoir, ou étendre la garde aux `.jrxml` et `.sql` de `src/main/resources`. |
| 3 | minor | Les butoirs des gardes sont du texte : le test n'impose que le préfixe `"2026-11-10 (RF3"`. Aucune date n'est comparée à l'horloge, donc 2027-03-31 passera sans alerte. De plus le filtre `/entity/` exclut les entités JPA de la garde. | `TableDesPayeursTest.java:139` (préfixe seul), `:88` (exclusion `/entity/`) | Extraire la date du butoir par regex et échouer après cette date ; restreindre l'exclusion aux 3 entités nommées. |
| 4 | minor | Comptabilité : la liste des payeurs charge tout l'annuaire du centre en mémoire à chaque page. Tri en Java `nom, code` (ordre naturel UTF-16) alors que le legacy faisait `ORDER BY nom, code` selon la collation de la base, sans départage par `id` : deux payeurs de même nom et même code ont un ordre dépendant de l'annuaire. Recherche LIKE réimplémentée par regex. Écart assumé en KB-RF3-06 (non revérifié ici). | `PayeursParLAnnuaire.java:46,51` ; legacy `PayeursDuCentreJdbc.java:48` à e4175177^ | Ajouter `.thenComparing(id)` au comparateur ; inscrire le volume maximal (nombre de centres payeurs par centre) dans l'ADR 0128. |
| 5 | minor | Dérogation `-Dcharacterization.approve=true` de RF3a non tracée. L'ADR 0126 affirme « goldens écrits et verts sur le legacy AVANT tout changement » sans mentionner la dérogation, alors que les ADR 0086-0120 écrivent « jamais approve=true » comme règle. Le grep de `approve` dans les ADR 0121-0129 et les fiches RF3 ne trouve rien. | `.ddd/architecture/adr/0126-*.md` (ligne « Parité ») ; absence de mention dans la fiche RF3 | Consigner la dérogation dans l'ADR 0126 ou la fiche (qui l'a décidée, pourquoi) ; l'échantillon vérifié ci-dessus réduit le risque. |
| 6 | minor | Des ADR déjà acceptés (0123, 0125, 0126, 0128) sont modifiés dans a2e900b0 pour solder des résiduels. C'est cohérent, mais l'ADR 0126 garde « 24 tests / 45 goldens, aucun `_nouveau_code` » alors que 9 goldens sont devenus `_nouveau_code` en RF3d. | `git show --name-status a2e900b0` ; ADR 0126 ligne « Parité » | Ajouter un renvoi daté vers l'ADR 0129 dans la section Parité de l'ADR 0126. |
| 7 | minor | Comptes d'usage des payeurs non bornés au centre (`WHERE caisse_id = ?` seul), alors que RF1c et RF2c ont borné ceux des transporteurs et forfaits. Identique au legacy ; le risque (fuite d'un autre centre, résiduel RF3b-R1) n'est pas couvert par une garde SQL. | `LectureDesPayeursJdbc.java:87-89` | Borner par `center_id` (ou documenter explicitement la non-correction dans un KB-RF3 avec décision). |

## Non vérifié (limite de tours)
- (4) Bornage au centre à chaque étage de RF3b (`PayeursDuCentreJdbc`, `ChaineDesPayeursParLAnnuaire`, `CaissesParLAnnuaireDesPayeurs`) et RF3b-R1.
- (4) ORDER BY déterministe des requêtes migrées de Séance et Pilotage.
- (5) Effectifs et butoirs de `TableDesTransporteursTest` et `TableDesForfaitsTest` (seule `TableDesPayeursTest` lue).
- (6) 403 prioritaire et absence de fuite des 400 de RF1c, RF2c et RF3d ; comptage borné. Cohérence ADR/RG/KB/glossaire avec le code.
- (7) §83-§84 de `verifications-production.md`.
- (8) Honnêteté des résiduels RF1-R1/R2, RF2-R1..R5, RF3-R1..R5, RF3b-R1..R5, RF3c-J1/R1..R4, RF3d-R1/R2 (seul RF3-R1..R5 lu dans l'ADR 0126).
- (2) Seule une partie des goldens rf3 est confrontée au legacy (suppressions, import ordonné, tri). Hiérarchie et modifications non confrontées.
- (1) RF1 et RF2 : revue des fichiers listés uniquement, pas de lecture des diffs de code.

## Top 3 actions
1. Constat 1 : corriger l'échappement CDATA/Jasper du gabarit CASE (correctif et test).
2. Constat 2 et 3 : couvrir le jrxml et rendre les butoirs exécutables dans les gardes `TableDes*`.
3. Constat 5 : tracer la dérogation `approve=true` de RF3a, puis compléter les points non vérifiés.
