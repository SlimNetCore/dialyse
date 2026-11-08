---
status: accepted
date: 2026-11-08
review-by: 2026-12-15
decision-makers: [utilisateur (décisions déléguées à l'assistant via le coordinateur, 2026-11-08), architecte]
context-scope: seance
related: ADR 0002, 0024 (refactoring sur place), 0062, 0074, 0088 (SE1) à 0093 (SE6) ; `.ddd/safety-net/seance-se7a-routes-residuelles.md` (net be0d7107, 15 tests, 17 goldens), `.ddd/migration/slices/seance-se7a-routes-residuelles.md`, `.ddd/safety-net/seance-se7-lectures-publiees.md`, KB-SEA-57 à 61
---

# 0094 - Séance, SE7a : reprise sur place des routes résiduelles (journal du jour, calendrier du centre, volet médical, séances récentes)

Périmètre : `GET /api/v1/seances/journal`, `GET /calendar`, `POST /calendar/holiday`, `POST /calendar/closure`, `DELETE /calendar/holiday/{id}`, `DELETE /calendar/closure/{id}` (aujourd'hui dans `SeanceRestController`), `GET /api/v1/seances/patient/{id}/recentes` (`SeanceStationRestController`), `PUT /api/v1/seances/{id}/volet-medical` (`VoletMedicalRestController`). HORS périmètre : le tableau de bord (`dashboard`, `dashboard/details`, `dashboard/export`, SE7b) et le PUT du volet paramédical (DM1).

## RISQUES CLINIQUES, DE CONFIDENTIALITÉ ET D'INTÉGRITÉ À FAIRE CONFIRMER AVANT LA MISE EN PRODUCTION
Toutes les décisions de cet ADR sont prises par délégation, EN VIGUEUR, **à confirmer métiers / DPO**. **Jalon daté : les risques ci-dessous sont à arbitrer au plus tard le 2026-12-15 (revue) ; aucune mise en production sans décision écrite sur chacun.**

### A. Comportements CLINIQUES et CONFIDENTIELS REPRODUITS À L'IDENTIQUE, avec alerte (décision métier / DPO requise)
| KB | Risque (reproduit, non corrigé) | Condition de mise en production | Contrôle (SELECT seule, `verifications-production.md` §59) |
|---|---|---|---|
| **KB-SEA-59 (1) : le volet médical est REMPLACÉ en entier** | `PUT /{id}/volet-medical` écrase les six observations (prescription, tolérance, examen clinique, résultats biologiques, ajustements thérapeutiques, conclusion). **Un champ absent du corps EFFACE la valeur déjà saisie** (un corps `{centerId}` seul vide les six observations), sans version, sans historique, sans confirmation. Une observation médicale peut disparaître par un simple oubli de champ côté client. | Décision écrite des médecins responsables : (a) conserver le remplacement total, (b) passer à une modification partielle (champ absent = inchangé), ou (c) garder l'historique des versions. | requêtes 2 (écritures par auteur) et 3 (volets vidés) |
| **KB-SEA-59 (2) : la route reste ouverte sur une séance SIGNÉE** | Seuls `CREE`, `ABSENT` et `FACTUREE` sont refusés (422). Une séance `VALIDEE` ou `SIGNEE` accepte un nouveau volet : **la signature du médecin ne verrouille pas ses propres observations**, qui peuvent être modifiées après coup sans trace autre que la ligne générique de l'audit. | Décision écrite : verrouiller le volet à la signature, ou le soumettre à un déverrouillage tracé (comme SE2 pour la régularisation). | requête 1 (volets modifiés après la signature du médecin) |
| **KB-SEA-59 (3) : `ADMIN` peut écrire des observations médicales** | La route est ouverte à `ADMIN` (non médecin) comme à `MEDECIN`. Aucune règle ne rattache l'auteur à un médecin traitant ou signataire. | Décision écrite du DPO / des médecins : restreindre l'écriture aux médecins. | requête 2 |
| **KB-SEA-59 (5) : AUCUN plafond de taille sur les observations** | Les six colonnes de `volet_medical` sont de type `text` SANS borne (Flyway V1, vérifié sur PostgreSQL 16 par le script) et le legacy accepte tout (20 000 caractères au moins, golden `volet/droits_et_corps`). La reprise reproduit l'absence de plafond : aucune règle inventée ne refuse une observation clinique légitime. Risque : un corps démesuré (erreur ou abus d'un compte `ADMIN`/`MEDECIN`) est stocké tel quel. | Décision écrite des médecins / du DPO : fixer ou non un plafond (et lequel) ; tant qu'elle manque, aucune borne applicative n'existe. | requête 3 (étendre à la longueur maximale si besoin) |
| **KB-SEA-59 (4) : champs inconnus ignorés, aucune diffusion, aucune notification** | Un champ mal orthographié dans le corps est ignoré sans erreur (et efface donc la valeur voulue, cf. (1)). | Couvert par la décision (1). | — |
| **KB-SEA-60 : les séances récentes d'un patient exposent les constantes de séances `CREE` (non validées)** | `GET /patient/{id}/recentes` ne filtre pas sur le statut : poids, tension, durée, ultrafiltration d'une séance qui n'a pas été validée par l'infirmier sont servis à tout `ADMIN`, `INFIRMIER` ou `MEDECIN` du centre. Donnée de santé brute, éventuellement erronée, rendue comme faisant foi. | Décision écrite métier / DPO : ne servir que les séances validées ou signées, ou accepter explicitement l'exposition (rappel du poste infirmier). | requête 4 |

Rien de ce qui précède n'est « corrigé au passage » : la reprise reproduit le legacy et le **signale**. Aucune exception n'est corrigée pour le volet médical : même l'absence de plafond de taille est reproduite (point 5).

### B. Écarts CORRIGÉS par rapport au legacy (condition de mise en production : les métiers confirment la correction)
| KB | Correction | Condition de mise en production |
|---|---|---|
| **KB-SEA-57** (paramètres mal formés) | Journal (date absente, illisible, vide, centre absent ou illisible), calendrier (mois 0, 13, négatif, année au-delà de ±999 999 999, année ou mois absents ou texte, centre absent), ajout (corps absent, `{}`, date illisible ou absente), suppression (centre absent, identifiant illisible), volet médical (JSON illisible, corps absent, identifiant de séance illisible), séances récentes (`limit` texte, `before` illisible, identifiant de patient illisible) : **500 `INTERNAL_ERROR` devient 400 `BAD_REQUEST`**, **403 avant 400** (un profil sans droit ou un centre étranger reçoit toujours 403). L'année 0 et l'année 1900 restent acceptées (reproduit). | Prévenir les clients qui comptaient sur un 500 (aucun connu). |
| **KB-SEA-58 (partielle)** (calendrier) | Un second jour férié ou une seconde fermeture à la même date pour le même centre : **500 `DATA_ACCESS_ERROR` devient 409 `JOUR_DEJA_AU_CALENDRIER`** (la contrainte d'unicité de la base reste le garde-fou, y compris pour deux ajouts simultanés). Date absente : **400**. Libellé de plus de 255 caractères (borne de la colonne `label` / `reason`) : **400**. | Aucun risque de données. |
| Concurrence (volet médical) | La séance est lue sous verrou de ligne (`FOR UPDATE`) avant l'écriture du volet : deux saisies simultanées se suivent, verrou non obtenu dans le délai (5 s) : **409 `ECRITURE_CONCURRENTE`**. Un seul volet par séance, tenu aussi par la contrainte d'unicité de `volet_medical.seance_id`. La dernière écriture gagne toujours (remplacement total) : le verrou évite seulement l'entrelacement, il ne détecte pas l'écrasement d'une version lue plus tôt (pas de version, cf. KB-SEA-59 (1)). | — |
| Jour du centre | « Aujourd'hui » (borne `before` par défaut des séances récentes) vient de `HorlogeDuCentre` (fuseau du centre), jamais de la JVM : le legacy lisait `LocalDate.now()` du serveur dans le domaine. | — |

### C. Comportements REPRODUITS avec un signalement (sans alerte de santé)
| KB | Comportement reproduit | Décision |
|---|---|---|
| **KB-SEA-58 (suite)** : suppression d'un jour inexistant | `DELETE /calendar/holiday/{id}` et `/closure/{id}` répondent TOUJOURS `200 {"deleted":true}`, même si rien n'a été supprimé (identifiant inconnu, d'une autre table, d'un autre centre) : aucun 404, l'appelant ne sait pas si la ligne existait. Le cas d'usage rend un booléen « retiré » et le contrôleur le **journalise** (INFO, sans donnée de santé) sans changer la réponse. | REPRODUIT (parité) ; un 404 est un changement de contrat à décider avec le front. |
| **KB-SEA-58 (suite)** : aucun contrôle de cohérence | Ajouter un jour férié ou une fermeture un jour où des séances sont déjà posées ne dit rien, et rien n'est annulé ; l'an 1900 est accepté. | REPRODUIT ; la cohérence avec les séances est une décision métier distincte (SE1 : le calendrier est lu à la création d'une séance). |
| **KB-SEA-60 (partie `limit`)** | `limit` à 0 ou négatif est RAMENÉ à 1, supérieur à 10 ramené à 10, sans erreur ni alerte (comportement observable). Un patient inconnu ou d'un autre centre donne `200 []` (pas de 404, pas de fuite). L'accès est refusé à `SECRETAIRE`. | REPRODUIT. |

### D. Comportements REPRODUITS sans alerte
- **KB-SEA-61** : `sortiesArticles` du journal lit les BONS DE SORTIE du Stock datés du jour (langage publié `ConsommationsDeSeance.sortiesParDate`), pas les consommables déclarés sur les séances ; la liste des patients (code, nom, prénom) est servie, tous statuts confondus, à tout `ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` du centre.
- **Droits** : journal et lecture du calendrier : `ADMIN`, `INFIRMIER`, `MEDECIN`, `SECRETAIRE` ; écriture du calendrier : `ADMIN` seul ; volet médical : `ADMIN`, `MEDECIN` ; séances récentes : `ADMIN`, `INFIRMIER`, `MEDECIN`. Le centre doit être celui de la session (403 `ACCESS_DENIED`) ; `SUPERADMIN`, `DIRECTION` : 403 `ROLE_SCOPE`.
- **Libellés** : messages des refus du volet médical (statuts `ABSENT`, `CREE`, `FACTUREE`, accents compris), réponses `{id, seanceId, updatedAt}`, `{id, dayDate}`, `{deleted:true}`, journal `{dateSeance, patients, sortiesArticles}`, séances récentes (neuf clés).
- **Audit** : journal d'audit générique (route et statut), inchangé : il ne dépend pas du contrôleur. Aucune diffusion ni notification (volet médical comme calendrier).

## Contexte
Le net SE7a (15 tests, 17 goldens, commit be0d7107) établit que ces routes sont encore servies par la plateforme : SQL direct (`JdbcTemplate`) dans `SeanceRestController` pour le journal et le calendrier (`MERGE INTO … KEY (id)`, syntaxe H2), `VoletMedicalDomainService` + `VoletMedicalRepositoryAdapter` (JPA) pour le volet médical, `SeanceUseCase.recentByPatient` pour les séances récentes.

## Décisions
1. **Dans `backend/contexts/seance`**, mêmes tables et mêmes colonnes (`seances`, `patients` en lecture, `center_holiday`, `center_closure_day`, `volet_medical`), mêmes URL, mêmes champs JSON ; aucune migration (ADR 0024 : pas de façade ni de drapeau ; la bascule est le retrait des handlers legacy, réversible par retour git).
2. **Propriété du calendrier du centre (à confirmer métiers)** : il **RESTE dans le module Séance** pour cette slice (les routes y vivent), sans créer de dépendance vers l'Organisation maintenant. Résidu nominatif daté : **« transfert du calendrier à l'Organisation (slice O4 lectures publiées / paramétrage du centre) », butoir 2027-03-31**, revue 2026-12-15.
3. **Domaine (pur)** : `JourDuCalendrier` (jour férié ou fermeture exceptionnelle, libellé borné) avec `NatureDeJour`, `MoisDuCalendrier` (mois et année validés), `JourDejaAuCalendrier` ; `VoletMedical` (agrégat à une entité, séance référencée par identité) et `ObservationsMedicales` (six observations, sans plafond de taille : colonnes `text` non bornées) ; `Seance.exigerUnVoletMedical` (ABSENT, CREE, FACTUREE refusés dans cet ordre) ; `NombreDeSeancesRecentes` (1 à 10).
4. **Application** : `ConsulterLeJournalDuJour`, `ConsulterLeCalendrier`, `AjouterUnJourAuCalendrier`, `RetirerUnJourDuCalendrier`, `RenseignerLeVoletMedical` (verrou de ligne de la séance, unité de travail), `ConsulterLesSeancesRecentes`. Ordre des refus : centre (403), saisie (400), séance (400), statut (422). Aucune règle dans les cas d'usage.
5. **Ports** : `CalendrierDuCentre`, `VoletsMedicaux` (domaine), `Seances.dernieres` (ajouté au dépôt existant), `PatientsDuJour` et `SortiesDuJour` (application), `VoletsDeLaSeance.paramedical` (existant) pour les constantes.
6. **Adapters** : `CalendrierDuCentreJdbc`, `VoletsMedicauxJdbc`, `SeancesJdbc.dernieres` (persistance) ; ACL `PatientsDuJourJdbc` (`acl/legacypatient`, lecture de `patients`) et `SortiesDuJourDuStock` (langage publié du Stock). Contrats : double en mémoire, H2 et PostgreSQL 16 jetable (`backend/scripts/seance-se7a-pg16-rejeu.sh`). L'ACL `PatientsDuJourJdbc` n'a pas de contrat PostgreSQL propre : sa requête est exécutée sur le schéma réel par le script (SELECT seule) et testée sur H2.
7. **Pas d'événement d'intégration** (aucun consommateur) ; à créer avec le premier consommateur (Outbox, `seance.v1`).

## Résidus nominatifs (revue ≤ 2026-12-15 ET butoir propre pour chacun)
| # | Résidu | Revue | Butoir |
|---|---|---|---|
| R1 | Calendrier du centre dans la Séance : transfert à l'Organisation (O4 lectures publiées / paramétrage du centre) ; le front et la création de séance (SE1) lisent le même calendrier | 2026-12-15 | 2027-03-31 |
| R2 | ACL `PatientsDuJourJdbc` lit directement `patients` (inscrite, nommée et datée, dans la liste blanche de `TableDuPatientCouvertureTest`, comme les autres jointures séances-patients) : à remplacer par un langage publié du Patient (code, nom, prénom d'un lot de patients ; `FichesDesPatients.noms` n'expose pas le code) | 2026-12-15 | 2027-03-31 |
| R3 | Constantes des séances récentes lues par `VoletsDeLaSeance.paramedical` (ACL du legacy, une lecture par séance, au plus 10) : à remplacer à la reprise du volet paramédical (DM1) | 2026-12-15 | 2027-06-30 |
| R4 | Décisions KB-SEA-59 (remplacement total, route ouverte sur séance signée, `ADMIN` écrit, aucun plafond de taille) et KB-SEA-60 (constantes `CREE`) : arbitrage médecins / DPO | 2026-12-15 | avant toute mise en production |
| R5 | Bornes du calendrier et cohérence avec les séances déjà posées (KB-SEA-58) ; 404 sur suppression inexistante | 2026-12-15 | 2027-03-31 |
| R6 | Audit des routes résiduelles : golden `se7/audit` non rejoué contre le legacy retiré (filtre générique non modifié) ; à lever par une relecture du journal d'audit après déploiement | 2026-12-15 | 2027-01-31 |

## Lots
LOT A (livré 2026-11-08) : cet ADR, domaine, cas d'usage avec doubles, ports, ACL, adapters JDBC et contrats (mémoire, H2, PostgreSQL 16), câblage des cas d'usage ; AUCUN trafic changé (les handlers legacy sont encore actifs). LOT B : contrôleur et advice, retrait des handlers legacy (`SeanceRestController` journal et calendrier, `SeanceStationRestController`, `VoletMedicalRestController`), parité 15/15, écarts approuvés un par un, verrou ArchUnit. LOT C : RG-SEA, annexe 16, glossaire, §59, bilan des résidus, `built`.

LOT B (livré 2026-11-08, décisions à confirmer par le coordinateur) : `RoutesResiduellesControleur` (6 cas d'usage), `ErreursDeSeanceAdvice` étendu (403 avant 400 ; 409 `JOUR_DEJA_AU_CALENDRIER`), handlers legacy retirés (journal, calendrier x5 dans `SeanceRestController` ; suppression de `SeanceStationRestController`, `VoletMedicalRestController`, `VoletMedicalUseCase`, `VoletMedicalDomainService`, `SeanceRecap`, `recentByPatient`, `findRecentByPatient`) ; le tableau de bord et le volet paramédical restent legacy. Seam inscrit dans `seams.md` puis refermé. Verrou ArchUnit `seul_contexts_seance_sert_les_routes_residuelles` sans exception.
Parité : 15 tests, 17 goldens ; 9 goldens identiques au legacy, 8 écarts approuvés un par un (legacy archivé en R100 sous `se7/_ecarts-legacy/<dossier>/<nom>_avant_se7a.legacy.json`, vérifié contre `git show be0d7107:<chemin>`) :
| Écart | Legacy | Nouveau | Décision |
|---|---|---|---|
| Paramètre ou corps absent ou illisible (journal, calendrier, retrait, séances récentes, volet) | 500 `INTERNAL_ERROR` | 400 `BAD_REQUEST` (403 avant 400) | CORRIGÉ (KB-SEA-57, 60) |
| Mois hors bornes (0, 13, négatif, année 1000000000) | 500 | 400 | CORRIGÉ (KB-SEA-57) ; année 0 et 1900 restent 200 (reproduit) |
| Jour férié ou fermeture en double | 500 `DATA_ACCESS_ERROR` | 409 `JOUR_DEJA_AU_CALENDRIER` | CORRIGÉ (KB-SEA-58) |
| Jour sans date, libellé de 2000 caractères | 500 | 400 (« Jour requis », libellé borné à 255) | CORRIGÉ (KB-SEA-58) |
| Corps `{}` du calendrier ou du volet sans centre | 500 / 400 « CenterId cannot be null » | 400 « Centre requis » | CORRIGÉ (sans nom de classe) |
| Volet médical sur séance au statut inconnu (`EN_COURS`) | 400 avec le nom de la classe d'énumération | 400 « valeur illisible : statut inconnu » | CORRIGÉ (fuite de nom de classe) |
Reproduits sans écart : retrait d'un jour inexistant = 200 `deleted:true` (KB-SEA-58, signalé), remplacement total du volet, route ouverte sur séance signée, aucun plafond de taille (KB-SEA-59), `limit` ramené à 1..10 (KB-SEA-60), constantes des séances `CREE` exposées (KB-SEA-60), droits par profil, 403 des profils sans droit.
Audit : le journal d'audit est un filtre générique du gabarit de route, indépendant du contrôleur ; nouveau golden `se7/audit` (ajout, doublon 409, retrait, volet 200 et 403 sont journalisés avec la route et le statut ; les lectures ne le sont pas). Seul le statut du doublon change (500 vers 409). Le golden n'a pas pu être rejoué contre le legacy retiré : l'identité du comportement d'audit repose sur le filtre générique (non modifié).
