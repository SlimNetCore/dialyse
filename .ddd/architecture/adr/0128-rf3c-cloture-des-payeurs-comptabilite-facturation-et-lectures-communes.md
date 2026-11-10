---
status: accepted
date: 2026-11-10
review-by: 2026-12-15
decision-makers: [architecte (mise en oeuvre de l'ADR 0121, décision 3 révisée par l'utilisateur le 2026-11-10 : Comptabilité lit le langage publié)]
context-scope: patient-couverture (propriétaire), comptabilite, facturation, organisation, platform (garde de tables)
related: ADR 0126 (RF3a), 0127 (RF3b), 0121 (propriété, décision 3 révisée), 0124 (RF2, modèle suivi), 0079 (`patientcouverture.v1`), 0024 (refactoring sur place) ; fiche `.ddd/migration/slices/patient-couverture-rf3-payeurs.md` ; known-bugs KB-RF3-03, KB-FAC-65
---

# 0128 - RF3c : clôture des payeurs (Comptabilité, Facturation, lectures communes de l'Organisation)

Refactoring SUR PLACE : mêmes tables (`caisse_assurance`, `agence`, `centre_payeur`), mêmes URLs, mêmes rôles, mêmes codes, même JSON ; ni façade, ni flag, ni projection. Périmètre RF3c : lien Maven `comptabilite -> patient-couverture`, lecture des payeurs par Comptabilité via `PayeursDuCentre`, sort des cinq lecteurs `GROUP BY` de la Facturation, transfert à Patient et couverture des listes de choix `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}`, inventaire des entités JPA, fermeture de la garde de tables.

Parité : AUCUN golden modifié ni ajouté (rf3 : 45 goldens de RF3a et `lectures-communes`, organisation, comptabilité, facturation, seance, pilotage-direction, absences) ; aucun `_nouveau_code`, aucune archive `_ecarts-legacy`.

## Preuve d'absence de cycle Maven (avant tout ajout)
| Module | Dépendances `com.hemodialyse` (pom.xml) |
|---|---|
| `contexts-patient-couverture` | shared-kernel, organisation, planification |
| `contexts-organisation` | shared-kernel, stock, gmao |
| `contexts-planification` | shared-kernel, gmao, organisation |
| `contexts-stock`, `contexts-gmao` | shared-kernel |
| `contexts-comptabilite` | stock, shared-kernel (+ patient-couverture : ajout RF3c) |

Seuls `absences-patients`, `facturation` et `platform` référencent `contexts-comptabilite` ; aucun module atteignable depuis `patient-couverture` ne les référence. Après ajout, `./mvnw -o validate` sur le réacteur complet réussit, ordre : shared-kernel, gmao, stock, organisation, planification, patient-couverture, comptabilite, facturation, ... (aucun cycle). La Facturation dépendait déjà de patient-couverture (RF2).

## Décisions
| N° | Décision | Raison |
|---|---|---|
| 1 | Comptabilité : `PayeursDuCentreJdbc` (SQL sur `centre_payeur`) supprimé, remplacé par l'ACL mince `infrastructure/acl/patientcouverture/PayeursParLAnnuaire` (port inchangé `LectureDesPayeurs`) qui lit `PayeursDuCentre.centresPayeurs` / `detail` ; le compte client propre au payeur reste lu dans `comptes_payeurs` (table de Comptabilité). Tri par nom puis code, recherche sans casse avec jokers SQL non échappés (KB-CPT-05, `%`, `_`, `\`) reproduits en Java, page découpée en Java | ADR 0121 décision 3 révisée ; modèle RF2/RF3b ; contrat `PayeurContrat` inchangé (double en mémoire / H2 / PG16) |
| 2 | Facturation, reprise des soldes : `RepriseDesSoldesJdbc` ne lit plus `centre_payeur` ; port `application/payeur/AgenceDuPayeur` + ACL `AgenceDuPayeurParLAnnuaire` (`PayeursDuCentre.detail`, agence enregistrée sur le centre payeur). Deux cas de contrat ajoutés (H2 et PG16) : l'agence est figée pour un payeur du centre ; aucune pour un payeur inconnu, d'un autre centre ou absent | seul lecteur de la liste blanche qui n'est PAS une jointure `GROUP BY` : lecture d'annuaire pure |
| 3 | Facturation, quatre lecteurs MAINTENUS en liste blanche (jalon et butoir datés, voir RF3c-J1) : `SituationsDesFacturesJdbc`, `LectureDeLaSyntheseJdbc`, `LectureDuTableauDeBordJdbc`, `SyntheseFinanciereDesCentresJdbc` | l'agence et le centre payeur d'une facture sont des instantanés figés à l'émission, pas des lectures de référentiel ; la preuve de parité (goldens inchangés) ne couvre que H2 alors que l'ordre (`ORDER BY` selon la collation), les filtres et la pagination SQL de ces lecteurs dépendent de la base |
| 4 | Lectures communes : `/api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` ET leurs doublons `/api/v1/organisation/referentiels/...` servis par Patient et couverture (`PayeursControleur`, `ConsulterLesPayeurs`, port `ChoixDesPayeurs`, adaptateur `ChoixDesPayeursJdbc`, caches `ListesDePayeursEnCache` aux MÊMES noms `ref.*` et clés). Requêtes reprises TELLES QUELLES (`ORDER BY nom`, jointures de la liste détaillée non bornées par centre) ; `centerId` illisible : 400 (advice `ErreursDesPayeursAdvice`, comme avant), `centerId` absent : 500 (inchangé, KB-RF3-03) ; garde de centre 403 par la session | le port de lecture n'est pas l'annuaire publié : la liste de choix a ses règles de tri et de forme propres, sans les bornes de l'annuaire |
| 5 | Organisation : `ReferentielsCommunsJdbc`, `ReferentielsCommunsRepository`, `DetailPayeur`, les quatre méthodes de `ReferentielsCommunsUseCases` / `ReferentielsCommunsService` et leurs routes dans les deux contrôleurs retirés ; il ne reste que `etatsPatients` | fiche O5 : référentiels orphelins clos |
| 6 | Garde `TableDesPayeursTest` : effectif 7 -> 4 (les quatre jointures de la Facturation, chacune avec raison et butoir) ; règle ArchUnit `les_lecteurs_annuaire_des_payeurs_passent_par_le_langage_publie` étendue aux deux nouvelles ACL | modèle RF2/RF3b |

## Entités JPA (inventaire)
`CentrePayeurJpaEntity`, `CaisseAssuranceJpaEntity` et `AgenceJpaEntity` (platform) : aucune référence dans le code de production ni de test (grep des noms de classes, aucun dépôt Spring Data, aucune requête JPQL). Suppression ESSAYÉE puis ANNULÉE : sous `ddl-auto=update` (H2 de développement et de test) Hibernate crée ces tables AVANT `schema.sql` (`CREATE TABLE IF NOT EXISTS`), et `agence_id` y est nullable comme en production (Flyway V1) ; `schema.sql` la déclare `NOT NULL`. Sans les entités, `ModelesDePersistanceAlignesFacturationIntegrationTest.payeur_du_patient_lu_pour_l_instantane_de_la_facture_f3` échoue (nullité de `centre_payeur.agence_id`). Les trois entités restent donc, comme RÉSIDUEL NOMMÉ RF3c-R2.

## Écarts de parité
Aucun observé par les goldens. Nuances de modèle consignées : RF3c-R1 (ordre de tri et recherche de Comptabilité effectués en Java), RF3c-R3 (caisse bornée au centre dans l'annuaire lu par Comptabilité, voir RF3b-R1).

## Résiduels nominatifs
| ID | Résiduel | Raison / retrait |
|---|---|---|
| RF3c-J1 | Quatre lecteurs de la Facturation nomment encore les trois tables (jointures sur l'instantané figé de la facture) | butoir : revue au 2027-03-31. Retrait possible quand la Facturation lit la chaîne des payeurs d'une facture (agence figée -> caisse) par une lecture publiée groupée ET que le tri/filtre/pagination SQL est reproduit sous PG16 par contrat |
| RF3c-R1 | Comptabilité : `lister` trie et filtre en Java (`nullsLast`, ordre naturel de `String`) au lieu de `ORDER BY nom, code` et `LIKE` de la base | volumétrie d'un centre (quelques centaines de payeurs) ; la collation PostgreSQL peut ordonner autrement que l'ordre naturel de Java les noms accentués ou de casse différente (non vérifié sur les données réelles) ; un compte `comptes_payeurs` n'est lu que pour la page |
| RF3c-R1b | Le FILTRE de recherche des payeurs côté Comptabilité est lui aussi refait en Java (`PayeursParLAnnuaire`, méthodes `correspond` / `motif` : `toLowerCase(Locale.ROOT)` + expression régulière) au lieu du `LIKE` de la base ; le repli de casse et d'accents du `LIKE` PostgreSQL peut différer de Java (non vérifié) | même volumétrie que RF3c-R1 ; vérification : contrat PG16 avec un nom accentué avant la bascule (revue globale 2026-11-10) ; code inchangé |
| RF3c-R1c | `DatesIllisiblesDeLaDirectionAdvice` porte aussi le 400 de TOUTE l'administration générique des référentiels (RF1c, RF2c, RF3d) : le nom ne le reflète pas (revue globale 2026-11-10) | renommage NON fait : plus de cinq références (7 fichiers de code et de test, 28 fichiers de documentation, `covered.json`, `seams.md`) ; à traiter avec le retrait de l'administration générique ou dans un lot de renommage dédié |
| RF3c-R2 | Entités JPA `CentrePayeurJpaEntity`, `CaisseAssuranceJpaEntity`, `AgenceJpaEntity` conservées | définissent le DDL H2/Hibernate (`agence_id` nullable comme en production) ; retrait avec l'alignement de `schema.sql` sur Flyway (butoir : fin de l'administration générique) |
| RF3c-R3 | `PayeursDuCentre.detail` borne l'agence et la caisse au centre ; l'ancienne lecture de Comptabilité ne lisait que `centre_payeur` | sans effet sur les champs lus (code, nom) ; même contrôle que RF3b-R1 avant la bascule |
| RF3-R1 | `ReferentialKind.CAISSE/AGENCE/CENTRE_PAYEUR` restent dans l'administration générique (descripteurs : champs, alias d'en-têtes, modèles d'import, libellés ; `PatientMigrator` y résout un centre payeur) ; `ReferentialAdminTables` ne nomme plus aucune des trois tables depuis RF3a | modèle RF1/RF2 (`TRANSPORTEUR`, `FORFAIT` restent) ; retrait avec l'administration générique (butoir : 2027-03-31, à confirmer par les métiers ; verrouillé par `DescripteursResiduelsDeLAdministrationGeneriqueTest` : liste nominative des cinq descripteurs résiduels RF1-R1, RF2-R1, RF3-R1, comparée à l'horloge) |
| RF3-R3 | **SOLDÉ le 2026-11-10 par RF3d (ADR 0129).** Texte d'origine : Les 500 du legacy (KB-RF3-01 à 03) ne sont pas corrigés | décision de l'utilisateur requise |
| RF3c-R4 | Le lien Maven `comptabilite -> patient-couverture` couple la construction : le script `comptabilite-pg16-rejeu.sh` (`-pl contexts/comptabilite`, hors ligne) lit patient-couverture dans le dépôt Maven local | `./mvnw -o install -DskipTests -pl contexts/patient-couverture` avant le rejeu, ou ajouter `-am` |

## Conséquences
- Aucun fichier legacy de la plateforme modifié (ni code de production ni `covered.json`) ; `seamAllowed` reste `[]`. Retrait du code Organisation (les contrôleurs perdent huit routes, reprises mot pour mot par `PayeursControleur`).
- Rejeux PG16 : `patient-couverture-rf3-pg16-rejeu.sh` (20 cas, dont 2 nouveaux : listes de choix), `comptabilite-pg16-rejeu.sh` (53 dont 8 payeurs), `facturation-pg16-rejeu.sh` (reprise des soldes : 13 dont 2 nouveaux).
- Retour arrière : revert Git.

## Questions ouvertes
- Confirmation métier de la propriété (ADR 0121, décision 2) avant le 2026-12-15 (condition 5 du retrait du CRUD générique).
- Faut-il corriger les 500 (KB-RF3-01 à 03) en 400 comme RF1c/RF2c ?
- La Facturation doit-elle un jour lire la chaîne payeur par un langage publié groupé (RF3c-J1) ou le figeage de l'agence suffit-il à garder ces jointures ?
