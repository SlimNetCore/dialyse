---
status: approved
date: 2026-10-19
slice: facturation-f2-simulation
order: 2 (F1 à F7, voir `.ddd/migration/roadmap.md` § 1 ter)
sources: [.ddd/tactical/facturation.md (F1-F4, F11, Q3), docs/reference/regles-de-gestion/12-facturation-reglements-comptabilite.md (RG-FAC-020 à 023), ADR 0019, ADR 0024, ADR 0030]
---

> **Refonte sur place (ADR 0024)** : mêmes tables, mêmes URLs, mêmes DTO JSON ; retour arrière = commit précédent.

# F2 — Simulation de facturation (aperçu, exclusion, changement de forfait)

**Comportement observable** : un administrateur ou un médecin simule la facturation d'une période (mois ou deux
dates) : séances éligibles du centre, forfait appliqué à chaque séance, regroupement en factures (par patient, ou par
patient et par forfait), lignes, totaux HT/TVA/TTC et numéros prévisionnels ; il peut exclure une séance de la
facturation ou lui imposer un autre forfait, puis la simulation est recalculée.

| Route                                                   | Rôles (legacy)     | Legacy                                                                                                                                                                                                                                  |
|---------------------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /api/v1/facturation/preview`                      | `ADMIN`, `MEDECIN` | `FacturationRestController` → `FacturationDomainService.preview` → `SeanceFacturationJdbcAdapter.findEligibleSeances`, `FacturationSettingsJdbcAdapter`, `FactureJdbcAdapter.currentInvoiceSequence`, `TypeTvaJdbcAdapter.findActiveAt` |
| `POST /api/v1/facturation/preview/seances/{id}/exclude` | `ADMIN`, `MEDECIN` | `excludeSeance` → `SeanceFacturationJdbcAdapter.markAsAbsent` (statut `ABSENT`) puis aperçu                                                                                                                                             |
| `POST /api/v1/facturation/preview/seances/{id}/forfait` | `ADMIN`, `MEDECIN` | `updateSeanceForfait` → `overrideForfait` (colonnes `forfait_override_*`) puis aperçu                                                                                                                                                   |

Tables lues : `seances`, `patients`, `centre_payeur`, `prise_en_charge`, `forfait`, `facturation_settings`,
`facture_sequence`, `tva_types` ; écrites : `seances` (statut, `forfait_override_*`).

## Décisions Q3 (2026-10-19, utilisateur)

1. **Motif d'exclusion obligatoire** (le legacy n'en demande aucun).
2. **Réinclusion possible** tant que la séance n'est pas facturée.
3. **`ABSENT` reste au contexte Absences** : la Facturation ne fait plus passer une séance exclue à l'état `ABSENT` ;
   une séance `ABSENT` n'est pas facturable (elle est ignorée par la simulation).

Conséquence : l'exclusion devient une **marque propre à la Facturation** (séance exclue de la facturation, motif,
auteur, date), réversible ; elle demande une évolution de schéma (colonnes sur `seances` ou table dédiée) et une
nouvelle route de réinclusion. **ADR à rédiger à la construction** (stockage de l'exclusion, contrat JSON du motif,
effet sur l'écran Angular de simulation).

## Filet de caractérisation à écrire d'abord (sur le legacy)

1. Aperçu : centre sans séance ; éligibilité (statuts `VALIDEE`, `SIGNEE` seuls ; déjà facturée ; bornes de période ;
   autre centre) ; forfait appliqué (surcharge, PEC effective puis demandée couvrant la date, repli sur la PEC la plus
   récente, « Forfait non defini » à 0) ; regroupement multi-forfait vrai/faux ; lignes, totaux, TVA (type actif à la
   date du jour) ; numérotation prévisionnelle (séquence courante + 1, modèle du centre) ; période (mois, deux dates,
   fin avant début, absente, mois illisible).
2. Exclusion : effet en base (statut `ABSENT`, auteur), séance absente de l'aperçu, refus (inconnue, autre centre,
   déjà facturée), champs obligatoires.
3. Changement de forfait : effet en base, nouveau prix dans l'aperçu, refus (forfait inconnu, d'un autre centre, séance
   non modifiable).
4. Autorisations par profil et isolement par centre.

## Cible (à préciser au filet)

Contexte `facturation` : domaine `Simulation`/`FactureSimulee`, politique de forfait appliqué (F2), regroupement (F11),
calcul HT/TVA/TTC ; lecture du taux par le langage publié de la Comptabilité (`ParametrageFiscal`) ; ACL Séance (lecture
des séances éligibles, écriture de la surcharge et de l'exclusion) ; `SimulationControleur` sur les mêmes
URLs. Le legacy garde `validate` jusqu'à F3.

## État au 2026-10-19 : `characterized`

- Filet : 13 scénarios, 44 goldens, verts et stables sur le legacy (`.ddd/safety-net/facturation-f2-simulation.md`).
- Constats : KB-FAC-13 à 21 (`known-bugs.md`). Déjà tranché par Q3 : KB-FAC-13 (exclusion propre à la Facturation,
  motif obligatoire, réinclusion, `ABSENT` non touché).
- **Décisions à prendre avant la construction** : KB-FAC-14 (isolement par centre : corriger comme KB-FAC-01 ?),
  KB-FAC-15 (`MEDECIN` refusé par le filtre des profils : aligner ?), KB-FAC-16 (taux de TVA du jour ou de la
  période ?),
  et le stockage de l'exclusion (colonnes sur `seances` ou table dédiée, migration Flyway) avec la route de réinclusion.

### Décisions F2 (2026-10-19, utilisateur) — à réaliser par la slice, goldens `_nouveau_code`

**Révision de Q3, point 3** : l'exclusion **reste** un passage de la séance au statut `ABSENT` (colonne `statut`
actuelle), mais **avec un motif d'absence obligatoire**, enregistré comme une **absence patient** du module Absences
(table `absences_patients`, motifs `MotifAbsence` existants) ; la Facturation ignore les séances `ABSENT`.

| KB             | Décision                      | Effet attendu du nouveau code                                                                                                                                                                                                                                                                                                                                                                                          |
|----------------|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KB-FAC-13      | **Corriger**                  | exclusion = statut `ABSENT` + absence patient déclarée avec son motif (obligatoire, `AUTRE` exige un commentaire, règles du module Absences) ; auteur et date tracés par l'absence (plus dans `forfait_override_updated_*`) ; **réinclusion** possible tant que la séance n'est pas facturée : retour au statut d'avant l'exclusion (`VALIDEE` ou `SIGNEE`, mémorisé) et retrait de l'absence déclarée par l'exclusion |
| KB-FAC-14      | **Corriger**                  | aperçu, exclusion, réinclusion et changement de forfait : 403 si le centre demandé diffère de celui de la session                                                                                                                                                                                                                                                                                                      |
| KB-FAC-15      | **Reproduire, route alignée** | simulation réservée à `ADMIN` (le `@PreAuthorize` ne cite plus `MEDECIN`, que le filtre des profils refusait déjà)                                                                                                                                                                                                                                                                                                     |
| KB-FAC-16      | **Corriger**                  | taux de TVA en vigueur à la **fin de la période facturée** (lu par le langage publié `ParametrageFiscal`)                                                                                                                                                                                                                                                                                                              |
| KB-FAC-17 à 21 | **Reproduire**                | casse du statut, forfait appliqué, 500 de protocole, numérotation prévisionnelle, exclusion sans période annulée                                                                                                                                                                                                                                                                                                       |

Points de construction : contrat d'entrée de l'exclusion (champs `motif`, `commentaire` ajoutés au JSON actuel),
nouvelle
route de réinclusion, mémorisation du statut d'origine (colonne sur `seances` ou portée par l'absence), passage par le
langage publié des Absences (ACL vers le module legacy tant qu'il n'est pas un contexte) ; écran Angular de simulation à
adapter (saisie du motif, bouton de réinclusion). ADR à rédiger à la construction.

## État au 2026-10-20 : `built`

### Réalisation

**Contexte `facturation`** (hexagonal, ArchUnit, `check-arch` 0 violation) :

- domaine `simulation` : `Simulation`, `FactureSimulee`, `LigneSimulee`, `SeanceFacturable`, `PatientFacture`,
  `ForfaitApplique`,
  `PolitiqueDeForfaitApplique` (surcharge > PEC couvrant la date > PEC la plus récente même échue > « Forfait non
  defini » à 0),
  `PriseEnChargeTarifee`, `Regroupement`, `PeriodeDeFacturation`, `TauxDeTva`, `NumerotationPrevisionnelle` ; ports
  `SeancesFacturables`, `ForfaitsDuCentre`, `SequenceDeFacturation` ; `ModeleDeNumerotation.numero(…)` (jetons
  RG-FAC-010) ;
- domaine `seance` : `SeanceDeFacturation` (décide `exclure`, `reinclure`, changement de forfait), `MotifDAbsence`,
  `Exclusion`,
  `Reinclusion`, port `SeancesDeFacturation` ;
- application `simulation` : `SimulerLaFacturation`, `ExclureUneSeance`, `ReinclureUneSeance`,
  `ChangerLeForfaitDUneSeance`
  (écriture et recalcul dans une transaction), ports `TauxDeTvaApplicable`, `AbsencesDesPatients` ;
  `SessionCourante.utilisateur()` ;
- ACL dans le module : `infrastructure/acl/legacyseance` (`SeancesFacturablesJdbc`, `SeancesDeFacturationJdbc`,
  `ForfaitsDuCentreJdbc`), `infrastructure/acl/comptabilite/TauxDeTvaComptabilite` (langage publié `ParametrageFiscal`),
  `infrastructure/persistence/SequenceDeFacturationJdbc` ; ACL côté `platform` :
  `infrastructure/acl/facturation/AbsencesDesPatientsLegacy` (service du module Absences) ;
- interface : `SimulationControleur` (`POST /api/v1/facturation/preview`, `/preview/seances/{id}/exclude`,
  `/preview/seances/{id}/include` **nouvelle route**, `/preview/seances/{id}/forfait`), réservé à `ADMIN`.

**Migration** `V39__facturation_exclusion_de_seance.sql` (additive : `seances.statut_avant_exclusion`,
`seances.exclusion_absence_id`, nullables) et `db/schema.sql` ; **ADR 0031** (stockage de l'exclusion, contrat JSON).

**Contrat JSON** : mêmes champs que le legacy ; `…/exclude` ajoute `motif` (obligatoire) et `commentaire` ; `…/include`
prend
`centerId`, `month` ou `periodStart`/`periodEnd`, `regroupementMultiForfait` ; réponses inchangées.

**Parité** : filet F2 (13 scénarios, 44 goldens) rejoué sur le nouveau code : 39 goldens inchangés ; 5 goldens caducs
retirés (`centre_A_par_admin_B`, `exclusion_nominale_200`, `exclusion_nominale_etat_base`,
`exclusion_seance_A_par_admin_B`,
`exclusion_seance_A_par_admin_B_etat_base`) et remplacés par des goldens `*_nouveau_code` (33 au total, dont
réinclusion, motif,
délai du module Absences, centres, TVA à la fin de période). Retouches de fixture (harnais et tests, sans toucher un
golden ni
une assertion conservés) : requête d'exclusion avec `motif` (champ obligatoire du nouveau contrat) ; exclusions réussies
à
dates relatives au jour d'exécution avec alias (`nommerTexte`) ; suppression des `absence_patient` du centre au
nettoyage ;
`adminA(utilisateur)` ; `goldenExclusions`. Test d'intégration legacy `FacturationRestControllerIntegrationTest` (quatre
tests de la simulation, assertions inchangées) : session `UserPrincipal` rattachée au centre (le contexte confronte le
centre
demandé à la session, KB-FAC-14, un `user("admin")` sans centre répond 403) ; le test d'exclusion date la séance de J-2,
envoie
un `motif` et une période relative (délai de 60 jours du module Absences) ; nettoyage des `absence_patient` du centre.

**Tests** : module `facturation` 167 tests verts (domaine, cas d'usage avec doubles en mémoire, contrats mémoire/H2 des
séances
et de la séquence, ArchUnit) ; contrats PostgreSQL 16 `SeancesPostgresContractIT` (22) et `SequencePostgresContractIT`
(3)
verts sur le schéma Flyway V1..V39 ; ACL Absences (table de traduction des motifs) ; alignement des colonnes lues ou
écrites
par la simulation sur le schéma figé (`ModelesDePersistanceAlignesFacturationIntegrationTest`) ; PIT du domaine et de
l'application du module : 94 % de mutants tués (154/163, force des tests 97 %).

### Écarts décidés (voir `known-bugs.md`, « Décisions F2 réalisées »)

KB-FAC-13 corrigé (motif, absence déclarée, réinclusion) ; KB-FAC-14 corrigé (403) ; KB-FAC-15 reproduit, route
`ADMIN` ;
KB-FAC-16 corrigé (TVA à la fin de période) ; KB-FAC-17 à 21 reproduits (« reproduits, correction à la livraison
suivante »).
Ordre des contrôles (KB-FAC-21) : l'état de la séance (422) puis le motif (400) sont contrôlés en premier, puis la
période (400), puis seulement les écritures ; le legacy écrivait d'abord et annulait la transaction à l'échec du
recalcul. Résultat
observable identique (400, séance inchangée) ; seule la précédence d'une période absente sur une séance inconnue ne
change pas.

### Points à valider

1. Une séance de plus de 60 jours ne peut plus être exclue (règle du module Absences appliquée telle quelle, ADR 0031).
2. La réinclusion **supprime** la ligne d'absence créée par l'exclusion (le module Absences ne sait qu'annuler, état
   définitif).
3. Une séance `ABSENT` sans mémoire d'exclusion (absence réelle, exclusion d'avant la slice) ne se réinclut pas.
4. L'auteur de l'absence est l'utilisateur de la session ; `userId` du corps n'est plus utilisé que pour la surcharge de
   forfait.

### Reste à retirer avec F3..F6

Legacy conservé car encore lu par la validation (F3) : `FacturationRestController` (`/validate`, `/dashboard`,
`/synthese/print`), `FacturationDomainService` (`preview` interne à `validate`, `validate`, `dashboard`, réglages),
`FacturationUseCase` (`validate`, `dashboard`, réglages), `FacturationApplicationService`, `SeanceFacturationPort` /
`SeanceFacturationJdbcAdapter` (`findEligibleSeances` et le calcul du forfait pour `validate`, `markAsBilled`,
`loadDashboard`),
`TypeTvaRepositoryPort` / `TypeTvaJdbcAdapter.findActiveAt`, `FactureRepositoryPort.currentInvoiceSequence` /
`nextInvoiceNumber`, `FactureNumberTemplate`, `FacturationPreviewQuery/Result/Invoice/Line/Seance` (portés par
`validate`),
`SeanceEligibleForFacturationSpecification`. **Retiré en F2** : les trois routes de simulation du contrôleur,
`FacturationUseCase.preview/excludeSeance/updateSeanceForfait`,
`FacturationDomainService.excludeSeance/updateSeanceForfait`,
`SeanceFacturationPort.markAsAbsent/overrideForfait`, les trois requêtes `FacturationPreview*Request`, les deux
commandes,
`FacturationRestControllerTest` (son seul test portait sur la route retirée).

### Plan de bascule

Refonte sur place (ADR 0024) : mêmes URL, pas de façade ni de flag ; retour arrière = commit précédent.
**Déploiement** :
migration V39 appliquée au démarrage (additive, rejouable) ; contrôles de production : `verifications-production.md`
point 18. **Frontend à adapter** (non modifié ici) : saisie du motif (liste des motifs d'absence) et du commentaire
(obligatoire pour
`AUTRE`) à l'exclusion ; bouton de réinclusion (`POST …/include`) sur les séances exclues ; message 403 si le centre
demandé
diffère de la session ; messages 422 de l'exclusion (délai de 60 jours, période facturée) ; TVA calculée à la fin de la
période ; écran réservé à `ADMIN`.
