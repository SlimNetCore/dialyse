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
