---
slice: seance-se7c1-lectures-stock
date: 2026-11-08
reviewer: relecteur indépendant (lecture seule)
verdict: pass-with-actions
---

# Revue SE7c1 (lots A, B, C)

Contrôles automatiques : `check-arch` = 0 violation (0 baseline, 0 nouvelle). Aucun test exécuté (lecture seule).

| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| minor | tests | `TableDesSeancesTest` ne vérifie pas le COMPTE de la liste blanche (18) : seuls les butoirs, les entrées mortes et les nouveaux lecteurs sont gardés. Le « 18 » n'existe que dans les docs. | `backend/platform/src/test/java/com/hemodialyse/backend/architecture/TableDesSeancesTest.java:121-129` (aucun `hasSize`) | Ajouter `assertThat(LECTEURS_CONNUS).hasSize(18)` (ou retirer l'affirmation « vérifie le compte » des docs). |
| minor | tests | Regex de détection `(FROM\|JOIN\|INTO\|UPDATE\|TABLE)\s+nom` : manque `FROM a, seances`, `public.seances`, et les ressources (`.jrxml`, `.sql` hors `src/main/java`). Suggestion, pas une violation. | `TableDesSeancesTest.java:84-85`, `:99-106` (seulement `src/main/java`) | Documenter la limite dans le Javadoc ou élargir la regex (`,\s*seances`, préfixe de schéma). |
| minor | legacy | R3 a pour butoir « avant toute mise en production », sans date, alors que les autres ont une date. Cohérent avec le sens mais non testable. | `.ddd/migration/slices/seance-se7c-lectures-facturation-stock.md:40` | Ajouter une date butoir (ex. 2027-01-31) ou un critère d'activation écrit. |
| minor | archi | `LecturesPublieesDesSeancesJdbc` fait 3 requêtes par appel à `de()` (séance, salle, créneau) : fidèle à l'ancien code, mais l'ancien code était appelé dans la même transaction JDBC ; aucun défaut observable, perf identique. À noter seulement. | `backend/contexts/seance/.../infrastructure/persistence/LecturesPublieesDesSeancesJdbc.java:37-44` | Aucune. |
| nit | docs | Le message de commit ff8c1466 annonce « 19 lecteurs » ; le code, la fiche, l'annexe 16 et l'ADR disent 18 (rectifié). Pas de trace corrective dans l'historique. | `git log ff8c1466` vs `TableDesSeancesTest.java:49-74` | Aucune (historique git), le compte rectifié est cohérent partout. |

## Réponses aux 9 points

1. **Hexagonal / cycle Maven** : OK. Ports dans `seance/publishedlanguage` (interfaces pures, nom métier), adapter dans `seance/infrastructure/persistence`, bean dans `SeanceConfiguration` (composition). Traducteurs `@Component` en `platform/.../infrastructure/acl/stock/`, qui implémentent les ports du Stock et appellent seance.v1 : pattern ACL côté aval respecté. Le Stock ne dépend d'aucune classe Séance (règle `le_stock_ne_depend_pas_de_la_seance`, `FrontieresDesContextesTest`, ajoutée en ff8c1466) ; aucun pom modifié dans les 3 commits, donc pas de cycle. Placement en plateforme justifié (le module Seance dépend de Stock) et enregistré comme résidu R2.
2. **Fidélité** : identique à l'ancien SQL, comparaison ligne à ligne avec `git show ff8c1466` (anciennes classes). Séance inconnue ou d'un autre centre : `Optional.empty()` donne `PerimetreDeSortie.journee()` (avant : liste vide donne `journee()`) ; `estFacturee` faux (même requête `id AND center_id`). Salle nulle ou d'un autre centre : `codeSalle` nul (même requête `salle … AND center_id`). Créneau nul : demi-journée nulle ; créneau d'un autre centre : avant `texte=""` donne nul, maintenant `simplifier(null)+" "+simplifier(null)=" "` donne nul. Matin/après-midi/midi/soir : `demiJournee` reprend l'ordre APRES/MIDI/SOIR avant MATIN et la normalisation NFD à l'identique (l'ancien concaténait `code + " " + libelle`, idem). Casse de FACTUREE (KB-SEA-70) : `equalsIgnoreCase` conservé dans l'adapter. Aucun écart trouvé.
3. **SQL résiduel sur `seances` dans le Stock** : aucun (`grep` `FROM|JOIN|UPDATE|INTO seances` dans `backend/contexts/stock/src/main` : 0 ; test `le_contexte_stock_ne_lit_plus_la_table_des_seances`). Les deux classes `*Legacy` et leurs 2 beans sont supprimés (`StockContexteConfiguration`) ; les références restantes sont dans des docs/commentaires historiques.
4. **Seam** : `covered.json` `seamAllowed: []` (ligne 217) ; ajout des 2 chemins exacts `platform/.../infrastructure/acl/stock/{StatutDesSeancesParLaSeance,PerimetreDesSeancesParLaSeance}.java` ; `seams.md` lot B inscrit avant action, accord coordinateur cité, fermé. Conforme à `20-legacy-safety.md` ; aucun édit legacy hors périmètre (les modifications de tests sont hors `legacyPaths`).
5. **§61** : 7 requêtes SELECT seul, aucune écriture ; colonnes cohérentes (`salle_id`, `creneau_id`, `facture_id`, `statut`, `center_id`, `salle`, `position_creneau`) ; texte identique, même ordre 1 à 7, aux 7 requêtes du script `backend/scripts/seance-se7c1-pg16-rejeu.sh` (comparaison à l'œil, chaîne par chaîne) ; le script vérifie en plus les types de colonnes de l'adapter et exécute les 4 requêtes de l'adapter. Sémantique des commentaires exacte (req. 6 : Stock refuse car statut = FACTUREE ; req. 7 : statut différent, Stock croit modifiable). Exécution réelle sur PG16 non rejouée par ce relecteur.
6. **7 résidus** (R1 à R7, fiche `:36-45`) : tous revus 2026-12-15, butoirs propres sauf R3 (voir minor). Le test impose `butoir > REVUE` pour chacun des 18 lecteurs (`TableDesSeancesTest.java:134-141`). Décomposition R4 5 + R5 7 (3+1+1+2) + R6 6 (3+1+1+1) = 18.
7. **Audit c2** : exact et complet. Les 5 fichiers de Facturation correspondent à la liste blanche (`TableDesSeancesTest.java:52-56`) ; `SeancesDeFacturationJdbc` = 1 SELECT + 4 UPDATE (`:34`, `:48`, `:55`, `:62`, `:70`) ; `lier` = 1 UPDATE (`LiaisonDesSeancesJdbc.java:27`) ; la synthèse a bien 3 requêtes (`LectureDeLaSyntheseJdbc.java:56,72,87`) et le tableau de bord 3 (`LectureDuTableauDeBordJdbc.java:40,55,70`). KB-SEA-47 vérifiée (aucun `forfait_override_code` dans l'UPDATE de `poserLeForfait`, `SeancesDeFacturationJdbc.java:70-72`) ; KB-FAC-17 vérifiée (`statut = ?` / `IN ('VALIDEE','SIGNEE')` sensibles à la casse). Les deux définitions de « facturée » (tableau de bord par `statut='FACTUREE'`, synthèse par `facture_id`) sont réelles. Recommandations c2a (lectures + 4 UPDATE gardés `facture_id IS NULL` : vrai pour exclure/réinclure/forfait ; `lierALAbsence` n'a PAS cette garde mais exige `statut='ABSENT'`, d'où « tous gardés » légèrement imprécis, `SeancesDeFacturationJdbc.java:55-57`) puis c2b (`lier` seul, test de course PG16) sensées. Imprécision d'audit : à corriger en une phrase dans `.ddd/safety-net/seance-se7c2-ecritures-facturation.md` (c2a : « tous gardés par `facture_id IS NULL` » est faux pour `lierALAbsence`). Classée minor.
8. **Horloge** : aucune occurrence de `now()`, `Clock`, `currentTimeMillis` dans les tests SE7c1 (caractérisation, traducteur, contrats du module Seance). `TableDesSeancesTest` utilise des `LocalDate.of` fixes. OK.
9. **Compte « 18 dont 8 de plateforme »** : cohérent : test (5 Facturation + 3 absences + 1 planification + 1 dossier-médical + 8 plateforme), fiche (`:24`, `:41-43`), annexe 16 (`:125`). L'ADR et §61 n'avancent pas de chiffre contradictoire (grep « 17|19 lecteurs » : 0). Seule trace de « 19 » : message de commit (nit).

## Verdict : pass-with-actions

Blockers : 0. Majors : 0. Minors : 4 (dont la précision de l'audit c2 ci-dessous). Nit : 1.

Top 3 actions
1. Ajouter l'assertion du compte (18) dans `TableDesSeancesTest` (ou nuancer les docs).
2. Corriger dans l'audit c2 la phrase « 4 UPDATE tous gardés par `facture_id IS NULL` » (`lierALAbsence` : garde par statut `ABSENT` uniquement).
3. Dater le butoir de R3 (décisions KB-SEA-68/69/70) avant la mise en production.
