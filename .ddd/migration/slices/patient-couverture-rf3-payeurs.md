---
slice: patient-couverture-rf3-payeurs
status: built (RF3a, RF3b et RF3c built ; résiduels nommés : ADR 0128 ; confirmation métier avant 2026-12-15)
date: 2026-11-10
order: 8
context: patient-couverture
sources: [ADR 0121, ADR 0024, ADR 0079, ADR 0097, .ddd/migration/reviews/analyse-referentiels-orphelins.md]
---

# RF3 - Caisses d'assurance, agences et centres payeurs chez Patient & couverture

> Propriété décidée par l'ADR 0121 (décision 2 : le centre payeur est un attribut du patient, `patients.centre_payeur_id`), **à confirmer métiers <= 2026-12-15**. Contredit `facturation.md:17-18` (écart à corriger, glossaire Facturation `proposed`). Refactoring SUR PLACE : trois tables chaînées `caisse_assurance` -> `agence` -> `centre_payeur`, mêmes URLs, comportement inchangé. À faire en DERNIER (le plus de lecteurs). Trois sous-slices a, b, c.

## Comportement observé à caractériser
| # | Scénario | Source |
|---|---|---|
| 1 | CRUD admin `/api/v1/admin/referentials/{caisses,agences,centres-payeurs}` : rôles, garde de centre | `ReferentialAdminTables.java:48-67` |
| 2 | Hiérarchie : un centre payeur exige une agence, une agence exige une caisse ; clé `code` unique par centre pour chaque niveau ; `type_caisse` STANDARD ou VACANCIER ; `adresse` du centre payeur | `ReferentialKind.java:56,62,69`, schema.sql:4-27 |
| 3 | Suppression refusée si référencé : agence par une caisse, centre payeur par une agence, patients par un centre payeur : messages | `ReferentialAdminTables.java:51,58,66` |
| 4 | Import CSV/Excel : ordre 1 caisses, 2 agences, 3 centres payeurs ; rejet d'un parent absent ; vérification, rejeu, refus | `ReferentialKind.java:58,64` |
| 5 | Lectures `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` (garde de centre 403, forme `RefItem`) | `ReferentielsLegacyControleur.java:75-133`, `ReferentielsCommunsJdbc.java:33-90` |
| 6 | Affectation d'un centre payeur à un patient (fiche) : existence et centre | `PatientsJdbc.java:39,50` |
| 7 | Figeage de l'agence et du centre payeur à l'émission (instantanés) : non rafraîchis par une modification du référentiel | `SituationsDesFacturesJdbc.java:33-35`, `LectureDeLaSyntheseJdbc.java:53-67` |
| 8 | Chaîne payeur bornée à tous les étages (`ag.center_id = cp.center_id`, ADR 0120) | `PatientsDesCentresJdbc`, `SeancesRealiseesDesCentresJdbc` |
| 9 | Invalidation des caches `ref.*` (trois types) | `CacheConfig.java:48-57` |

## Goldens à prévoir
Environ 45 (3 tables x CRUD, hiérarchie, doublons, suppressions refusées par niveau, import ordonné, lectures dont `-details`, cache). Les goldens des lecteurs migrés (Séance, Pilotage, Facturation, Comptabilité, reporting) restent INCHANGÉS : preuve de parité. Rejeu PG16.

## Langage publié à créer (`patientcouverture.v1`)
`PayeursDuCentre` : `caisses(centre)`, `agences(centre, caisse)`, `centresPayeurs(centre)`, `detail(centre, centrePayeur)` (centre payeur + agence + caisse), `chaine(centre, centrePayeur)` (agence et caisse du centre payeur, borné à chaque étage), `existe(centre, centrePayeur)`. Usage à la suppression : lecture interne PC (patients) + `UsageDeLAgence` interne ; aucun autre contexte ne référence ces tables par FK (les instantanés de Facturation sont des copies, pas de FK : à vérifier).

## Sous-slices
| Sous-slice | Contenu | Effort |
|---|---|---|
| RF3a | CRUD + import + invalidation de cache + retrait des 3 types de l'admin générique | 1 session |
| RF3b | Langage publié `PayeursDuCentre` + migration des lecteurs « annuaire » : Séance (`SeancesRealiseesDesCentresJdbc`, `FacturationDesSeancesJdbc` si nom seul), Pilotage `PatientsDesCentresJdbc`, reporting platform `FacturationSyntheseMensuelleReportService.java:113-114`, Absences | 1 session |
| RF3c | Comptabilité lit `PayeursDuCentre` publié (ADR 0121 décision 3 révisée : lien `comptabilite -> patient-couverture` à ajouter, réacteur sans cycle à prouver), Facturation : jointures `GROUP BY` en jalons datés, retrait du code Organisation (`ReferentielsCommunsJdbc`, partie payeurs) ; inventaire de `CentrePayeurJpaEntity` et `CaisseAssuranceJpaEntity` | 1 session |

## Réalisation
| Sous-slice | État | Détail |
|---|---|---|
| RF3a | **built** (2026-11-10, ADR 0126) | Filet HTTP : 24 tests, 45 goldens, verts sur le legacy AVANT tout changement, puis à parité SANS golden modifié (aucun `_nouveau_code`, aucune archive `_ecarts-legacy`). Domaine pur (`CaisseDAssurance`, `Agence`, `CentrePayeur`, `Payeur`), cas d'usage `GererLesPayeurs`, langage publié `GestionDesPayeurs` + `RefusDePayeur`, JDBC `CaissesJdbc`/`AgencesJdbc`/`CentresPayeursJdbc`/`LectureDesPayeursJdbc` sur les MÊMES tables, invalidation par niveau après commit, ACL `PayeursParPatientCouverture`. Garde `TableDesPayeursTest` (effectif 11). Contrat unique fake / H2 / PG16 (rejeu `backend/scripts/patient-couverture-rf3-pg16-rejeu.sh`). KB-RF3-01 à 05 reproduits. |
| RF3b | **built** (2026-11-10, ADR 0127) | Langage publié `PayeursDuCentre` (`caisses`, `agences`, `centresPayeurs`, `detail`, `chaines`, `existe` ; ajusté : voir ADR 0127 décision 2), adaptateur `PayeursDuCentreJdbc`, contrat unique double en mémoire / H2 / PG16 (6 cas, rouge vérifié avant le code). Lecteurs migrés (aucun golden modifié) : Séance `SeancesRealiseesDesCentresJdbc` et `FacturationDesSeancesJdbc` (agence du centre payeur), Pilotage `PatientsDesCentresJdbc`, impression `FacturationSyntheseMensuelleReportService`. Absences : aucune entrée dans la liste blanche, rien à migrer. Garde `TableDesPayeursTest` : effectif 11 -> 7. Les lectures `/referentials/*` d'Organisation restent à RF3c. |
| RF3c | **built** (2026-11-10, ADR 0128) | Aucun cycle Maven (preuve dans l'ADR, `validate` du réacteur). Comptabilité : `PayeursDuCentreJdbc` remplacé par l'ACL `PayeursParLAnnuaire` (contrat `PayeurContrat` inchangé : double / H2 / PG16). Facturation : `RepriseDesSoldesJdbc` migré (port `AgenceDuPayeur`, ACL `AgenceDuPayeurParLAnnuaire`, 2 cas de contrat ajoutés) ; quatre lecteurs `GROUP BY` / instantané MAINTENUS (`SituationsDesFacturesJdbc`, `LectureDeLaSyntheseJdbc`, `LectureDuTableauDeBordJdbc`, `SyntheseFinanciereDesCentresJdbc`, RF3c-J1, butoir 2027-03-31). Organisation : lectures `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` et doublons `/organisation/referentiels/...` servies par PC (`PayeursControleur`, `ChoixDesPayeursJdbc`, mêmes requêtes, mêmes caches `ref.*`) ; `ReferentielsCommunsJdbc` et son port retirés. Garde `TableDesPayeursTest` : 7 -> 4. Aucun golden modifié. Entités JPA : conservées (RF3c-R2). `ReferentialKind` : descripteurs conservés (RF3-R1). |

Résiduels RF3c : voir l'ADR 0128 (RF3c-J1, R1 à R4). Résiduels RF3b : voir l'ADR 0127 (RF3b-R1 à R5). Résiduels RF3a : voir l'ADR 0126 (RF3-R1 à R5). RF3d (2026-11-10, ADR 0129, à confirmer métiers <= 2026-12-15) : KB-RF3-01 à 03 CORRIGÉS (400 `BAD_REQUEST` sur corps, identifiant, centre et page illisibles de TOUTE l'administration générique, et sur `centerId` absent des lectures communes des payeurs et de `categories-transport`) ; 9 goldens re-approuvés en `_nouveau_code` (anciens `_avant_rf3d`), 2 nouveaux (`admin-generique`), aucun golden d'un autre contexte modifié ; rejeu PG16 vert (port 55801).
RF4 (2026-11-10, ADR 0130, traitement de la revue indépendante RF1-RF3d, à confirmer métiers <= 2026-12-15 pour le compte d'usage) : KB-RF3-05 CORRIGÉ (compte d'usage des payeurs borné au centre ; port `UsageDesPayeurs.nombreDeReferences(centre, niveau, id)` ; goldens `saisie-*/suppressions` inchangés ; rejeu PG16 vert, contrat des payeurs 21 tests, port 55810) ; KB-RF4-01 CORRIGÉ (code de caisse = paramètre Jasper dans la synthèse dynamique) ; gabarit statique de la synthèse inscrit à la liste blanche de `TableDesPayeursTest` (effectif 5, KB-RF4-02, RF3c-J1) ; butoirs des gardes `TableDes*Test` exécutables (dates comparées à l'horloge, exclusion `/entity/` nominative) ; tri des payeurs de Comptabilité départagé par identifiant (KB-RF4-03 : annuaire entier chargé par page, consigné). Dérogation `approve=true` de RF3a tracée dans l'ADR 0126 (addendum). Aucun golden modifié, aucun seam, aucun flag.
RF5 (2026-11-10, ADR 0131, revue complémentaire RF1-RF3d) : ordre de `parCaisse` / `parMois` garanti (KB-RF5-02), nom de caisse « à collation près » (KB-RF5-01, javadoc corrigée), 400 des listes de choix à message neutre ou liste blanche (KB-RF5-03), garde d'architecture `@PreAuthorize` sur `/api/v1/admin/referentials/**` et refus par défaut (KB-RF5-04, aucune route sans garde), motif ancré (KB-RF5-05). Goldens INCHANGÉS, SQL inchangé (pas de rejeu PG16). Points de la revue non traités : voir l'ADR 0131.

## Garde de table nominative
`TableDesPayeursTest` : tables `caisse_assurance`, `agence`, `centre_payeur` ; scanne tout le code de production hors PC (entités JPA exceptées, à inventorier en RF3c) ; liste blanche nominative et DATÉE, effectif asserté, échec sur entrée morte. Entrées attendues à l'ouverture : Facturation (`SituationsDesFacturesJdbc`, `LectureDeLaSyntheseJdbc`, `LectureDuTableauDeBordJdbc`, `SyntheseFinanciereDesCentresJdbc`, `RepriseDesSoldesJdbc`), Séance (2), Pilotage (1), platform reporting (1), **Comptabilité `PayeursDuCentreJdbc` (ACL décidée par l'ADR 0121, décision 3, nominative et datée)**, `ReferentialAdminJdbcAdapter`, `ReferentielsCommunsJdbc`. Cible : seules Comptabilité (ACL) et les jointures `GROUP BY` de Facturation restent, chacune avec raison et butoir.

État de la garde après RF3c (2026-11-10) : 4 entrées, toutes des jointures de la Facturation sur l'instantané figé de la facture, chacune avec raison et butoir 2027-03-31 (ADR 0128). La Comptabilité (ACL) a disparu de la liste : elle lit `PayeursDuCentre`.

## Conditions de retrait du CRUD générique pour ces types
1. Filet RF3 vert (CRUD, hiérarchie, import ordonné à parité).
2. `ReferentialKind` sans `CAISSE`, `AGENCE`, `CENTRE_PAYEUR` ; `ReferentialAdminTables` sans les trois entrées ; `COUNT` d'usage remplacés par des lectures de PC.
3. Lectures `/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}` servies par PC ; code Organisation retiré.
4. Garde RF3 verte (effectif asserté), build complet vert, `check-arch` 0 violation, PG16 rejoué, aucun golden de lecteur modifié.
5. Confirmation métier de la décision 2 (ou ADR 0121 reconduit).
Retour arrière : revert Git.

Bilan RF3c (2026-11-10) : conditions 1, 3 et 4 tenues (jointures `GROUP BY` : jalons datés, voir ADR 0128) ; condition 2 PARTIELLE : `ReferentialAdminTables` ne nomme plus les trois tables (RF3a), mais `ReferentialKind.CAISSE/AGENCE/CENTRE_PAYEUR` restent comme descripteurs de fichiers d'import et pour la reprise des patients (RF3-R1, comme `TRANSPORTEUR` et `FORFAIT`) ; condition 5 (confirmation métier) EN ATTENTE, échéance 2026-12-15.

## Effort
3 sous-slices (environ 3 sessions). Risque élevé : plus gros nombre de lecteurs, jointures `GROUP BY` à ne pas découper, trois tables chaînées.

## Questions ouvertes
1. Le centre payeur est-il vraiment un attribut du patient (PC) et non de la facture ? Le glossaire Facturation dit l'inverse (`facturation.md:17-18`) ; correction `proposed` ajoutée.
2. Comptabilité : TRANCHÉE le 2026-11-10 (utilisateur) : `PayeursDuCentre` publié (ADR 0121, écart de vérification : aucun cycle Maven).
3. Termes « Caisse d'assurance », « Agence », « Centre payeur » à ajouter au glossaire de PC (`proposed`) ; homonyme « centre » seul (Organisation) à garder distinct (`organisation.md:31`).
4. `CentrePayeurJpaEntity` et `CaisseAssuranceJpaEntity` : INVENTORIÉES en RF3c, aucune référence mais conservées (elles définissent le DDL H2 sous `ddl-auto=update` : RF3c-R2, ADR 0128) ; `AgenceJpaEntity` idem.
5. La Facturation doit-elle lire la chaîne payeur par un langage publié groupé (RF3c-J1, butoir 2027-03-31) ?
