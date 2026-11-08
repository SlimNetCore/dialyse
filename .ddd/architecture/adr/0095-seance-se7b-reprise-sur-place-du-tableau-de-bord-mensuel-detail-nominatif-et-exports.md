---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance
related: ADR 0002, 0024 (refactoring sur place), 0062, 0088 (SE1) à 0094 (SE7a) ; `.ddd/safety-net/seance-se7b-tableau-de-bord-mensuel.md` (net 973b6a8a, 16 tests, 16 goldens), `.ddd/migration/slices/seance-se7b-tableau-de-bord-mensuel.md`, `.ddd/safety-net/seance-se7-lectures-publiees.md`, KB-SEA-62 à 67, RG-SEA-051, RG-ABS-060 à 062
---

# 0095 - Séance, SE7b : reprise sur place du tableau de bord mensuel des séances (compteurs, détail nominatif, exports CSV / XLSX / PDF)

Périmètre : `GET /api/v1/seances/dashboard`, `GET /dashboard/details?kind=presence|absence`, `GET /dashboard/export?format=csv|pdf|xlsx` (aujourd'hui dans `SeanceRestController`, environ 460 lignes d'agrégation et de génération de fichiers). Dernier résidu du `SeanceRestController` : après le lot B il ne reste plus de route dans ce contrôleur.

## RISQUES DE CONFIDENTIALITÉ ET DE PILOTAGE À FAIRE CONFIRMER AVANT LA MISE EN PRODUCTION
Toutes les décisions de cet ADR sont prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO**. **Jalon daté : les risques ci-dessous sont à arbitrer au plus tard le 2026-12-15 (revue) ; aucune mise en production sans décision écrite sur chacun.** Rien de ce qui suit n'est « corrigé au passage » : la reprise reproduit le legacy et le **signale** (seul KB-SEA-63, en section B, est corrigé).

### A. Comportements REPRODUITS à l'identique, avec alerte (décision métier / DPO requise)
| KB | Risque (reproduit, non corrigé) | Condition de mise en production | Contrôle (SELECT seule, `verifications-production.md` §60) |
|---|---|---|---|
| **KB-SEA-66 : le détail nominatif du tableau de bord est une donnée de santé ouverte à la secrétaire** | `GET /dashboard/details` expose, pour chaque séance réalisée ou chaque absence du mois, le **nom, le prénom, l'identifiant du patient et sa présence ou absence** à `ADMIN`, `INFIRMIER`, `MEDECIN` **et `SECRETAIRE`** du centre, **sans pagination ni filtre** (un mois entier, tous les patients). Un compte de secrétaire compromis ou curieux obtient la liste nominative de la fréquentation d'un centre. Les droits ne sont PAS restreints par cette reprise. | Décision écrite métier / DPO : (a) conserver l'ouverture à la secrétaire, (b) la retirer du détail nominatif en gardant les compteurs, (c) paginer et journaliser les lectures du détail. | requête 2 (lectures du détail par profil, journal d'audit) |
| **KB-SEA-64 : les compteurs ne se recoupent pas avec le détail ; un patient peut être présent ET absent le même jour** | `presenceCount` compte des séances, `absenceCount` les absences du suivi, `effectifPatients` des patients ; le détail écarte les patients introuvables dans le centre ; un patient dont la séance est réalisée et dont une absence est restée ouverte compte dans les deux. `totalSeances` est toujours égal à `presenceCount`. | Décision métier avant d'utiliser ces chiffres pour du pilotage (taux d'absentéisme, effectif) : quelle définition fait foi. | requête 3 (patients à plusieurs séances réalisées le même jour, qui gonflent les présences ; le recoupement avec les absences se fait dans §60 lot C) |
| **KB-SEA-65 : l'effectif et les répartitions sont contaminés par des patients d'un autre centre** | Les « présents » viennent d'une jointure des séances du centre avec **toute** la table des patients, sans filtre du centre du patient : un patient d'un autre centre ayant une séance réalisée dans ce centre entre dans l'effectif, la répartition par sexe et par âge, mais pas dans le détail nominatif (cloisonné). | Décision métier : filtrer ou non par centre du patient (transfert, patient de passage). | requête 1 (séances de patients d'un autre centre) |
| **KB-SEA-62 : `absenceDetails.expectedByWeekday` porte 14 clés** | Les sept clés françaises (`LUNDI`...) sont initialisées à 0 et jamais incrémentées ; les comptes sont sur les clés anglaises (`MONDAY`...). Un consommateur qui lit `LUNDI` lit toujours 0. Le front en dépend peut-être : **rien n'est changé** (le domaine le reproduit et le documente). | Décision avec le front : garder, supprimer les clés mortes ou traduire. | — |

### B. Écart CORRIGÉ par rapport au legacy (condition de mise en production : les métiers confirment la correction)
| KB | Correction | Condition de mise en production |
|---|---|---|
| **KB-SEA-63** (paramètres mal formés) | Mois 0, 13, négatif ; année au-delà de ±999 999 999 ; année ou mois absents ou illisibles ; centre illisible ou absent (export) : **500 `INTERNAL_ERROR` devient 400 `BAD_REQUEST`**, **403 avant 400** (un profil sans droit ou un centre étranger reçoit toujours 403). Les années 0, 1 et 9999 restent acceptées (reproduit). Le `kind` inconnu et le `format` inconnu restaient déjà en 400 ; le format est toujours contrôlé AVANT le mois. Si un golden de Absences A4 ou de Patient-couverture PC1 change, c'est un écart à approuver et à archiver dans le filet concerné. | Prévenir les clients qui comptaient sur un 500 (aucun connu). |

### C. Comportements REPRODUITS sans alerte
- **KB-SEA-67** : tri du détail par jour, nom, prénom **sensible à la casse** (les majuscules avant les minuscules : « Zola » avant « dupont ») ; CSV annoncé `text/plain` sans jeu de caractères.
- **Droits** : `ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` sur les trois routes ; `SUPERADMIN` et `DIRECTION` : 403 `ROLE_SCOPE` ; le centre doit être celui de la session (403 `ACCESS_DENIED`, KB-ABS-05).
- **Libellés** : noms de fichier `seances-dashboard-<année>-<mois sur 2 chiffres>.<ext>`, feuilles XLSX sans accent (`Synthese`, `Repartition sexe`, `Repartition age`), titres du PDF, clés JSON.
- **Aucun montant** : le tableau de bord n'expose ni n'ajoute aucun montant (les absences sont comptées, jamais valorisées).
- **Jours non ouvrés illisibles** : la lecture des fériés et des fermetures qui échoue, quelle qu'en soit la cause, ne bloque pas le tableau de bord et ne compte aucun jour (comportement observable du legacy, qui avalait toute exception SQL). **Écart de journalisation non fonctionnel** : un WARN, avec le seul identifiant du centre et la nature de jour (jamais le message de l'exception), remplace le silence.
- **Jour du centre** : « aujourd'hui » (jours échus, absences bornées au jour, mois futur sans absence ni séance prévue) vient de `HorlogeDuCentre` (fuseau du centre), jamais de la JVM ni de `LocalDate.now`.
- **Audit** : journal d'audit générique, inchangé ; aucune diffusion ni notification.

## Contexte
Le net SE7b (16 tests, 16 goldens, commit 973b6a8a) établit que ces routes sont servies par `SeanceRestController` : SQL direct (`JdbcTemplate`) sur `seances`, `patients`, `center_holiday`, `center_closure_day`, absences par `AbsencesDuPilotage`, formules dans le contrôleur, fichiers générés dans le contrôleur (Apache POI 5.2.5, OpenHTMLtoPDF 1.0.10).

## Décisions
1. **Dans `backend/contexts/seance`**, mêmes tables, mêmes URL, mêmes champs JSON ; aucune migration (ADR 0024 : ni façade ni drapeau ; la bascule est le retrait des handlers legacy, réversible par retour git).
2. **Domaine (pur)** : `MoisDuTableauDeBord` (bornes, jours échus), `Sexe`, `TrancheDAge`, `FinDeLaPriseEnCharge` (libération de la place), `PatientDuMois`, `SeancesAttendues` (séances prévues, jours bloqués, KB-SEA-62), `EffectifDuMois` (effectif et répartitions, KB-SEA-65), `TableauDeBordDesSeances`, `TypeDeDetail`, `LigneDuDetail` (tri, KB-SEA-67), `FormatDExport`. Aucune formule dans les cas d'usage ni dans les adapters.
3. **Application** : `ConsulterLeTableauDeBord`, `ConsulterLeDetailDuTableauDeBord`, `ExporterLeTableauDeBord`. Ordre des refus : centre (403), type ou format (400), mois (400).
4. **Ports sortants** : `SeancesRealisees`, `PatientsDuTableauDeBord`, `AbsencesDuCentre`, `JoursBloques`, `ExportsDuTableauDeBord`. Les lectures croisées passent toutes par un port.
5. **Adapters** : `SeancesRealiseesJdbc` (table `seances`, propre au contexte) ; **ACL nommée** `PatientsDuTableauDeBordJdbc` (`acl/legacypatient`, lecture de `patients`, inscrite et datée dans la liste blanche de `TableDuPatientCouvertureTest`, sans exception ArchUnit) ; **ACL** `AbsencesDuCentreParLePilotage` (`acl/absencespatients`, langage publié `AbsencesDuPilotage`, aucune lecture de table des Absences) ; `JoursBloquesDuCalendrier` (compose le port `CalendrierDuCentre` de SE7a : le calendrier vit dans le module, aucune lecture SQL des tables du calendrier hors `CalendrierDuCentreJdbc`) ; `ExportsDuTableauDeBordCsvPdfXlsx` (`infrastructure/export`, mêmes bibliothèques et versions que la plateforme : **aucun changement de bibliothèque**).
6. **Tests** : domaine pur (60), cas d'usage avec doubles en mémoire (11), exports décodés (4), ACL et tolérance du calendrier (3), lectures SQL sur H2 (4) et requêtes exécutées sur PostgreSQL 16 jetable par `backend/scripts/seance-se7b-pg16-rejeu.sh` (SELECT seule : ces adapters ne font que lire).
7. **Pas d'événement d'intégration** (aucun consommateur) ; à créer avec le premier consommateur.

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre pour chacun)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| R1 | ACL `PatientsDuTableauDeBordJdbc` lit directement `patients` (programmation hebdomadaire, état, sommeil, jointure séances-patients, identités) : à remplacer par un langage publié du Patient (effectif et programmation d'un mois) | 2026-12-15 | 2027-03-31 |
| R2 | Règle de libération de la place (`FinDeLaPriseEnCharge`) traduite une troisième fois (Patient `FinOccupation`, Planification `FinDOccupation`) : à remplacer par la règle publiée du Patient | 2026-12-15 | 2027-03-31 |
| R3 | Deux sources du « jour » dans une même lecture : la Séance (`HorlogeDuCentre`) pour les bornes, `AbsencesDuPilotage` pour le comptage ; fuseaux identiques tant que les centres partagent le fuseau par défaut | 2026-12-15 | 2027-01-31 |
| R4 | Décisions KB-SEA-62, 64, 65, 66, 67 : arbitrage métiers / DPO / front | 2026-12-15 | avant toute mise en production |
| R5 | Tolérance du calendrier illisible (WARN) : décider s'il faut échouer au lieu de compter zéro jour non ouvré | 2026-12-15 | 2027-03-31 |
| R6 | Génération de PDF et de XLSX déclarée dans le module (POI, OpenHTMLtoPDF) et dans la plateforme : un module d'export partagé est à décider avec le premier autre consommateur | 2026-12-15 | 2027-06-30 |
| R7 | Test PC1 `le_tableau_de_bord_de_la_seance_compte_l_effectif...` lit le mois courant (horloge) et masque les dates : non modifié | 2026-12-15 | 2027-01-31 |

## Lot B : routage, parité, écarts approuvés un par un (livré 2026-11-08, décisions à confirmer par le coordinateur)
`TableauDeBordDesSeancesControleur` (nom propre : `TableauDeBordControleur` existe dans Facturation, conflit de bean), `ErreursDeSeanceAdvice` étendu (400 effectif sur année ou mois absents ou illisibles et centre illisible ; 403 avant 400). `SeanceRestController` SUPPRIMÉ (il n'avait plus aucune autre route) ; seam inscrit dans `seams.md` AVANT, refermé après (`seamAllowed` vide, fichier retiré de `covered.json`). Verrous ArchUnit `seul_contexts_seance_sert_le_tableau_de_bord_mensuel` et `le_controleur_historique_de_la_seance_a_disparu`, sans exception ; l'entrée du contrôleur retirée de la liste blanche `TableDuPatientCouvertureTest`.
Parité : les 16 scénarios SE7b, dont les exports CSV, XLSX et PDF décodés, sont identiques au legacy, SAUF `tableau/parametres` ; les filets Absences A4 et Patient-couverture PC1 restent verts, SAUF 4 goldens A4 (même famille KB-SEA-63). 5 écarts, tous KB-SEA-63 (500 devient 400), approuvés un par un, legacy archivé en R100 (`git mv`, vérifié contre `git show 973b6a8a:<chemin>`) sous `_ecarts-legacy`, golden recréé en `*_nouveau_code.approved.json` :
| # | Archive legacy | Écart |
|---|---|---|
| 1 | `seance/se7b/_ecarts-legacy/tableau/parametres_avant_se7b.legacy.json` | 19 cas (mois 0, -1, 13 ; année 1 000 000 000 ; année ou mois absents ou illisibles ; centre illisible ou absent) sur les 3 routes : 500 vers 400 ; le format inconnu et le `kind` inconnu étaient déjà 400 |
| 2 | `absences-patients/consultation/_ecarts-legacy/lectures-publiees-a4/tableau_de_bord_mois_13_avant_se7b.legacy.json` | A4 : `dashboard` mois 13 : 500 vers 400 |
| 3 | `.../tableau_de_bord_sans_centre_avant_se7b.legacy.json` | A4 : `dashboard` sans `centerId` : 500 vers 400 |
| 4 | `.../tableau_de_bord_annee_illisible_avant_se7b.legacy.json` | A4 : `dashboard` année `abc` : 500 vers 400 |
| 5 | `.../details_type_absent_avant_se7b.legacy.json` | A4 : `dashboard/details` sans `kind` : 500 vers 400 |
Aucun autre golden de A4 ou de PC1 n'a changé. `BorneDesAbsencesDuTableauDeBordTest` (KB-ABS-35, test unitaire du contrôleur supprimé) est reporté en test d'application du module ; `AbsencePatientIntegrationTest` appelle désormais les cas d'usage du module.

## Lots
LOT A (livré 2026-11-08) : cet ADR, domaine, cas d'usage avec doubles, ports, ACL, adapters de lecture et d'export, requêtes rejouées sur PostgreSQL 16 ; AUCUN trafic changé (les handlers legacy sont encore actifs). LOT B : contrôleur et advice, retrait des 3 handlers legacy (seam inscrit avant, refermé après), parité 16/16, écarts approuvés un par un, verrou ArchUnit. LOT C : RG-SEA, annexe 16, glossaire, §60, bilan des résidus, `built`.
