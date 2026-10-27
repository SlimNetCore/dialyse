---
status: proposed
date: 2026-10-27
sources: git diff / untracked (base c270d40b), check-arch, goldens p6-semaine + gmao/pilotage, ADR 0060, fiche P6, seams.md, roadmap.md, known-bugs.md
---
# Revue indépendante : planification-p6-planning-semaine-lectures (refactoring sur place, ADR 0024)

Contrôles automatiques : `check-arch` **0 violation** (0 en baseline, 0 nouvelle). `scan --incremental` non relancé : la revue est en lecture seule et ce scan réécrirait `.ddd/scan/`. Aucun build ni test lancé, à la demande de l'appelant.

## Vérifié conforme (preuves)
- (a) Les 13 archives `_ecarts-legacy` (11 P6 + 2 GMAO) sont **identiques octet pour octet** à `HEAD:` (`cmp` sur chaque fichier). Écarts entre legacy et `_nouveau_code` : uniquement 500→400 (dates/centre illisibles), capacité 0 les jours fériés ou de fermeture exceptionnelle, sommeil exclu de la grille, EN_SERVICE seulement, `centresMasques` + centre masqué exclu du total. Rien d'autre ne change.
- (c) Absences et Direction excluaient déjà le sommeil dans le legacy (ancien SQL de `PatientsAttendusJdbc` et `FILE_ACTIVE` dans `HEAD`). La présence unique ne change donc rien pour KB-ABS. `date_admission NOT NULL` (`V1__baseline_schema.sql:1053`) : le cas « admission nulle = sans borne » ne peut pas se produire. La règle de sortie est équivalente (`FinDOccupation.java:25-35`). L'ordre des attendus est conservé (`ConsulterLaPresenceDesPatients.java:28`).
- (g) Toutes les éditions et suppressions legacy (`DirectionCapaciteQueryService`, `DirectionGmaoQueryService`, `DatesIllisiblesDeLaDirectionAdvice`, `SeancePlaceJdbcAdapter`, `PlanningSemaineRestController`, `GenerateurImpact*`) figurent dans `covered.json`. `seamAllowed: []` (`covered.json:380`). Absences n'importe que `publishedlanguage`.
- Les droits sont inchangés (`SemaineControleur.java:29` = le `@PreAuthorize` de HEAD). La semaine prend la date par défaut au fuseau du centre (`HorlogeDesCentres`).
- (i) Aucun BOM ni mojibake dans les fichiers modifiés ou nouveaux. Les 4 i18n sont des JSON valides. Les dates d'en-tête sont 2026-10-27, égales à la date système.
- Le correctif de `ClotureMensuelleDesAbsencesNouveauCodeIntegrationTest` est sain : la regex ancrée sur une valeur JSON entière `"AAAA-MM"` ne capture plus de fragments d'UUID.

## Constats

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | archi/legacy (anonymat) | **Le masquage reste incomplet (violation de l'intention de KB-PLN-75, « corrigé »).** Pour un centre masqué, `ligne()` met `fileActive` et `tauxOccupation` à null, mais publie encore `niveau`, `atteinte` et `capacite`. Ces trois valeurs bornent la file masquée. Exemple du golden : capacité 2 + `ATTEINTE` donne une file de 2 à 4 ; capacité 4 + `ATTEINTE` donnerait exactement 4. Le défaut existait déjà dans le legacy, mais n'est pas consigné. | `DirectionCapaciteQueryService.java:57-61` ; `reponse_complete_nouveau_code.approved.json:24-35` | Décision utilisateur, puis nouvel écart : `niveau`/`atteinte` à null (ou `MASQUE`) quand la file est masquée ; golden + KB. |
| minor | stratégique | **Suggestion :** le total exclut aussi la capacité et les générateurs des centres masqués (total 16 au lieu de 24 générateurs). Le chiffre « capacité de la société » change donc au-delà de ce que l'anonymat exige. C'est cohérent avec la note UI, mais il faut le confirmer comme décision. | `DirectionCapaciteQueryService.java:86-102` ; `DirectionCapaciteIntegrationTest` diff (16/84) | Faire confirmer explicitement dans l'ADR 0060 (« total = centres publiés ») ou séparer la capacité (non sensible) de la file. |
| minor | legacy | **Trois règles de présence coexistent** : module (sommeil exclu), `SemaineDeLInfirmierLegacy` (sommeil inclus jusqu'à P8), `SeancePlanningJdbcAdapter` (règle Séance jusqu'à P9). L'infirmier voit des patients en sommeil que le secrétariat ne voit plus. Les jalons sont consignés, donc c'est acceptable, mais rien n'est prévu en vérification de production. | `roadmap.md` « Dette de P6 » ; `seams.md:162` | Ajouter un contrôle à `verifications-production.md` et une ligne de risque utilisateur dans la fiche ; tenir le jalon P8. |
| minor | tactique/UX | **Suggestion :** un patient en sommeil disparaît de la grille mais tient sa place (P5 refuse ensuite la case). La grille montre donc une place libre qui ne l'est pas. | `SemaineDuPlanning.java:120-122` ; `SejourDuPatient.java:9-10` | Afficher un compteur « places tenues par des patients en sommeil » (P8) ou le documenter dans la doc fonctionnelle. |
| minor | archi/perf | `PresenceDesPatients` (`attendusLe`, `fileActive`) et `PatientsAuxGenerateurs` rechargent **tous** les patients du centre, avec en plus une lecture du dossier médical (`patientsARisque`), à chaque appel : une fois par jour et par centre pour la détection d'absences, une fois par centre pour la Direction. C'est une lecture de données médicales inutile pour un simple comptage. | `SejoursDesPatientsJdbc.java:40-43` ; `ConsulterLaPresenceDesPatients.java:24-35` ; `CompterLesPatientsAuxGenerateurs.java:25` | Prévoir une variante `SejoursDesPatients` sans risque infectieux, ou un chargement paresseux. |
| minor | archi (fuseau) | (f) La fin de période par défaut est au fuseau global `app.planification.fuseau-par-defaut`, mais la coupure des générateurs est à minuit **UTC**. Résultat : une fenêtre d'1 h d'écart (un générateur installé tôt le lendemain, heure locale, est compté). La décision « fuseau du centre » n'est appliquée qu'à la semaine. | `DirectionCapaciteQueryService.java:50,65,83` | Utiliser `end.plusDays(1).atStartOfDay(fuseau).toInstant()` ; consigner le choix du fuseau global pour une société dans l'ADR. |
| minor | tactique | `DirectionGmaoQueryService` code en dur la liste des statuts « indisponibles » de GMAO, alors que GMAO publie déjà `IndisponibilitesDesGenerateurs` (f0552486). Écart non testé : l'ancienne jointure comptait un patient rattaché au générateur d'un **autre** centre, la nouvelle (`duCentre(centreId)`) ne le compte plus. | `DirectionGmaoQueryService.java:36-37,117-120` | Consommer le port GMAO publié ; ajouter un scénario ou un KB pour le cas inter-centres. |
| minor | docs | Incohérences : `roadmap.md:160` liste `DirectionGmaoQueryService` comme restant (il est fait, `seams.md:156`), indique « 11 goldens » au lieu de 13 et garde le statut « à planifier » alors que la slice est construite. `seams.md:162` met le retrait de `SalleVueJdbcAdapter` à « fin de P6 session 2 », alors que `roadmap.md:173` dit P9. `ADR 0060:46` indique « 11 goldens » sans les 2 GMAO. | lignes citées | Aligner les trois documents. |
| nit | tactique | `ConsulterLaBaseDeLaCapaciteTheorique` utilise une exception (`SaisieInvalide`) comme contrôle de flux pour retomber sur la valeur par défaut. | `ConsulterLaBaseDeLaCapaciteTheorique.java` (`duCentre`) | Méthode `patientsParPosteOuDefaut()` côté repository. |
| nit | tests | L'ordre des patients dans le conflit `GENERATEUR_MIXTE` dépend de l'itération des HashMap/HashSet (fidèle au legacy, mais fragile). | `SemaineDuPlanning.java:282-296` | Tri explicite lors d'un prochain écart décidé. |
| nit | tests | Les 2 goldens GMAO sont modifiés sur place, sans suffixe `_nouveau_code`, contrairement à la convention P6 (ils sont quand même archivés). | `gmao/pilotage/direction_capacite_*.approved.json` | Accepté, à mentionner dans la fiche (c'est déjà le cas). |
| nit | docs/i18n | `kab.json` `MASKED_NOTE` est en anglais (même chose que le précédent Absences, `kab.json:442`). | `frontend/public/i18n/kab.json` | Traduction kabyle à demander. |
| nit | tests | (h) `DirectionCapaciteIntegrationTest` utilise l'horloge réelle (`:43,62,82,151`). Les périodes sont passées explicitement, donc les assertions ne dépendent ni de minuit ni du jour du mois. Le défaut était déjà présent. | lignes citées | Facultatif : dates fixes. |

## Verdict : **pass-with-actions**
Compte : blocker 0 · major 1 · minor 7 · nit 5.

Top 3 actions :
1. Masquer aussi `niveau`/`atteinte` d'un centre masqué : décision utilisateur, golden et KB (major).
2. Aligner `roadmap.md:160`, `seams.md:162` et `ADR 0060:46` ; faire confirmer « total = centres publiés » dans l'ADR 0060.
3. Retirer la lecture du dossier médical du comptage de présence ; mettre la coupure des générateurs au même fuseau que la période.
