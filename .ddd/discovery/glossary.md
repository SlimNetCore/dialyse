---
status: draft
date: 2026-10-09
sources:
  - .ddd/discovery/event-storming.md (timeline et hotspots)
  - .ddd/scan/summary.md (termes polysémiques, tables)
  - enums et méthodes du domaine (Grep), docs/reference/regles-de-gestion (RG cités)
validation: tous les termes en `proposed` ; aucun terme validé par un expert
---

# Glossaire (langage omniprésent — découverte)

Colonne « Domaine d'usage » = zone de vocabulaire observée (pas un bounded context décidé). Les **homonymes** ont une entrée par sens, marquée ⚠. Aucun désaccord n'est tranché ici.

## Termes centraux

| Terme métier | Définition | Domaine d'usage | Identifiant code | Nom legacy | Synonymes à bannir | Exemple / règle | Statut |
|---|---|---|---|---|---|---|---|
| Séance (de dialyse) ⚠ | Acte de dialyse d'un patient, un jour donné, dans un centre, avec volets paramédical/médical et consommables | Réalisation de séance | `Seance` | `seances`, `SeanceStatus` | session (`canCreateSession`) | `CREE → VALIDEE → SIGNEE → FACTUREE`, sans retour (RG-SEA-001) | proposed |
| Séance facturable ⚠ | Séance validée ou signée, non facturée, dans la période, prise comme ligne de facture au prix d'un forfait | Facturation | `SeanceFacturationJdbcAdapter`, `SeanceEligibleForFacturationSpecification` | même table `seances` lue en SQL | — | signature médecin non exigée (RG-FAC-020) | proposed |
| Séance (motif de sortie) ⚠ | Référence de la séance qui justifie une sortie de stock de consommables | Stock | `BonSortie.seanceId` | `addSeanceConsommation` | — | sortie immuable si séance facturée (RG-SEA-002) | proposed |
| Séance réalisée | Séance au statut validée, signée ou facturée | Absences / Séance | — (règle) | — | séance faite | interdit de déclarer une absence ce jour-là (RG-ABS-012) | proposed |
| Validation de séance | Constat par l'infirmier que la dialyse a eu lieu (souvent par scan QR) | Réalisation de séance | `Seance.validerParInfirmier` | `validate`, `scanAndValidate` | — | jour même, sinon déverrouillage admin (RG-SEA-046) | proposed |
| Signature médicale | Visa du médecin sur la séance | Réalisation de séance | `Seance.signerParMedecin` | `signByMedecin` | — | — | proposed |
| Régularisation | Validation a posteriori d'une séance passée restée « créée », après déverrouillage par l'administrateur | Réalisation de séance | `unlockForRegularisation` | `deverrouiller-regularisation` | rattrapage (≠) | 7 derniers jours, ADMIN seul | proposed |
| Dérogation hors planning | Séance réalisée en dehors du placement prévu, avec motif | Réalisation de séance | `DerogationPlanning`, `MotifHorsPlanning` | `marquerHorsPlanning` | — | — | proposed |
| Exclusion de facturation ⚠ | Retrait d'une séance de la facture depuis la simulation | Facturation | `excludeSeance` | statut **`ABSENT`** | « absent » (confusion avec absence patient) | séance non réalisée, non facturable (RG-FAC-023) | proposed — H1 |
| Absence de patient ⚠ | Jour de dialyse prévu où le patient n'a pas été dialysé, détectée ou déclarée | Absences patients | `AbsencePatient`, `StatutAbsence` | `absences_patient` | — | `A_QUALIFIER → JUSTIFIEE/NON_JUSTIFIEE → RATTRAPEE/ANNULEE` | proposed |
| Absence d'infirmier ⚠ | Indisponibilité d'un infirmier (congé, maladie, formation) | Planning infirmiers | `AbsenceInfirmier`, `TypeAbsence` | — | — | déclenche la recherche de remplaçant | proposed |
| Qualification (d'absence) | Attribution d'un motif à une absence (justifiante ou non) | Absences patients | `AbsencePatient.qualifier` | `MotifAbsence.justifiante()` | — | sans motif → « à qualifier » | proposed |
| Rattrapage | Séance supplémentaire compensant une absence | Absences patients | `AbsencePatient.rattraper` | `RATTRAPEE` | régularisation (≠) | — | proposed |
| Valorisation (d'absence) | Montant perdu figé à la création : forfait PEC HT × (1 + TVA) | Absences patients | `ValorisationAbsenceService` | — | — | RG-ABS-030 | proposed |
| Patient ⚠ | Personne dialysée dans un centre, avec un état et une place | Patient & couverture | `Patient` | `patients` | client, user | état de sortie exige une date d'évènement (RG-PAT-031) | proposed — H5 |
| État du patient | Situation administrative : permanent, occasionnel, vacancier local/étranger, transféré, décédé, greffé, guéri | Patient | `etat` (`dateEvenementEtat`) | — | statut | — | proposed — H5 |
| Type de patient | Vacancier / non vacancier | Patient | `PatientType` | — | — | redondant avec l'état ? | proposed — H5 |
| Mouvement de patient | Événement de vie administrative : admission, séjour temporaire, reprise, transfert, décès, greffe, guérison, place libérée | Patient | `TypeMouvementPatient` | — | — | — | proposed |
| Place (de dialyse) | Combinaison salle + créneau + générateur + jours de dialyse attribuée à un patient | Planning / placement | `PlacementPatientService`, `position_creneau` | — | poste ? | libérée la nuit après le dernier jour d'occupation (RG-PAT-033) | proposed |
| Placement | Attribution d'une place à un patient, proposée avec un score 0-100 | Planning | `PlanificationAffectationService.proposer` | affectation | affectation (sens double, cf. Assuré) | refus si incohérent (RG-PLN-050) | proposed |
| Créneau / Salle / Générateur | Plage horaire / local / machine de dialyse | Planning | `CreneauRef`, `SalleRef` | — | — | salles d'isolement (RG-PLN-053) | proposed |
| Optimisation du planning | Calcul automatique d'un planning patients/infirmiers, appliqué sur décision | Planning | `RunOptimisation`, `StatutRun` | Timefold | — | replanification nocturne optionnelle (RG-PLN-100) | proposed |
| Remplaçant | Infirmier proposé (classé) pour couvrir une absence | Planning infirmiers | `RemplacantService`, `RemplacementInfirmier` | — | — | RG-INF-051 | proposed |
| Sous-effectif | Créneau dont l'effectif infirmier est insuffisant (ratio par défaut 4, à confirmer) | Planning infirmiers | `PresenceInfirmierScheduler` | — | — | contrôle 07:15 à 14 j | proposed |
| Assuré | Personne titulaire des droits d'assurance, rattachée au patient (principal ou non) | Patient & couverture | `bc-assure` | `affectation` | — | un seul assuré principal à la fois | proposed — H5 |
| Prise en charge (PEC) | Accord d'un payeur couvrant une période et un forfait pour un patient | Patient & couverture | `PriseEnCharge`, `PecStatus` | `bc-pec` | — | `CREE → VALIDEE → CLOTUREE` ; période demandée vs effective | proposed |
| Attestation de droits | Justificatif de droits à l'assurance du patient | Patient & couverture | `bc-insurance` | `ATTESTATION_DROITS` | — | cause de non-facturation | proposed |
| Forfait | Prix HT d'une séance défini par la PEC ou surchargé sur la séance | Facturation / PEC | `forfaitId` | — | tarif | priorité : surcharge > PEC effective > PEC demandée (RG-FAC-021) | proposed |
| Caisse d'assurance / Agence | Organisme payeur et son antenne | Facturation | `caisse_assurance`, `agence` | — | — | — | proposed |
| Centre payeur ⚠ | Entité qui paie la facture (payeur), éventuellement sans compte comptable propre | Facturation / Comptabilité | `centre_payeur`, `payeurs` | — | centre (≠ centre de dialyse) | — | proposed — H4/H13 |
| Facture | Document figeant patient, payeur, période, forfaits, TVA pour un groupe de séances | Facturation | `FactureAggregate`, `LigneFacture` | — | — | numéro `FAC-{YEAR}-{SEQ}` | proposed |
| Validation de facturation | Émission atomique des factures de la période et passage des séances en facturées | Facturation | `FacturationDomainService.validate`, `FacturationValideeEvent` | — | — | échoue si une séance n'est plus éligible (RG-FAC-030) | proposed — H2 |
| Simulation de facturation | Aperçu des factures sans rien enregistrer | Facturation | `preview` | — | — | — | proposed |
| Type de TVA | Taux applicable par période de validité et type de prestation | Facturation | `TypeTVA` | `RegleTVA` (comptabilité) | — | sans type actif : 0 % | proposed |
| Règlement | Paiement (ou remboursement si négatif) rattaché à une facture | Règlements | `FactureReglementAggregate`, `FacturePayment` | `payment` | paiement | situation : non réglée / partiellement / réglée / trop-perçu | proposed |
| Écriture comptable | Pièce équilibrée débit/crédit dans un journal | Comptabilité | `EcritureComptable`, `StatutEcriture` | — | — | `BROUILLON → VALIDEE → EXPORTEE` (immuable) | proposed |
| Journal | Registre d'écritures (VE, BQ, CA, AC, ST) | Comptabilité | `JournalCode` | — | — | — | proposed |
| Compte comptable ⚠ | Compte du plan SCF (706, 411xxx, 44571, 512, 530…) | Comptabilité | `CompteComptable`, `MappingComptable` | — | compte (seul) | plan par centre, paramétrable | proposed — H4 |
| Compte client ⚠ | Compte 411 du tiers débiteur (patient payant lui-même ou payeur) | Comptabilité | — | `411100`, `411500` | — | — | proposed — H4 |
| Compte d'article ⚠ | Compte comptable propre à un article de stock | Stock / Comptabilité | `Article` (comptes) | — | — | RG-CPT-011 | proposed — H4 |
| Compte utilisateur ⚠ | Identité de connexion d'une personne | Accès | `app_user*` | — | compte (seul) | MFA, verrouillage 5 min | proposed — H4 |
| Période comptable / Clôture ⚠ | Mois comptable ; une fois clôturé, aucune écriture n'y est générée | Comptabilité | `cloturerPeriode` | — | — | ADMIN, non reclôturable (RG-CPT-005) | proposed — H3 |
| Extourne | Écriture inverse annulant une écriture | Comptabilité | `extourner` | — | annulation | — | proposed |
| Article | Produit référencé (consommable, médicament) avec unités et conditions de conservation | Stock / Catalogue | `Article` | `bc-article` | produit | — | proposed |
| Lot | Quantité d'un article avec numéro et date d'expiration | Stock | `Lot` | — | — | consommation FEFO | proposed |
| Bon (commande / réception / sortie) ⚠ | Document de mouvement de stock | Stock | `BonCommande`, `BonReception`, `BonSortie`, `BonStatut` | — | — | `BROUILLON → VALIDE → RECU / ANNULE` | proposed |
| PMP | Prix moyen pondéré d'un article | Stock | `PmpRecalculationCoordinator` | — | coût moyen | article verrouillé pendant recalcul (RG-STK-024) | proposed |
| Inventaire / Clôture d'inventaire ⚠ | Comptage physique ; sa clôture fige les mouvements antérieurs | Stock | `Inventaire`, `InventaireStatut` | — | — | `EN_COURS → CLOTURE / ANNULE` | proposed — H3 |
| Ordonnance ⚠ | Document médical signé listant des médicaments | Suivi médical | `Ordonnance`, `StatutOrdonnance` | — | — | `BROUILLON → SIGNEE → IMPRIMEE / ANNULEE` | proposed — H8 |
| Prescription médicale ⚠ | Prescription de traitement (ex. EPO/fer dose × fréquence) suivie en observance | Suivi médical | `prescriptions_medicales` | — | — | même chose qu'ordonnance ? | proposed — H8 |
| Observance | Respect de la prescription d'anémie sur une période | Suivi médical | `AlerteObservance`, `TypeAlerteObservance` | — | — | alerte `RETARD_CONSTATE` (06:30) | proposed |
| Demande d'examen | Demande d'analyses suivie jusqu'à validation | Suivi médical | `DemandeExamen` | — | — | `DEMANDE → PRELEVE → RESULTAT_DISPONIBLE → VALIDE / ANNULE` | proposed |
| Observation biologique / Analyte | Résultat mesuré d'une analyse | Suivi médical | `ObservationBiologique` | — | — | correction tracée | proposed |
| Sérologie | Résultat d'un marqueur sérologique | Suivi médical | `Serologie`, `MarqueurSerologique` | `serologies_patient` | — | — | proposed |
| Bilan pré-greffe | Parcours de préparation à la greffe rénale (étapes, immunologie, RCP) | Greffe | `BilanPreGreffe`, `StatutBilanGreffe` | `bilans_pre_greffe` | — | `NON_DEBUTE → … → GREFFE_REALISEE` | proposed — H7/H12 |
| Décision RCP | Avis de la réunion de concertation pluridisciplinaire | Greffe | `DecisionRcp`, `AvisRcp` | — | — | favorable / défavorable / ajourné | proposed |
| Donneur vivant | Donneur apparenté ou non, avec crossmatch | Greffe | `DonneurVivant` | — | — | — | proposed |
| Greffe ⚠ | (a) état de sortie du patient ; (b) mouvement ; (c) statut de bilan « greffe réalisée » | Patient / Greffe | `GREFFE`, `GREFFE_REALISEE` | — | — | lien entre (a) et (c) ? | proposed — H7 |
| Équipement | Matériel suivi en GMAO (générateur…) | GMAO | `Equipement`, `StatutEquipement` | — | — | `EN_SERVICE / EN_MAINTENANCE / … / REFORME` | proposed |
| Intervention | Opération de maintenance sur un équipement | GMAO | `Intervention`, `StatutIntervention` | — | — | `PLANIFIEE → EN_COURS → TERMINEE / ANNULEE`, rectifiable | proposed |
| Réforme | Retrait définitif d'un équipement | GMAO | `Equipement.reformer` | — | — | — | proposed |
| Centre ⚠ | Établissement de dialyse, unité d'isolement des données | Organisation | `CenterId` | `center` / `centre`, `centers` | Center (anglais) | isolement par centre (RG-TRV-002) | proposed — H13 |
| Société | Entité juridique regroupant des centres | Organisation | `societes` | — | — | un centre peut changer de société | proposed — H13 |
| Direction | Vue de la société sur ses centres, anonymisée | Direction | `Direction*` | — | — | instantané mensuel figé (RG-DIR-090) | proposed |

## Smells relevés (pour l'ACL et le renommage)
- Verbes CRUD à la place d'intentions : `createPatient`/`updatePatient` (admission ? mutation ?), `BilanPreGreffe.changerStatut`, `updateDate`, `updateForfait`, `create/validate/close` (PEC).
- Statut opaque / trompeur : `SeanceStatus.ABSENT` (= exclue de facturation).
- Mélange de langues : `center`/`centre`, `payment`/`règlement`, `Status`/`Statut`, `session`/`séance`.
- Suffixes techniques : 139 classes `*Service/*Manager/*Helper` (summary.md).

## Questions ouvertes
Voir `.ddd/discovery/event-storming.md` § Questions ouvertes (Q1-Q12) ; les termes marqués H1–H13 y sont rattachés.
