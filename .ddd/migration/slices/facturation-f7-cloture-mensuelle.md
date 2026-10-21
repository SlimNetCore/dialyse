---
slice: facturation-f7-cloture-mensuelle
status: built
date: 2026-10-21
order: 7 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md (F16, mois de facturation), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-040 à 045), ADR 0009, 0013, 0035, 0036, 0037, slices F2 et F3, clôture comptable RG-CPT-005]
---

> **Nouvelle fonctionnalité, refonte sur place (ADR 0024)** : aucun legacy à caractériser, donc pas de filet ni de parité ; ni façade,
> ni routage, ni projection, ni métriques. Migration **additive** V42. Retour arrière = commit précédent (les tables restent, sans effet).

# F7 — Clôture mensuelle de facturation

## Périmètre

Un administrateur clôture un mois de facturation d'un centre : plus aucune facture n'y est émise, plus aucune séance du mois n'y est
exclue, réincluse ou changée de forfait. La clôture se rouvre, avec un motif, tant que la comptabilité du mois n'est pas close.

## Règles décidées par l'utilisateur (2026-10-21) et réalisation

| # | Règle | Réalisation |
|---|---|---|
| 1 | Grain : un mois (année-mois) par centre | agrégat `MoisDeFacturation` (identité centre + mois) ; table `periodes_facturation`, clé (centre, année, mois) |
| 2 | Mois clos : validation, exclusion, réinclusion, changement de forfait refusés (422 `PERIODE_DE_FACTURATION_CLOTUREE`) ; simulation, lectures, règlements permis | `GardeDesMoisClos` appelée par `ValiderLaFacturation` (tout mois couvert par l'intervalle) et par `ExclureUneSeance`, `ReinclureUneSeance`, `ChangerLeForfaitDUneSeance` (mois de la **date de la séance**) |
| 3 | Aucune précondition ; avertissement chiffré ; double clôture | `CloturerLeMois` compte `SeancesFacturables.dansLaPeriode(mois)` ; **409 `PERIODE_DEJA_CLOTUREE`** (choisi, comme la clôture comptable) |
| 4 | Réouverture ADMIN, motif obligatoire, tracée ; refusée si la comptabilité du mois est close ; 409 si non close | `RouvrirLeMois` ; port publié `PeriodesComptables` (Comptabilité) via l'ACL `ClotureComptableComptabilite` ; journal `periodes_facturation_journal` |
| 5 | Événements `PeriodeDeFacturationCloturee` et `PeriodeDeFacturationRouverte`, sans consommateur | publiés dans la transaction (`PublicationDesPeriodesSpring`) ; **aucun écouteur** : le registre Modulith n'enregistre une publication que par écouteur transactionnel, donc rien n'est laissé incomplet |
| 6 | API ADMIN seul, 403 si le centre diffère de la session, auteur = session, `code` + `detail` | `PeriodesDeFacturationControleur`, `ClotureDeFacturationAdvice` |
| 7 | Migration additive, verrou pessimiste sérialisant clôture et émission | V42 + `schema.sql` ; `MoisDeFacturationJdbc.verrouiller` (`SELECT ... FOR UPDATE`) |

Précisions de conception : (a) l'agrégat s'appelle `MoisDeFacturation` car `PeriodeDeFacturation` est déjà l'intervalle de dates d'une
simulation ; (b) la ligne du mois est créée **ouverte** au premier verrouillage (validation ou modification incluses), pour avoir quelque
chose à verrouiller ; c'est sans effet métier ; (c) un mois clos garde sa dernière clôture et sa dernière réouverture (le journal garde tout) ;
(d) une validation vide (aucune séance) d'un mois clos est aussi refusée ; (e) le motif est borné à 500 caractères.

## API (noms exacts, tous `ADMIN`)

Le centre est celui de la session ; un `centerId` (corps ou paramètre, facultatif) différent répond **403**.

| Route | Réponse | Refus |
|---|---|---|
| `GET /api/v1/facturation/periodes?annee=AAAA` | 200 `{centerId, annee, periodes:[12 × {mois:"AAAA-MM", statut:"OUVERT"\|"CLOTURE", cloturePar, clotureLe, rouvertPar, rouvertLe, motifDeReouverture}]}` | 400 `ANNEE_INVALIDE` (absente ou hors 2000-2100) |
| `POST /api/v1/facturation/periodes/{mois}/cloturer` (mois `AAAA-MM`, corps facultatif `{centerId}`) | 200 `{centerId, mois, statut:"CLOTURE", cloturePar, clotureLe, seancesEligiblesRestantes}` | 400 `MOIS_INVALIDE` ; 409 `PERIODE_DEJA_CLOTUREE` |
| `POST /api/v1/facturation/periodes/{mois}/rouvrir` (corps `{motif, centerId?}`) | 200 `{centerId, mois, statut:"OUVERT"}` | 400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE` (absent, blanc ou > 500 caractères), `MOIS_INVALIDE` ; 409 `PERIODE_NON_CLOTUREE` ; 422 `PERIODE_COMPTABLE_CLOTUREE` |
| `POST /validate`, `POST /preview/seances/{id}/exclude`, `/include`, `/forfait` (routes existantes) | inchangées | **422 `PERIODE_DE_FACTURATION_CLOTUREE`** (`detail` : « La periode de facturation AAAA-MM est cloturee : … », propriété `mois`) |

Rôles : `SECRETAIRE`, `MEDECIN`, `INFIRMIER` → 403 ; sans session → 401/403 (sécurité commune).

## Réalisation (2026-10-21)

- **Domaine** (`domain/cloture`) : `MoisDeFacturation` (cloturer, rouvrir, exigerOuvert), `EntreeDuJournal`, refus `MoisDeFacturationClos`,
  `MoisDejaClos`, `MoisNonClos`, `MotifDeReouvertureObligatoire`, `ReouvertureRefuseeParLaComptabilite`, port `MoisDeFacturationRepository` ;
  `PeriodeDeFacturation.moisCouverts()`.
- **Application** (`application/cloture`) : `CloturerLeMois`, `RouvrirLeMois`, `ConsulterLesPeriodes`, `GardeDesMoisClos`, ports `ClotureComptable`,
  `PublicationDesPeriodes`. Modifiés minimalement : `ValiderLaFacturation`, `ExclureUneSeance`, `ReinclureUneSeance`, `ChangerLeForfaitDUneSeance`
  (un paramètre `GardeDesMoisClos` et une ligne d'appel chacun).
- **Infrastructure** : `MoisDeFacturationJdbc`, `ClotureComptableComptabilite` (ACL), `PublicationDesPeriodesSpring` ; câblage dans
  `FacturationContexteConfiguration`. **Comptabilité** : port publié `publishedlanguage/PeriodesComptables` + bean dans `ComptabiliteConfiguration`.
- **Interface HTTP** : `PeriodesDeFacturationControleur`, `PeriodesReponse`, `ClotureReponse`, `ClotureDeFacturationAdvice` (aussi sur `ValidationControleur`
  et `SimulationControleur`), `RequeteInvalide`.
- **Langage publié** : `PeriodeDeFacturationCloturee`, `PeriodeDeFacturationRouverte`.

## Migration (V42, additive)

`backend/platform/src/main/resources/db/migration/V42__facturation_cloture_mensuelle.sql` (+ DDL H2 dans `db/schema.sql`) : `periodes_facturation`
(clé primaire centre/année/mois, CHECK mois 1-12 et statut `OUVERT`/`CLOTURE`) et `periodes_facturation_journal` (identité, CHECK du type d'acte).
`IF NOT EXISTS`, aucun DROP, aucun DELETE, **aucune ligne** : aucun mois n'est clos au départ. Contrôles de production (SELECT) : `.ddd/migration/verifications-production.md`, point 23.

## Concurrence : clôture contre validation

Décision : **verrou pessimiste de la ligne du mois** pris dans la transaction de chaque acte (clôture, réouverture, validation, exclusion, réinclusion,
forfait). Une validation en cours fait attendre la clôture (avertissement exact) ; une clôture en cours fait attendre la validation, ensuite refusée ; les mois
d'un intervalle se verrouillent par ordre croissant. Preuve : `MoisDeFacturationConcurrentContrat` (deux transactions, déterministe) ; **retirer `FOR UPDATE`
fait échouer deux des quatre tests** (essai du 2026-10-21 sur H2).

## Tests

| Niveau | Tests |
|---|---|
| Domaine | `MoisDeFacturationTest` (12), `PeriodeDeFacturationTest` (+4 : mois couverts par un intervalle) |
| Application (doubles en mémoire) | `CloturerEtRouvrirLeMoisTest` (11), `ModificationsDUnMoisClotureTest` (5), `ValiderLaFacturationTest` (+4) |
| Contrats du dépôt | `MoisDeFacturationContrat` : double en mémoire (6), JDBC H2 (8), PostgreSQL 16 (10, `*PostgresContractIT`) |
| Concurrence | `MoisDeFacturationConcurrentContrat` : H2 (4), PostgreSQL 16 (4, y compris la création simultanée de la ligne) |
| Architecture | `ArchitectureHexagonaleTest` du module (inchangé, vert) ; `FrontieresDesContextesTest` ; `ReglesDeGestionDocumentationTest` |
| Alignement des modèles de persistance | `ModelesDePersistanceAlignesFacturationIntegrationTest` (+ tables V42) |
| HTTP (Spring complet, H2) | `ClotureEtReouvertureNouveauCodeIntegrationTest` (13), `GardesDuMoisClosNouveauCodeIntegrationTest` (13) : goldens `*_nouveau_code` sous `src/test/resources/characterization/facturation/cloture/nouveau_code` |

Non-régression : les 400+ scénarios de caractérisation F1 à F6, la Comptabilité C1 à C7 et la GMAO restent verts sans modification de golden (voir « Suites »).

## Écarts

Aucun legacy : pas d'écart de parité. Écarts de conception : voir « Précisions de conception ».

## À valider par le métier

1. L'avertissement ne bloque rien : un administrateur peut clore un mois avec des séances éligibles ; il faut ensuite rouvrir (motif) pour les facturer.
2. La double clôture répond **409** (et non 200 inchangé).
3. La réouverture est impossible dès que la période comptable du mois est clôturée (clôture comptable irréversible, RG-CPT-005) : un oubli de facturation sur
   un mois comptablement clos ne se corrige plus par une facture normale.
4. Seuls les actes de **facturation** sont gelés ; les règlements des factures d'un mois clos restent permis.
5. Les mois futurs et très anciens peuvent être clos (aucune précondition de chronologie).
6. La réponse de liste garde la dernière clôture et la dernière réouverture d'un mois ; l'historique complet n'est qu'au journal (pas d'écran ni d'API de lecture du journal en F7).

## À faire côté frontend (non modifié ici)

1. Écran de facturation : appeler `GET /api/v1/facturation/periodes?annee=` et afficher l'état de chaque mois (ouvert, clôturé par/le, rouvert par/le/motif) ; le mois de la période choisie
   doit prévenir qu'il est clos avant toute validation ou modification.
2. Boutons **Clôturer** et **Rouvrir** (ADMIN seulement), avec confirmation ; **Clôturer** affiche l'avertissement chiffré `seancesEligiblesRestantes` de la réponse
   (« N séances restent à facturer ») ; **Rouvrir** ouvre une fenêtre à motif obligatoire (≤ 500 caractères).
3. Gérer les codes : 422 `PERIODE_DE_FACTURATION_CLOTUREE` sur valider/exclure/réinclure/forfait (message avec le mois, propriété `mois`) ; 409 `PERIODE_DEJA_CLOTUREE` et
   `PERIODE_NON_CLOTUREE` ; 422 `PERIODE_COMPTABLE_CLOTUREE` ; 400 `MOTIF_DE_REOUVERTURE_OBLIGATOIRE`, `MOIS_INVALIDE`, `ANNEE_INVALIDE`. Ajouter les clés i18n (4 langues) comme pour
   `SEANCES.FACTUREE_NON_MODIFIABLE`.
4. Désactiver (ou masquer) Valider, Exclure, Réinclure et Changer de forfait quand le mois visé est clos ; la simulation reste utilisable.
5. Une validation par dates qui touche un mois clos est refusée : afficher le mois en cause.

## Résultats (2026-10-21)

- `./mvnw -o test` complet (tous modules) : **BUILD SUCCESS, 3840 tests, 0 échec, 0 erreur**. Les scénarios de caractérisation F1 à F6, la Comptabilité
  C1 à C7 et la GMAO sont verts sans modification de golden (`git status` : aucun fichier de golden existant modifié).
- `check-arch.ts` : 0 violation. `ArchitectureHexagonaleTest` des modules facturation et comptabilité, `FrontieresDesContextesTest`,
  `ReglesDeGestionDocumentationTest` verts (RG-FAC-040 à 045 documentées, contrôleur tracé dans l'annexe).
- **PostgreSQL 16 réel** (cluster jetable, port 55449, détruit en sortie) : `backend/scripts/facturation-pg16-rejeu.sh` étendu (étape F7), code de sortie 0.
  Constats : V42 s'applique après V1..V41, deux tables présentes et vides, V42 rejouable ; `MoisDeFacturationPostgresContractIT` (10) et
  `MoisDeFacturationConcurrentPostgresContractIT` (4, dont la création simultanée de la ligne d'un mois jamais verrouillé) verts.
- **Mutation PIT** (`backend/scripts/pit-f7-cloture.sh`) sur `domain.cloture` et `application.cloture` : 41 mutants tués sur 43 (**95 %**), couverture de lignes 99 %.
  Survivant : une borne de longueur du motif dans `rouvrir` ; sans couverture : `MoisDeFacturationClos.mois()` (lu par l'advice HTTP, couvert par les goldens).
- Preuve du verrou : retirer `FOR UPDATE` de `MoisDeFacturationJdbc.verrouiller` fait échouer deux des quatre tests de `MoisDeFacturationConcurrentJdbcTest`.
- Frontend, `docs/client` (`.docx`, `.pdf`, maquette) : **non régénérés** ; seul `documentation-fonctionnelle.html` reçoit les RG-FAC-040 à 045 (édition ciblée).
