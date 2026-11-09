---
status: proposed
date: 2026-11-09
scope: slice organisation-o4-lectures-publiees, lots B a E (ad8f82df ADR 0108, fedd55e7 ADR 0109, 48f400b4 ADR 0110, 486f89bd ADR 0111), refactoring sur place (ADR 0024)
sources: [git show de chaque commit et de son parent, adapters JDBC d'Organisation, traducteurs Stock/Planification/plateforme, TableDeLOrganisationTest, covered.json, seams.md, known-bugs KB-ORG-09/10, verifications-production §72/§73, fiche O4, ADR 0108-0111]
reviewer: relecteur independant (lecture seule ; un build Maven a la fois)
---

# Revue O4 lots B a E : Stock, libelles, Planification, identite imprimee

## Verdict : **pass-with-actions**
0 blocker, 0 major, 4 minor, 4 nit. Les 15 lecteurs sont fideles a l'ancien SQL (verifie texte contre texte via `git show <commit>^`). O4 peut passer a `built` avec les 7 residuels nominatifs ; les actions ci-dessous ne bloquent pas, mais m1 et m2 doivent etre tracees avant la bascule.

## Controles
| Controle | Resultat |
|---|---|
| Lot B, Stock (4 lecteurs) | `actifsAuSensStrict` = `SELECT id FROM centers WHERE actif = TRUE ORDER BY id`, identique a `CentresActifsLegacy` (HEAD~3^). `tous()` = `SELECT id FROM centers` = ancien `OperationsDeStockJdbc.centres()`. Horloge : ancien (filtre null/blanc, `trim`, `DateTimeException` -> UTC) = `fuseauHoraireDe(...).orElse(UTC)` (SQL valide au lot A). Verrou : `SELECT id FROM centers WHERE id = ? FOR UPDATE` identique, via `VerrouDuCentreFourni` -> traducteur plateforme -> `VerrouDuCentre`, meme `JdbcTemplate`/DataSource donc meme transaction. KB-ORG-09 et §72 : coherents avec le code (variante stricte nommee, a confirmer au 2026-12-15). |
| Lot C, libelles (3 lecteurs) | Seance : `salle.code` et `position_creneau.code,libelle` bornes au centre, identiques ; `null` si inconnu (le `code` NULL n'est pas insere -> meme `null`). Patients : libelle medecin `TRIM(COALESCE...)`, blanc exclu, `trim` ; creneau `code,libelle` ; recherches medecin/creneau identiques caractere pour caractere (expressions et `LIKE ... OR LOWER(CAST(id ...))`). Mouvements : LEFT JOIN sur cle primaire sans borne -> deux `IN (:ids)` sans borne ; pas de doublon possible, ordre/pagination restent dans la requete principale (`ORDER BY ... LIMIT ? OFFSET ?` inchange), ids NULL ignores (`distincts`), cle du Map = id, nom NULL -> absent -> `null` comme l'ancien. KB-ORG-10 reproduit (non borne), §73 present. |
| Lot D, Planification (6 lecteurs) | `salle` (`id` seul, `isolement = 'OUI'`), `ORDER BY code`, page `LIMIT ? OFFSET ?` + `COUNT(*)`, `salleDuCentre`, creneaux `ORDER BY code` + rang, `COUNT(*)` creneaux/serie : texte identique. Commande `definir` : meme `UPDATE ... SET isolement = 'NON' WHERE center_id = ?` puis un UPDATE par salle borne `center_id` ; aucun `@Transactional`/`REQUIRES_NEW` (grep). `reserverLeCentre` = `UPDATE centers SET id = id WHERE id = ?` ; la traduction `RefusDeVerrou.verrouillant` reste dans le traducteur de Planification (classe rendue `public`, donc meme exceptions). Verrou : meme JdbcTemplate -> meme connexion transactionnelle. |
| Lot E, identite imprimee | SQL des deux requetes identique a l'ancien `DocumentIdentityProvider` (jointure externe `LEFT JOIN societes`, aucun filtre d'activite), `lower`/`str` rognage deplaces tels quels, logo `byte[]` non vide sinon `null`. `ModeleDocumentProvisioner` : `SELECT id FROM centers` = `AnnuaireDesCentres.tous()` (tous centres, inactifs compris). |
| Admin generique (O4-R13) | Raison valable : SQL dynamique multi-tables de plusieurs proprietaires, cache `ref.*` invalide apres commit ; seule la desactivation de `medecin` (ligne 192) est une ecriture detectee. Voir n1. |
| `covered.json` / seams | `ModeleDocumentProvisioner` ajoute a `covered` apres un filet vert sur le legacy (`IdentiteImprimeeCasLimitesCharacterizationIntegrationTest`) : legitime, `seamAllowed` `[]`. `seams.md` coherent (lot B : 2 creations de traducteurs, C/D : aucun, E : 2 editions). |
| Graphe Maven et frontieres | Stock ne depend pas d'Organisation : ports `CentresFournis`, `VerrouDuCentreFourni` + traducteurs en plateforme (`CentresDuStockParOrganisation`). Production des consommateurs : seul `publishedlanguage` importe. Noms conformes au glossaire (`AnnuaireDesCentres`, `LibellesDeLOrganisation`, `IdentiteImprimee`, `VerrouDuCentre`). |
| Garde `TableDeLOrganisationTest` | `hasSize(7)`. Verifie par grep : les 7 entrees contiennent chacune au moins une vraie requete SQL (AuthService 97..282, DirectionAccountService:104, SocieteAdminAccountService:49..200, DevSuperAdminBootstrapInitializer:70, ReferentialAdminJdbcAdapter:192, DirectionAccessGuard:53, UserRestController:108) : aucune entree maintenue par un commentaire. La liste des residuels et de leurs proprietaires (O4-R9 a R13) est honnete. |
| `.approved` | `git diff --stat HEAD~4..HEAD -- '*.approved.json'` : vide. Tests consommateurs : constructions uniquement. |

## Constats
| # | Severite | Constat | Preuve | Correction |
|---|---|---|---|---|
| m1 | minor | O4-R7 : aucun contrat sur PostgreSQL. `reserverLeCentre` (`UPDATE centers SET id = id`, `FOR NO KEY UPDATE`) et `verrouiller` (`FOR UPDATE`) ne sont testes que sur H2, qui ne distingue pas les deux modes de verrou ni l'effet sur les insertions referencant le centre ; `ORDER BY id`/`COALESCE` identiques mais non prouves. Le SQL etant identique a l'ancien, le risque est de regression nulle, pas de parite non prouvee. | `VerrouDuCentreJdbc.java` ; fiche O4-R7 | `*PostgresContractIT` (Testcontainers) pour `VerrouDuCentre`, avant le retrait du legacy. |
| m2 | minor | KB-ORG-09/10 : deux comportements reproduits sans decision metier ; echeance 2026-12-15. | known-bugs lignes 1312-1313 | Executer §72/§73 en production, puis trancher ; supprimer `actifsAuSensStrict` si 0 NULL. |
| m3 | minor | Dans `LectureDesRessourcesPubliees`, `toutes`/`sallesDIsolement` lisent desormais toutes les colonnes de toutes les salles (filtre en Java) au lieu de `SELECT id ... AND isolement = 'OUI'`. Meme resultat, plus de donnees lues ; negligeable. | `SallesDuCentrePubliees.java` | Aucune ou exposer une lecture ciblee. |
| m4 | minor | `IdentiteImprimee.IdentiteDeLaSociete` expose un `byte[]` mutable dans un record (egalite par reference, tableau partage). | `IdentiteImprimee.java` | Copie defensive ou documenter (deja « ne pas modifier »). |
| n1 | nit | La regex lit commentaires et chaines : l'entree `ReferentialAdminJdbcAdapter` ne vit que par `UPDATE medecin` (ligne 192) ; le SQL dynamique sur `salle`/`position_creneau` est invisible a la garde. Constat deja note au lot A (n2) non traite. | `TableDeLOrganisationTest.java` (`LECTURE_SQL`) | Retirer les commentaires avant le match ; mentionner l'admin generique comme angle mort. |
| n2 | nit | O4-R12 : `DevSuperAdminBootstrapInitializer:70` = `SELECT id FROM centers`, strictement `AnnuaireDesCentres.tous()`. | `DevSuperAdminBootstrapInitializer.java:70` | Recommandation : MIGRER (une ligne, profil dev) des qu'un filet l'autorise ; sinon laisser nominatif sans risque. |
| n3 | nit | `JourDuCentreLegacy` garde son nom (O4-R1), `CentresActifs` publie inutilise (O4-R6). | fiche O4 | Apres accord utilisateur. |
| n4 | nit | Commentaire de tete de `TableDeLOrganisationTest` et Javadoc ne disent plus « planned » : a verifier a la passation en `built`. | `TableDeLOrganisationTest.java:20-21` | Mettre a jour la Javadoc de classe. |

## Recommandation
O4 peut passer a `built` avec 7 residuels nominatifs (butoir 2027-03-31), sous reserve de tracer m1 (IT PostgreSQL) et m2 (decision metier) comme conditions du retrait.

## Suites données (2026-11-09)
Revue lecture seule : aucune correction appliquee par le relecteur. Actions recommandees : m1 (IT PostgreSQL du verrou), m2 (SELECT §72/§73 puis decision), n1 (garde sans commentaires), n2 (migrer O4-R12), n4 (Javadoc).

### Resultats d execution (point 7)
En cours au moment de l'ecriture ; voir mise a jour ci-dessous.
