---
status: draft
date: 2026-11-10
sources: [O5 l.69, O3, .ddd/strategic/{bounded-contexts,context-map}.md, glossaires facturation / seance / patient-couverture / organisation, code cité]
---

# Référentiels orphelins : contexte propriétaire (analyse statique, lecture seule)

Périmètre : l'admin générique `ReferentialKind` expose 9 types (`platform/.../referential/admin/model/ReferentialKind.java:23-74`). Cinq sont orphelins, mais « payeurs » recouvre en réalité TROIS tables chaînées (caisse -> agence -> centre payeur), soit 7 tables au total avec transporteur et forfait.

## Tableau synthèse

| Référentiel | Table(s) | Écrit par | Lu par (SQL direct) | Recommandé | Alternative | Effort |
|---|---|---|---|---|---|---|
| Payeurs (caisse, agence, centre payeur) | `caisse_assurance`, `agence`, `centre_payeur` (schema.sql:4-27) | admin générique uniquement (`ReferentialAdminTables.java:48-67`) ; seed démo | Facturation (6 fichiers), Séance (2), Comptabilité (1), Pilotage (1), Organisation (lecture `/referentials`), reporting platform (1) | **Facturation** | Patient & couverture | 3 slices |
| Forfaits | `forfait` (schema.sql:115) | admin générique (`ReferentialAdminTables.java:19-26`) | Séance (2 ACL), Facturation (1 ACL), Patient & couverture (2), Organisation (lecture) | **Patient & couverture** | Facturation | 2 slices |
| Transporteurs | `transporteur` (schema.sql:102) | admin générique (`ReferentialAdminTables.java:69-73`) | Patient & couverture (1), Organisation (lecture) | **Patient & couverture** | Organisation (le garder) | 1 slice |

## Constats communs

- **Un seul écrivain** : le CRUD et l'import CSV/Excel de `ReferentialAdminRestController` (`/api/v1/admin/referentials`, ADMIN/SUPERADMIN, garde de centre : ligne 47-49). Aucun `INSERT/UPDATE/DELETE` dans les contextes sur ces 5 tables (recherche `INTO|UPDATE` sans résultat hors seed). Les contextes ne font que lire. L'écriture est donc triviale à déplacer ; la difficulté est la lecture inter-contextes.
- **Lectures HTTP** : `ReferentielsLegacyControleur` (organisation, `/api/v1/referentials/{transporteurs,forfaits,centres-payeurs,centres-payeurs-details,caisses,agences}`, lignes 75-133) via `ReferentielsCommunsJdbc.java:33-90` (SQL direct, caches `ref.*`). Cette classe lit déjà des tables dont Organisation n'est pas propriétaire : c'est un « annuaire commun ».
- **Caches** `ref.*` (`CacheConfig.java:48-57`) invalidés par l'adaptateur admin (`ReferentialAdminTables` : liste de caches par type). Le propriétaire doit reprendre cette invalidation.
- **Contrôles d'usage à la suppression** (`Usage`, `ReferentialAdminTables.java:24,31,39,51,58,66,72`) : le CRUD compte les références dans d'autres tables (patients, prise_en_charge, facture_lignes, agence...). Le déplacement impose de remplacer ces `COUNT` par des lectures publiées (déjà fait pour le forfait imposé à une séance, `UsagePublie`, SE7e, ligne 88).
- **Graphe Maven actuel** (pom des contextes) : `organisation` n'a aucune dépendance de contexte ; `facturation -> comptabilite, patient-couverture` ; `seance -> organisation, patient-couverture` ; `absences -> comptabilite, facturation, patient-couverture` ; `pilotage -> organisation, seance, facturation` ; `comptabilite` n'en a aucune ; `patient-couverture -> organisation` (pom:85, portée non vérifiée).

## Payeurs (caisse, agence, centre payeur)

- **Table(s)** : `caisse_assurance(type_caisse STANDARD|VACANCIER)`, `agence(caisse_id)`, `centre_payeur(agence_id, adresse)`, toutes à `center_id` (schema.sql:4-27). `patients.centre_payeur_id` pointe le centre payeur (`PatientsJdbc.java:39,50`).
- **Aggregate / invariants** : hiérarchie 3 niveaux, clé naturelle `code` unique par centre (`ReferentialKind.java:56,62,69`), un centre payeur exige une agence, une agence exige une caisse (ordre d'import 1/2/3, lignes 58,64). Suppression refusée si référencé (agence par caisse, centre payeur par agence, patients par centre payeur). Aucun comportement métier : agrégat « Payeur » = arbre de référence, pas de transition d'état.
- **Lit** : Facturation figeage de l'agence et du centre payeur à l'émission (`agence_id_snapshot`, `centre_payeur_id_snapshot` : `SituationsDesFacturesJdbc.java:33-35`, `LectureDeLaSyntheseJdbc.java:53-67`, `LectureDuTableauDeBordJdbc.java:64`, `SyntheseFinanciereDesCentresJdbc.java:78`, `RepriseDesSoldesJdbc.java:93`) ; reporting platform `FacturationSyntheseMensuelleReportService.java:113-114` ; Séance (`SeancesRealiseesDesCentresJdbc.java:28-30`, `FacturationDesSeancesJdbc.java:54`) ; Pilotage (`PatientsDesCentresJdbc.java:32-34`) ; Comptabilité (`PayeursDuCentreJdbc.java:37,58`).
- **Routes** : `/api/v1/admin/referentials/{caisses,agences,centres-payeurs}` (CRUD, import, template) ; lectures `/api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}`.
- **Glossaire** : Facturation « Payeur » (`facturation.md:17`, ancien nom `payeurs`/`centre_payeur`, homonyme « centre » seul) et « Caisse d'assurance / Agence » (`facturation.md:18`, `CaisseAssurance`, `Agence`, tables citées) ; `organisation.md:31` : « *Centre* (ici) ≠ *Centre payeur* (Facturation) » ; bounded-contexts.md:24,91 place « payeur/caisse/agence » dans Facturation.
- **Recommandation : Facturation** (langage déjà décidé, plus gros lecteur, ventilation par caisse). Alternative : Patient & couverture (propriétaire de `patients.centre_payeur_id`, plus bas dans le graphe Maven).
- **Risques** : (1) `comptabilite` lit `centre_payeur` mais Facturation dépend de Comptabilité (`facturation/pom.xml:28`) : Comptabilité ne peut PAS consommer l'API publiée de Facturation (cycle) ; elle garde sa lecture SQL en ACL, ou le port est publié dans shared-kernel ; (2) Patient & couverture écrit `patients.centre_payeur_id` sans pouvoir dépendre de Facturation (cycle via `facturation -> patient-couverture`) : il valide par un port lu en SQL ; (3) Séance, Pilotage et Absences doivent migrer vers un langage publié de Facturation (Séance n'en dépend pas aujourd'hui ; Pilotage oui).
- **Effort** : 3 slices : (a) CRUD + import + invalidation de cache + suppression des 3 types dans l'admin générique ; (b) langage publié `PayeursPublies` + migration des lectures Séance/Pilotage/platform ; (c) Comptabilité et Patient & couverture en ACL + retrait du code Organisation (`ReferentielsCommunsJdbc` partie payeurs).

## Forfaits

- **Table** : `forfait(code, libelle, prix DECIMAL(10,2) nullable)` (schema.sql:115-121). Clé naturelle `code` (`ReferentialKind.java:27`). Références : `prise_en_charge.forfait_demande_id/forfait_effectif_id`, `facture_lignes.forfait_id`, forfait imposé à une séance (copie, pas de FK ; `ReferentialAdminTables.java:24-25`).
- **Écrit** : admin générique seulement. Prix nullable, pas de validité temporelle (hypothèse : à vérifier).
- **Lit** : Séance `ForfaitsDuCentreJdbc.java:27` (ACL `legacyreferentiel`) et `ForfaitsDeSeanceJdbc.java:27` ; Facturation `ForfaitsDuCentreJdbc.java:25` (ACL `legacyseance`) ; Patient & couverture `CouvertureDesPatientsJdbc.java:23,90` (jointure prix de PEC) ; Organisation lecture `/referentials/forfaits`.
- **Routes** : `/api/v1/admin/referentials/forfaits` ; `GET /api/v1/referentials/forfaits` (`ReferentielsLegacyControleur.java:85`).
- **Invariants** : suppression refusée si utilisé (PEC, lignes de facture, séances). Le forfait imposé est un instantané non rafraîchi (`seance.md:42`) : la propriété du catalogue n'affecte pas l'historique.
- **Glossaire** : Patient & couverture « Forfait de PEC : prix HT de la séance prévu par la PEC » (`patient-couverture.md:19`, synonyme interdit « tarif ») ; Facturation « Forfait appliqué » (`facturation.md:16`, priorité surcharge > PEC > PEC demandée) ; Séance « Forfait du centre » (`seance.md:43`, port `ForfaitsDuCentre`). Trois sens du mot : le catalogue lui-même n'a pas de terme propre : à nommer.
- **Recommandation : Patient & couverture** : tous les consommateurs (Facturation, Séance, Absences) dépendent déjà de lui en Maven, donc aucun cycle ; la PEC est le point d'ancrage tarifaire. Alternative : Facturation (plus naturel côté « tarif », mais Séance n'en dépend pas, et Patient & couverture ne peut pas le lire sans cycle : sa jointure `CouvertureDesPatientsJdbc` casserait).
- **Risque** : sens commercial (« tarif ») plutôt que médical dans un contexte clinique ; à trancher avec les experts. La jointure de prix reste interne si PC est propriétaire.
- **Effort** : 2 slices : (a) CRUD + import + usages (le comptage `UsagePublie` Séance reste) ; (b) langage publié `ForfaitsDuCentre` unique remplaçant les deux ACL JDBC dupliquées (Séance, Facturation) + retrait Organisation.

## Transporteurs

- **Table** : `transporteur(nom, telephone)` (schema.sql:102-107) ; sœur non listée `categorie_transport(libelle)` (schema.sql:109) et lue par `/referentials/categories-transport` (ligne 80) : candidat de même propriétaire.
- **Écrit** : admin générique. Clé naturelle `nom` (`ReferentialKind.java:74`) ; type `phone` validé.
- **Lit** : Patient & couverture `LectureDesPatientsJdbc.java:156` (nom affiché) et filtres `83-85` ; Organisation lecture `ReferentielsCommunsJdbc.java:74`. Usage : `patients.transporteur_aller_id/retour_id` (`ReferentialAdminTables.java:72`). Aucun autre contexte.
- **Routes** : admin `/transporteurs` ; lecture `GET /api/v1/referentials/transporteurs` (ligne 75).
- **Invariants** : aucun hors unicité du nom et refus de suppression si utilisé (aller ou retour).
- **Glossaire** : absent des glossaires (aucun terme « transporteur » dans patient-couverture, planification, pilotage) : terme à ajouter (statut `proposed`).
- **Recommandation : Patient & couverture** (unique consommateur métier : fiche patient). Alternative : conserver dans Organisation en « annuaire commun » (aucun coût, aucun cycle).
- **Risque** : faible. Le déplacement n'apporte de la valeur que si l'on supprime l'écran générique.
- **Effort** : 1 slice (avec `categorie_transport`).

## Décision de séquencement suggérée

1. Transporteurs (+ catégories de transport) puis Forfaits chez Patient & couverture : sans cycle.
2. Payeurs chez Facturation en dernier : le plus de lecteurs, cycle Comptabilité à traiter par ACL.
3. L'écran générique `/api/v1/admin/referentials` reste en façade routant vers chaque propriétaire tant que les URLs sont conservées (ADR 0024 : mêmes URLs, rôles ADMIN/SUPERADMIN, garde de centre).

## Limites de l'analyse

Statique : pas de lecture des dépendances Maven par portée (compile ou test non vérifiée pour `patient-couverture -> organisation`, `seance -> organisation`) ; pas d'exécution de `check-arch` ni du scan ; les routes frontend et les lecteurs par ORM (`CentrePayeurJpaEntity`, `CaisseAssuranceJpaEntity` dans `platform/.../persistence/entity`, non analysés) ne sont pas cartographiés ; les lecteurs JPA peuvent donc s'ajouter à la liste. Les comptes de fichiers viennent de Grep sur `src/main` uniquement ; les rapports Jasper et le module migration (`HistoryRules`, `MigrationEntity` référencent les tables) n'ont pas été inspectés.

## Questions ouvertes

1. Qui est le « métier » du tarif : Facturation (prix) ou Patient & couverture (accord PEC) ? Faut-il un terme « Catalogue de forfaits » propre ?
2. Le centre payeur est-il un attribut du patient (couverture) ou de la facture (Facturation) ? Le glossaire dit Facturation ; le patient le porte (`patients.centre_payeur_id`).
3. Accepte-t-on des tables `center_id` isolées par centre dupliquées par centre (modèle actuel) ou un référentiel partagé ? (hors périmètre du déplacement).
4. Comptabilité : lecture SQL en ACL acceptée pour les payeurs (cycle Maven), ou port publié dans shared-kernel ?
5. `CentrePayeurJpaEntity` et `CaisseAssuranceJpaEntity` (platform) : qui les utilise ? À traiter avec `SocieteJpaEntity` (O5 l.70).
6. `forfait.prix` nullable : sens métier d'un forfait sans prix (KB-SEA-45/47 le tolèrent) ?
7. Les données migrées (`HistoryRules`, `MigrationEntity`) écrivent-elles ces tables ? Si oui, c'est un second écrivain.
