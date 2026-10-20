---
status: done
date: 2026-10-20
slice: facturation-f3-validation-emission
sources: roadmap § 1 ter (F3), .ddd/tactical/facturation.md (F5-F9, F14-F15), RG-FAC-030 à 032
---

# Filet de caractérisation — F3 Validation et émission des factures

**Où** : `backend/platform/src/test/java/com/hemodialyse/backend/characterization/facturation/validation/`
(5 classes + harnais `ValidationCharacterizationSupport` + enregistreur d'événements
`EvenementsFacturationValideeEnregistres`) ; goldens
`backend/platform/src/test/resources/characterization/facturation/validation/{emission,numerotation,seances,refus,evenement}/`.
**52 scénarios**, 0 flaky (3 exécutions, ordre des classes et des méthodes aléatoire).
Mutation : `backend/scripts/pit-f3-validation.sh` (résultat plus bas). Constats : `known-bugs.md` § « slice F3 »,
KB-FAC-22 à 36.

## Périmètre exact

Une seule route : `POST /api/v1/facturation/validate` (`FacturationRestController.validate`,
`@PreAuthorize ADMIN, MEDECIN` ;
corps `FacturationValidateRequest` : `centerId`, `userId`, `month` | `periodStart`+`periodEnd`,
`regroupementMultiForfait`,
`previewGeneratedAt`) → `FacturationApplicationService.validate` (transaction ; publie `FacturationValideeEvent`) →
`FacturationDomainService.validate` (recalcule l'aperçu legacy `preview` à la date du jour, puis crée) →
`FactureJdbcAdapter`
(`nextInvoiceNumber`, `saveAll`) + `SeanceFacturationJdbcAdapter` (`findEligibleSeances`, `loadForfait`,
`markAsBilled`) +
`TypeTvaJdbcAdapter.findActiveAt` + `FacturationSettingsJdbcAdapter.findByCenterId`. Réponse :
`{createdInvoices, billedSeances}`.
Tables écrites : `factures`, `facture_lignes`, `facture_sequence`, `seances` (`statut = 'FACTUREE'`, `facture_id`) ;
puis, après commit,
`ecritures_comptables` / `lignes_ecriture` (Comptabilité, écouteur `FacturationValideeComptabiliteListener`).

### Ce que fait le legacy (réponse aux questions de conception)

| Sujet          | Comportement actuel                                                                                                                                                                                                                                                                                     |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Éligibilité    | statut SQL exactement `VALIDEE` ou `SIGNEE` (majuscules), dans le centre et la période (bornes incluses), `facture_id` nul (filtré en Java). `ABSENT`, `BROUILLON`, `FACTUREE`, minuscules, autre centre : ignorés                                                                                      |
| Regroupement   | décidé par le **corps** (booléen obligatoire), jamais par les réglages du centre : vrai = une facture par patient (une ligne par forfait + libellé) ; faux = une facture par patient et par forfait                                                                                                     |
| Forfait / prix | surcharge de séance, sinon PEC, sinon « Forfait non defini » à 0 (KB-FAC-18) ; ligne = prix × nombre (HALF_UP 2 décimales) ; **le prix et le libellé sont recopiés dans `facture_lignes`** (figés)                                                                                                      |
| Totaux         | HT = somme des lignes ; TVA = HT × taux/100 arrondie **HALF_UP sur le total de la facture** (pas par ligne) ; TTC = HT + TVA ; ex. 99,99 à 19 % → 19,00 ; 12,50 à 19 % → 2,38                                                                                                                           |
| TVA            | type actif `HEMODIALYSE` à la **date du jour de validation** (KB-FAC-22) ; aucun type actif/échu/inactif → 0 % ; taux figé dans `factures.tva_rate`                                                                                                                                                     |
| Numéro         | modèle du centre (`FAC-{YEAR}-{SEQ}` par défaut ; `{YYYY} {YEAR} {YY} {CENTER} {SEQ[n]}`) ; séquence de **l'année du jour** (KB-FAC-23) : courante + 1, + 2… dans l'ordre alphabétique des patients ; ligne de séquence créée à 1 si absente ; l'aperçu F2 annonce les mêmes numéros sans rien réserver |
| Snapshots      | patient (code, « Nom Prénom », état), n° d'immatriculation (= n° d'assurance), centre payeur et agence (d'après la 1re séance éligible du patient), période, `date_facturation` = jour de validation, taux                                                                                              |
| Atomicité      | une seule transaction : tout échec (doublon de numéro, prix négatif, séance plus éligible) annule factures, lignes, séquence, états de séances et événement                                                                                                                                             |
| Événement      | `FacturationValideeEvent(centre, période, nb factures, nb séances, totalTtc = 0, instant)` publié même si `{0,0}` (KB-FAC-27/29) ; période de l'événement = dates du corps si fournies, sinon mois (KB-FAC-28)                                                                                          |
| Comptabilité   | après commit, une écriture de ventes (journal `VE`, 3 lignes : client TTC, TVA collectée, ventes HT) par facture dont la période est incluse dans celle de l'événement, datée de la fin de période ; rejeu sans doublon ; mois clôturé : aucune écriture, facture conservée                             |

## Scénarios (52)

| Classe                          | Scénarios                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|---------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `EmissionDesFactures…` (9)      | nominale regroupée (3 patients, snapshots payeur/agence, totaux) ; sans regroupement (une facture par patient et forfait) ; regroupement avec lignes de libellés différents ; arrondis HALF_UP ; sans type de TVA ; TVA du jour ≠ fin de période ; TVA inactive/échue ; forfait non défini à 0 (+ écriture à zéro) ; prix/libellés figés après changement du référentiel                                                                                                                                                                                |
| `NumerotationDesFactures…` (11) | séquence + 1 dans l'ordre des patients ; première facture (sans ligne de séquence) ; année du jour ≠ année de la période ; modèle `{CENTER}/F{SEQ6}` ; modèle par défaut sans réglages ; l'aperçu ne réserve rien et annonce les mêmes numéros ; deux validations successives ; séquence par centre ; doublon avec une facture existante (500, rollback, nouvelle tentative identique, aucun événement) ; modèle sans `{SEQ}` (1 facture OK, 2 → 500)                                                                                                   |
| `SeancesFacturees…` (8)         | éligibilité (statuts, casse, séance liée à une facture, bornes, autre centre) ; période par dates ; mois prioritaire sur les dates ; aucune séance → 200 `{0,0}`, séquence intacte ; revalidation idempotente (état, événements, écritures) ; séance tardive → 2e facture ; facture existante sur la période n'empêche rien ; autre centre non facturé                                                                                                                                                                                                  |
| `RefusEtAutorisations…` (15)    | sans période, une seule date, fin avant début (400) ; mois illisible/texte, centre/date/JSON illisibles, corps vide, booléen absent (500) ; centre/utilisateur manquants (400) ; mois vide avec dates ; réglages du centre sans effet sur le regroupement ; `previewGeneratedAt` ignoré ; autorisations (ADMIN 200 ; MEDECIN `ROLE_SCOPE` ; SECRETAIRE/INFIRMIER `ACCESS_DENIED` ; anonyme 403 ; MEDECIN+ADMIN 200) ; admin du centre B sur le centre A ; centre inconnu ; prix négatif (400, rollback) ; échec de la dernière facture (rollback total) |
| `EvenementEtComptabilite…` (9)  | contenu de l'événement ; écritures de ventes par facture ; compte du payeur ; mois + dates divergents ; période par dates ; validation vide qui regénère des ventes manquantes ; période comptable clôturée ; montant nul ; rejeu sans doublon                                                                                                                                                                                                                                                                                                          |

## Point d'observation et non-déterminisme

HTTP (MockMvc, Spring complet, sécurité réelle + `RoleScopeFilter`, H2 uniquement) + état SQL (factures, lignes,
séquence, séances)

+ événement réellement publié (enregistreur `@EventListener` de test, observation seule) + écritures produites par
  l'écouteur de
  production. Aucun mock du code caractérisé, aucun seam, aucun code de production modifié.
  Jour d'exécution : TVA ouverte depuis 2000, séquence posée pour l'année en cours, périodes fixes dans le passé
  (janvier 2026,
  novembre 2025) ; `FAC-<année>-` et le jour remplacés par `<ANNEE>` / `<AUJOURD_HUI>` ; UUID générés et horodatages
  normalisés ;
  factures nommées `<FACTURE_n>` par numéro, écritures triées par numéro de pièce. Les périodes fixes restent lisibles
  (`2026-01-31`).
  Mise à jour d'un golden : `-Dcharacterization.approve=true` après relecture.

## Non verrouillé (limites)

- **Course de numérotation** entre deux validations simultanées (lecture puis écriture sans verrou) et échec
  `markAsBilled`
  (« séances plus éligibles », RG-FAC-030) : non rejouables de façon déterministe sans seam ni test concurrent ; le
  chemin
  est protégé indirectement (rollback total prouvé par le doublon de numéro).
- PostgreSQL : H2 seulement (la contrainte `UNIQUE(center_id, numero_facture)` et le rollback sont ceux de V1, mais la
  concurrence PostgreSQL n'est pas rejouée).
- Données de production (factures historiques à numéros dupliqués ou séquence en retard : question Q6) : à vérifier hors
  H2.
- `FacturationSettingsJdbcAdapter.save`, `TypeTvaJdbcAdapter` (hors `findActiveAt`),
  `FacturationDomainService.dashboard/getSettings/updateSettings`,
  `/dashboard`, `/synthese/print` : F5 / F1, non couverts ici.
- Frontend (écran de validation, messages) : non caractérisé.
- Messages techniques des 500 (`DATA_ACCESS_ERROR`, `INTERNAL_ERROR`) : seul le code est verrouillé.

## Fichiers legacy ajoutés à `covered.json` (propres à F3)

`FactureJdbcAdapter`, `TypeTvaJdbcAdapter` (lecture du taux), `FacturationSettingsJdbcAdapter` (lecture du modèle),
`FacturationValidateRequest`, `FactureAggregate`, `TypeTVA`, `LigneFacture`, `FacturationValideeEvent`,
`FactureRepositoryPort`,
`TypeTvaRepositoryPort`, `FacturationSettingsRepositoryPort`, `FacturationValidateCommand`,
`FacturationValidationResult`,
`FacturationPreview{Invoice,Line,Query,Result,Seance}`, `SeanceFacturationCandidate`,
`SeanceEligibleForFacturationSpecification`,
`FactureNumberTemplate`, `FacturationPeriod`, `ParametresFacturation`.
`FacturationValideeComptabiliteListener` était déjà couvert (C5).

### Fichiers partagés avec F4/F5/F6 que F3 devra modifier (déjà dans `covered.json`)

`FacturationRestController` (retrait de `/validate` ; reste `/dashboard`, `/synthese/print`), `FacturationDomainService`
(`validate` + `preview` interne ; reste `dashboard`, réglages), `FacturationUseCase`, `FacturationApplicationService`,
`SeanceFacturationPort` / `SeanceFacturationJdbcAdapter` (`findEligibleSeances`, `loadForfait`, `markAsBilled` à
retirer ;
`loadDashboard` reste), `TypeTvaRepositoryPort` / `TypeTvaJdbcAdapter.findActiveAt`. **Accord de l'utilisateur identique
à
C1/C2/F1/F2** (refonte sur place, ADR 0024/0030/0031) ; F3 ne modifie rien en production dans cette étape.

## Lecteurs et consommateurs de la facture hors Facturation

| Consommateur                 | Ce qu'il lit                                                                                                                                                                                                                                                                 | Protégé par                                                                            |
|------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| Comptabilité (C5)            | événement `FacturationValideeEvent` → `ComptabilisationDesVentesEtReglements.facturationValidee(centre, début, fin)` ; `FacturesAComptabiliserJdbc` lit `factures` (numéro, payeur, totaux, `date_facturation`, `period_end`, nom) ; liste « non comptabilisées », `rebuild` | F3 (`evenement/`), C5 `VentesEtReglements…`                                            |
| Stock                        | `SeanceBillingStatusJdbcAdapter` : séance `FACTUREE` (bon de sortie)                                                                                                                                                                                                         | `BonSortieApplicationServiceIntegrationTest`, `BonSortieRestControllerIntegrationTest` |
| Séance                       | `FACTUREE` rend la séance non supprimable / non modifiable (`Seance`, `SeanceDomainService`, volets médical et paramédical)                                                                                                                                                  | tests Séance existants ; F3 verrouille l'état écrit                                    |
| Absences                     | `AbsenceDonneesJdbcAdapter.periodeFacturee` (lit `factures.patient_id/period_*`), séances réalisées incluent `FACTUREE`                                                                                                                                                      | `AbsencePatientIntegrationTest`                                                        |
| Direction                    | `DirectionDashboard/Breakdown/AbsencesQueryService` (`factures.total_*`, `date_facturation`, séances `FACTUREE`)                                                                                                                                                             | `DirectionBreakdownIntegrationTest`, `DirectionCapaciteIntegrationTest`                |
| Règlements (F4)              | `FactureReglementJdbcAdapter` (`factures.total_ttc`, `facture_reglements.facture_id`)                                                                                                                                                                                        | tests Règlements                                                                       |
| Rapports                     | `FacturationSyntheseMensuelleReportService` (`factures`, `facture_lignes`), `CahierDialyseReportService`, `PlanningDonneesJdbcAdapter`                                                                                                                                       | tests de reporting                                                                     |
| Migration                    | `OpeningBalanceJdbcAdapter`, `MigrationRollbackJdbcAdapter` écrivent/suppriment `factures` et `facture_lignes` (reprise) ; `HistoryRules` (séances `FACTUREE`)                                                                                                               | tests de migration                                                                     |
| Simulation F2 (nouveau code) | `SequenceDeFacturationJdbc` (`facture_sequence`), `SeancesFacturablesJdbc` (séances, factures)                                                                                                                                                                               | F2 `apercu/`                                                                           |

## Mutation (PIT 1.30.0)

Voir la section ci-dessous, ajoutée après l'exécution.
