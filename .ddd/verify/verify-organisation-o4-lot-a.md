---
status: proposed
date: 2026-11-09
scope: slice organisation-o4-lectures-publiees, lot A (commit e0fc26a0, ADR 0107), refactoring sur place (ADR 0024)
sources: [git show HEAD, HEAD~1 (9 lecteurs supprimes + JourDuCentreLegacy), AnnuaireDesCentresJdbc, AnnuaireDesSocietesJdbc, traducteurs, AnnuairesContrat, TableDeLOrganisationTest, poms, ADR 0107, fiche O4, audit, seams.md, covered.json, glossaire, V1__baseline_schema.sql]
reviewer: relecteur independant (lecture seule ; un build Maven a la fois)
---

# Revue O4 lot A : annuaires des centres et des societes publies

## Verdict : **pass-with-actions**
0 blocker, 0 major, 3 minor, 3 nit. Les 10 lecteurs sont fideles a l'ancien SQL (texte identique, defauts de fuseau et gestion d'exception conserves chez chaque consommateur). Aucun `.approved` touche. Aucun cycle de modules.

## Controles
| Controle | Resultat |
|---|---|
| Fidelite SQL (point 1) | Les 9 requetes de `AnnuaireDesCentresJdbc` / `AnnuaireDesSocietesJdbc` sont identiques au caractere pres a celles de HEAD~1. Actifs : `COALESCE(actif, TRUE) = TRUE ORDER BY id` (Absences, Planification : identique). Tous : `SELECT id FROM centers`, sans tri ni filtre (Dossier medical, Seance : identique ; `UUID.fromString(getString)` devenu `getObject(UUID.class)`, equivalent sur colonne uuid). Fuseau : meme requete, meme filtre `null`/blanc, meme `trim`, `DateTimeException` -> vide = ancien `fuseauParDefaut`. Chaque traducteur applique `orElse(fuseauParDefaut)` avec SON defaut injecte (Planification, Patient-couverture, Seance, plateforme) ; centre nul : Seance garde son garde-fou (`JourDuCentrePublie.fuseauDe`), Planification/Patient passent `centre.valeur()` comme avant. Societes : `societes WHERE actif = TRUE` (NULL exclu, comme avant) ; societe d'un centre inconnu = vide ; societe inconnue = `EmptyResultDataAccess` -> vide -> `SocieteInconnue` (meme exception) ; centres par nom, `COALESCE(actif, TRUE)`. Aucun ecart non declare. |
| Plateforme `JourDuCentreLegacy` | `centres.fuseauHoraireDe(c).map(ZoneId::getId)` puis re-parse `ZoneId.of` : aller-retour sans perte (ZoneOffset, ids regionaux) ; `JourDuCentreLegacyTest` inchange et vert. |
| Contrat et tests sans filet (point 2) | `AnnuairesContrat` fixe : NULL = actif, desactive exclu de `actifs` mais inclus dans `tous`, fuseau absent / blanc / illisible / centre inconnu = vide, tri par id et par nom. Il couvre donc les trois comportements sans test direct (Dossier medical = tous ; Patient-couverture horloge = defaut sur vide ; Seance CentresARappeler = tous). Le contrat tourne sur le nouvel adapter (H2) ; comme le SQL est textuellement identique a l'ancien, il fixe de fait l'ancien comportement. Rejeu sur worktree HEAD~1 : non fait (aucun test equivalent n'existe contre l'ancien code ; en ecrire un aurait exige du code hors rapport). Voir n1. |
| Graphe Maven (point 3) | `contexts-organisation` depend de shared-kernel, stock, gmao seulement (`pom.xml:79,87,92`) ; stock et gmao ne dependent que de shared-kernel ; les 6 consommateurs et la plateforme ajoutent `contexts-organisation`. Preuve par lecture des poms et reacteur construit. Fuite de types : le code de production des consommateurs n'importe que `publishedlanguage` (grep) ; les tests importent `AnnuaireDesCentresJdbc` (acceptable). `FrontieresDesContextesTest:535` (regle Organisation hors langage publie) verte. Glossaire : 4 termes `proposed`, noms conformes (`AnnuaireDesCentres`, `Centre actif`, `Fuseau horaire du centre`). |
| Garde Organisation (point 4) | Liste blanche : 32 -> 22 (10 entrees retirees = les 10 lecteurs migres), un test fige `hasSize(22)`, `aucune_entree_morte` conserve. La regex `LECTURE_SQL` s'applique au texte entier du fichier, commentaires compris : elle sur-detecte, elle n'est pas aveugle. Aucun Javadoc a SQL (FROM centers) restant dans les fichiers migres. Risque inverse : un Javadoc contenant `FROM centers` garderait vivante une entree dont le SQL a disparu (voir n2). |
| Docs (point 6) | ADR 0107, fiche (plan A..E, residuels O4-R1..R7), glossaire et roadmap coherents entre eux et avec le code (10 lecteurs, 22 restants, Stock non migre). `covered.json` JSON valide, `seamAllowed` `[]`. `seams.md` : edition de `JourDuCentreLegacy` (deja couvert) consignee. Les 8 fichiers Organisation crees (dont ceux ecrits par heredoc) : CRLF coherent avec `core.autocrlf=true`, pas de BOM, pas de U+FFFD, finissent par un saut de ligne. |
| `.approved` et tests consommateurs (point 5) | `git show --stat HEAD` : aucun `.approved`. Diff des tests consommateurs : uniquement imports et constructions (`new CentresActifsPublies(new AnnuaireDesCentresJdbc(jdbc))`) ; assertions identiques. `JourDuCentrePublieTest` : meme scenarios, annuaire de test qui replique la traduction (fuseau lisible sinon vide). `CentresARappelerPubliesTest` : assertions identiques. |
| Tests (point 7) | Voir section « Execution » ci-dessous. |

## Constats
| # | Severite | Constat | Preuve | Correction |
|---|---|---|---|---|
| m1 | minor | O4-R3 : `centers.actif` est `boolean` NULLABLE sans defaut en schema ; Stock exclut NULL (`actif = TRUE`), Absences/Planification/Pilotage incluent, `CentresJdbc` lit NULL comme faux. Divergence reelle tant qu'une ligne NULL existe. | `V1__baseline_schema.sql:509` (`actif boolean,`) ; `CentresActifsLegacy` (Stock) `WHERE actif = TRUE` ; `CentresJdbc.java:24` (`getBoolean`) | Lot B : voir recommandation. |
| m2 | minor | O4-R4 (`CentresJdbc` NULL = faux) laisse deux sens de « actif » dans Organisation meme apres publication de `actifs()` (NULL = actif). Le glossaire le note, la fiche aussi. | `CentresJdbc.java:24` ; `AnnuaireDesCentres.java` (`actifs`) | Au lot B, aligner `CentresJdbc` ou renseigner la donnee (UPDATE NULL -> TRUE valide par l'utilisateur) et ajouter `NOT NULL DEFAULT TRUE`. |
| m3 | minor | Aucune execution du contrat sur PostgreSQL 16 (H2 seulement) alors que `ORDER BY id` sur uuid et `COALESCE` different peu mais ne sont pas prouves ; O4-R7 le consigne. | fiche O4-R7 | Variante `*PostgresContractIT` au lot B (comme prevu). |
| n1 | nit | Trois lecteurs sans test direct avant migration : parite fondee sur l'identite du SQL, pas sur un rejeu legacy ; honnetement declare dans l'ADR. | ADR 0107 « Consequences » | Aucune. |
| n2 | nit | Garde par regex sur le texte brut : un commentaire `FROM centers` peut maintenir une entree « vivante ». | `TableDeLOrganisationTest.java:73-74` | Retirer les commentaires avant le match (lot B). |
| n3 | nit | `JourDuCentreLegacy` (plateforme) ne contient plus de SQL mais garde son nom ; import `AnnuaireDesCentres` insere entre imports Spring. Acceptable : O4-R1 documente (renommage = nouveau fichier hors `covered.json`, accord utilisateur requis). | `JourDuCentreLegacy.java:6` ; `seams.md` | Renommer apres accord (O4-R1). |

## O4-R3 : analyse et recommandation pour le lot B
- Fait : un centre a `actif` NULL est inclus par Absences, Planification (nuit, liberation), Pilotage (centres de la societe), Dossier medical et Seance (tous) mais exclu des peremptions de Stock ; la colonne l'autorise (`V1:509`).
- Inconnu (non verifiable en lecture seule sans base) : s'il existe des lignes NULL en production. L'application ecrit `actif` via `CentresJdbc` (`c.actif()` primitif), donc l'ecriture applicative ne produit pas de NULL ; seuls un import SQL ou le seed pourraient en creer (le seed demo renseigne `actif`).
- Verification SELECT-only a faire passer par l'utilisateur sur une copie ou en lecture seule (aucun script existant dans `.ddd/safety-net`, non ajoute ici) : `SELECT count(*) FILTER (WHERE actif IS NULL) AS nuls, count(*) FILTER (WHERE actif) AS actifs, count(*) FILTER (WHERE NOT actif) AS inactifs FROM centers;`
- Recommandation : (a) si nuls = 0 : migrer Stock sur `actifs()` via port `*Fournis` en acceptant l'equivalence, et poser `NOT NULL DEFAULT TRUE` plus tard (decision utilisateur, DDL) ; (b) si nuls > 0 : ne pas changer la semantique de Stock au lot B ; exposer `actifsStrict()` ou garder un lecteur nomme avec KB-ORG-xx et decision metier (reproduire ou corriger) avant le 2026-12-15. Dans les deux cas : un test de parite NULL par consommateur.

## Execution (point 7)
Voir « Suites données » pour les resultats des suites.

## Suites données (2026-11-09)
Revue lecture seule : aucune correction appliquee par le relecteur. Actions recommandees : m1/m2 (SELECT de controle puis decision lot B), m3 (IT PostgreSQL), n2 (garde sans commentaires), n3 (O4-R1 apres accord).

### Resultats d execution (complement du point 7)
- 7 modules (organisation, planification, patient-couverture, absences-patients, seance, dossier-medical, pilotage-direction) : BUILD SUCCESS en ordre par defaut et avec -Dsurefire.runOrder=random.
- Plateforme filtree (TableDeLOrganisationTest 5, FrontieresDesContextesTest 47, ReglesDeGestionDocumentationTest 7, JourDuCentreLegacyTest 4 = 63) : BUILD SUCCESS par defaut et en random.
- Non execute : demarrage complet du contexte Spring de la plateforme (cablage de AnnuaireDesCentres dans JourDuCentreLegacy) ; rejeu du contrat sur HEAD~1 (aucun test equivalent contre l ancien code ; parite fondee sur l identite textuelle du SQL).
