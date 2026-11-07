---
status: proposed
date: 2026-11-07
sources: net 5bdd3a0e ; commits f35cfaba, 8321c452, 03c66446, 5093d554 ; ADR 0088 ; check-arch (0 violation) ; revue statique, aucun build ni test rejoué
---

# Revue SE1 (Séance : création, scan, consultation, liste)

## Contrôles vérifiés sans constat
| Point | Preuve |
|---|---|
| check-arch | `0 violations (0 baselined, 0 new)` ; état pipeline `seance-se1-creation: built` |
| Goldens legacy octet pour octet | `git diff -M --name-status 5bdd3a0e..HEAD` sous `characterization/seance/se1` : 13 `R100` (vers `_ecarts-legacy/**/*_avant_se1.legacy.json`) + 13 `A` (`*_nouveau_code.approved.json`), 0 `M` ; 31 `.approved.json` au total = 31 au net (18 inchangés + 13 nouveaux) |
| 13 écarts justifiés | diff un par un : 500 DATA_ACCESS_ERROR vers 409 (KB-SEA-02), 500 vers 400 et 403-avant-400 (KB-SEA-04), 400 vers 404 `PATIENT_INTROUVABLE` et QR `-` refusé (KB-SEA-06), statut inconnu sans nom de classe et liste 200 (KB-SEA-07), audit : lignes 500 vers 400/404 uniquement. Aucun écart hors décision |
| Reconstruction du module | `git rm` de 30 fichiers gelés (ADR 0074 passé `superseded par 0088`), `GEL.md` supprimé ; `backend/pom.xml:42`, `platform/pom.xml:87`, `Dockerfile:29` |
| Domaine pur | imports de `domain/` = `java.*` + `CentreId` ; pas de `now()`/`randomUUID` en domaine/application ; `ArchitectureHexagonaleTest` (domaine sans framework, sans UUID/horloge, application sans adapter, `aucun_import_du_legacy`) |
| Frontière | `FrontieresDesContextesTest` : seul ajout = la règle `plus_aucun_appelant_des_ecritures_legacy_de_la_creation_de_seance` ; aucune exception ajoutée ; le legacy n'importe de `contexts.seance` que `publishedlanguage` (grep) ; 8 langages publiés + `JourDuCentreFourni` à types primitifs |
| SQL du module | `SeancesJdbc` ne touche que `seances` (9 colonnes écrites, 13 lues) ; aucune autre table dans `contexts/seance` |
| Sécurité | `ErreursDeSeanceAdvice` : `DroitsDeLaRoute` évalue le `@PreAuthorize` avant 400 (`HttpMessageNotReadable`, `ArgumentTypeMismatch`, `MethodArgumentNotValid`) ; `SessionLegacy` garde de centre ; 409 `SEANCE_DU_JOUR_AMBIGUE` (`ScannerUnPatient:84`) ; verrou `pg_advisory_xact_lock` + `lock_timeout` 5 s (55P03 vers 409) ; `SeanceIllisible` sans nom de classe |
| Règles reproduites | `CodeScanne`/`ResoudreLePatientScanne` = `resolvePatientIdFromQr` legacy (UUID, code, numéro, tolérant) ; ordre planning, PEC, attestation (`ScannerUnPatient.creer`) ; `DerogationHorsPlanning` motif + précision obligatoire pour AUTRE + 255 ; `CauseDeNonFacturation` messages |
| Scan atomique | création JDBC + `ValidationFournieLegacy` (`validate(..., List.of())` puis `entites.flush()`) dans `UniteDeTravailSpring` (une transaction) ; `List.of()` = aucune sortie de stock, donc pas de double effet stock ; diffusions envoyées APRES commit (`ScannerUnPatient.diffuser`) donc aucune diffusion sur échec |
| Diffusions | `DiffusionsParLeLegacy` reproduit les 3 appels et conditions du legacy (`SeanceRestController` au net, l. 197-207) |
| KB-SEA-03 | en tête de l'ADR 0088, échéance 2026-12-15 |
| Verrous d'architecture et jalons | `TableDuPatientCouvertureTest` : `HistoriqueFourniLegacy` et `ForfaitsFournisLegacy` datés 2026-11-07, revue 2026-12-15 ; `seamAllowed` vide, `covered.json` ne garde que `SeanceRestController` et le glob `acl/seance/*.java` ; DTO supprimés |
| Script PG16 | `seance-se1-pg16-rejeu.sh` : port 5432 refusé, mot de passe `SEANCE_PG16_PASSWORD` (ou aléatoire), cluster détruit au `trap`, requêtes SELECT seules |
| Doc | `verifications-production.md` §53 : 4 requêtes SELECT ; RG-SEA-052 + annexe `16` ; exclusion `/contexts/seance/` levée de `ReglesDeGestionDocumentationTest` |
| Résidus nominatifs | grep : `create`, `createFromQr`, `scanAndValidate` sans appelant hors chaîne `SeanceApplicationService`/`SeanceDomainService` (et tests bc-seance) ; `getDetails` (SeanceRestController l. 221, 253, 269, 1005 ; VoletParamedical l. 55) et `search` (`SeanceRegularisationService:38`) encore appelés |

## Constats
| Sév. | Domaine | Constat | Preuve | Correction recommandée |
|---|---|---|---|---|
| major | tests / archi | **`centerId` absent sur le détail et la liste répond encore 500 `INTERNAL_ERROR`**, alors que KB-SEA-04 (décision CORRIGER, "centerId absent sur le détail et la liste"), ADR 0088 §10 et RG-SEA-052 ("une requête mal formée répond 400 et non 500") l'annoncent corrigé. Aucun handler de `MissingServletRequestParameterException` dans l'advice du module ; les deux goldens figent le 500 (non listé parmi les 13 écarts) | `.../se1/lecture/le_detail_refuse_en_400_une_seance_i/detail_refus_nouveau_code.approved.json:7` ; `.../lecture/la_liste_est_triee_par_date_decroiss/liste.approved.json:29` ; `contexts/seance/.../interfaces/http/ErreursDeSeanceAdvice.java` (aucun `MissingServletRequestParameter`) | Ajouter le handler (400 `BAD_REQUEST`, 403 avant 400 via `droitsSuffisants`) et réapprouver ces 2 goldens en `_nouveau_code` (legacy archivé en `_ecarts-legacy`), OU corriger KB-SEA-04/ADR/RG-SEA-052 pour dire que ce cas reste en 500 (décision explicite) |
| minor | legacy | Bilan nominatif des résidus incomplet : `SeanceRepositoryPort.findByPatientIdAndDate` (+ `SeanceRepositoryAdapter.findByPatientIdAndDate`) n'est plus appelé que par `create`/`scanAndValidate` ; l'ADR §7 annonçait son retrait, le §11/§12 l'omet ; idem `Seance.marquerHorsPlanning`. Le verrou ArchUnit ne protège que les 3 méthodes de `SeanceUseCase` | `SeanceDomainService.java:145,156` ; `SeanceRepositoryPort.java:20` ; `Seance.java` `marquerHorsPlanning` (appel unique `SeanceDomainService.java:97`) | Ajouter ces éléments à la liste du §11/§12 de l'ADR 0088 pour la revue du 2026-12-15 |
| minor | legacy | KB-SEA-01 ("deux horloges") corrigé seulement pour POST et scan : `SeanceDomainService.recentByPatient` (appelé par `SeanceStationRestController:56`, vivant), `updateDate`, déverrouillage lisent encore `LocalDate.now()` ; RG-SEA-052 ne le précise pas | `SeanceDomainService.java:196,231,249` | Mentionner le périmètre exact dans RG-SEA-052 / ADR (reste pour SE2, SE4, SE7) |
| minor | archi | Lecture SQL directe de la table GMAO `gmao_equipements` dans l'ACL de la plateforme, sans jalon daté dans une garde de tables (aucune garde GMAO ne la nomme) | `platform/.../acl/seance/GenerateursFournisLegacy.java:25` ; grep `gmao_equipements` dans `platform/src/test/.../architecture` : 0 résultat | Ajouter un jalon daté (comme `HistoriqueFourniLegacy`) ou passer par un langage publié de GMAO |
| minor | archi | La liste (lecture de `seances` jointe à `patients`) et le forfait courant restent des lectures SQL de la plateforme, hors module ; `TableDesSeancesTest` reste `@Disabled`, donc aucune liste blanche des lecteurs de `seances` ne les couvre (seul le jalon `TableDuPatientCouvertureTest` pour `patients`/`prise_en_charge`) | `TableDesSeancesTest.java:18` ; `HistoriqueFourniLegacy.java:37-41` ; `ForfaitsFournisLegacy.java:46` | Déjà reporté à SE7 (ADR §Conséquences) : tenir l'échéance 2026-12-15, ou rendre la garde active dès maintenant pour ces deux classes |
| minor | tests | Double mémoire `Banc` pour `HistoriqueDesSeances`/`ForfaitsDeSeance` : pas de contrat commun avec l'adapter réel (accepté ADR §12) ; contrat SQL de la plateforme uniquement H2 + PG16 (11 cas) | ADR 0088 §12 ; `LecturesDeLaSeanceContrat.java` | Acceptation tracée ; à rejouer avec le script PG16 avant mise en production |
| minor | legacy | Un échec de diffusion est désormais avalé et journalisé (`DiffusionsFournieesLegacy.diffuser`) ; le legacy laissait remonter une 500 après écriture. Amélioration non listée dans les décisions KB | `acl/seance/DiffusionsFournieesLegacy.java:44-50` | Consigner dans l'ADR 0088 (décision de robustesse) |
| minor | doc | Pratique du dépôt "doc fonctionnelle régénérée après chaque changement" : ADR §12 décide de NE PAS régénérer doc fonctionnelle/maquette (refus 500 vers 400/404/409 seulement) ; décision tracée mais à confirmer par l'utilisateur | ADR 0088 §12 | Faire confirmer, sinon régénérer |
| minor | doc | Documents périmés : la slice dit "session A faite, session B restante" et `characterized (jamais built...)` alors que l'état est `built` ; le glossaire dit "`ABSENT` n'existe pas dans ce contexte" alors que `StatutDeSeance.ABSENT` existe et `ScannerUnPatient` le refuse (422) | `.ddd/migration/slices/seance-se1-creation.md:33` et section Avancement ; `.ddd/strategic/glossaries/seance.md:27` ; `StatutDeSeance.java:8` | Mettre à jour (ABSENT = reliquat lisible, ADR 0024) |
| nit | tactique | `DerogationHorsPlanning` refuse une précision brute > 255 avant rognage (ADR : "rognée et bornée") : une précision de 256 caractères dont des espaces de fin tiendrait en 255 est refusée (en plus de `@Size(max=255)` du DTO, même effet) | `DerogationHorsPlanning.java` constructeur | Rogner puis borner, ou assumer (cas marginal) |
| nit | archi | Commentaire périmé : demande à la session B d'ouvrir une exception "NOMMEE et DATEE" alors que le choix tranché (§9) est aucune exception | `FrontieresDesContextesTest.java` (javadoc de `le_legacy_n_utilise_la_seance_que_par_son_langage_publie`) | Réécrire le commentaire |
| nit | archi | `historique` exécute un `COUNT` supplémentaire à chaque liste pour journaliser les statuts illisibles ; glob `acl/seance/*.java` dans `covered.json` ouvre l'édition de tout futur fichier du dossier | `HistoriqueFourniLegacy.java:78` ; `covered.json` | Journaliser sans requête dédiée (ou échantillonner) ; retirer le glob à la fermeture de SE2/SE7 |

## Limites de cette revue
Aucun build, test ni script PG16 rejoué (interdit). La verdeur de la suite (31/31 parité, contrats H2/PG16, ArchUnit) repose sur les messages de commit et l'ADR, non revérifiée ici.

## Verdict : pass-with-actions
0 blocker, 1 major, 9 minor, 3 nit.

Top 3 actions :
1. Traiter le 500 sur `centerId` absent (handler + 2 goldens `_nouveau_code`) ou acter explicitement qu'il reste en 500 dans KB-SEA-04, ADR 0088 §10 et RG-SEA-052.
2. Compléter le bilan nominatif des résidus (`findByPatientIdAndDate`, `marquerHorsPlanning`, horloges restantes) avant la revue du 2026-12-15 ; nommer `GenerateursFournisLegacy` dans une garde de tables.
3. Mettre à jour les documents périmés (slice, glossaire `ABSENT`, commentaire `FrontieresDesContextesTest`) et faire confirmer la non-régénération de la doc fonctionnelle.
