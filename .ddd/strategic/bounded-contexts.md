---
status: approved
date: 2026-10-09
sources:
  - .ddd/strategic/subdomains.md
  - .ddd/discovery/event-storming.md (timeline #1-74, hotspots H1-H13, réponses Q1/Q2/Q5/Q9)
  - .ddd/discovery/glossary.md, .ddd/discovery/legacy-analysis.md
validation: frontières proposées ; à valider au gate `strategic`
---

# Bounded contexts

Cible : **monolithe modulaire** sous `backend/contexts/<ctx>` (un module Maven/Gradle par contexte, un schéma de base par contexte à terme), pas de microservice par défaut — ADR [0001](../architecture/adr/0001-monolithe-modulaire-onze-contextes.md).
Sigles : ES = event-storming.md, H = hotspot, C = cluster du scan.

## Matrice sous-domaine ↔ contexte ↔ données

| Contexte (slug) | Sous-domaine | Type | Propriétaire en écriture de | Modules legacy repris |
|---|---|---|---|---|
| Séance (`seance`) | SD1 | Core | séance (acte de soin), volets paramédical/médical, consommables déclarés, dérogation, régularisation | `bc-seance/domain/seance`, `SeanceRestController` |
| Planification (`planification`) | SD2 | Core | place (salle·créneau·générateur·jours), placement, run d'optimisation, roulement et absences d'infirmiers, remplacement | `bc-seance/domain/{planning,infirmier}`, `platform/application/{planning,infirmier}`, Timefold |
| Absences patients (`absences-patients`) | SD3 | Core (à confirmer) | absence de patient, qualification, rattrapage, valorisation, **clôture mensuelle des absences** (nouvelle) | `bc-seance/domain/absence`, `platform/application/absence`, `AbsencePatientScheduler` |
| Patient & couverture (`patient-couverture`) | SD4 | Supporting | patient (identité administrative, état, mouvements), assuré, prise en charge, attestation de droits | `bc-patient`, `bc-assure`, `bc-insurance`, `bc-pec` |
| Facturation (`facturation`) | SD5 | Supporting | séance facturable (projection), exclusion de facturation, facture, payeur/caisse/agence, type de TVA, règlement, **clôture mensuelle de facturation** (nouvelle) | `bc-facturation`, `bc-reglement`, `SeanceFacturationJdbcAdapter` |
| Comptabilité (`comptabilite`) | SD6 | Supporting | plan comptable SCF, mapping, écriture, journal, période comptable, export | `bc-comptabilite`, listeners comptables, `ComptabiliteStockScheduler` |
| Stock (`stock`) | SD7 | Supporting | article (catalogue), lot, bons, PMP, inventaire / clôture de stock | `bc-stock`, `bc-article` |
| Dossier médical (`dossier-medical`) | SD8 | Supporting | ordonnance, prescription, examen, observation, sérologie, allergie, antécédent, anémie/observance, bilan pré-greffe, donneur | `bc-medical`, FHIR |
| GMAO (`gmao`) | SD9 | Supporting | équipement, intervention, plan de maintenance | `bc-gmao` |
| Organisation (`organisation`) | SD10 | Supporting | société, centre, salles, créneaux, paramètres de centre | `bc-referential` (partie organisation) |
| Pilotage direction (`pilotage-direction`) | SD11 | Supporting | instantanés mensuels, alertes de direction (lecture seule ailleurs) | `platform/application/direction` (C8) |
| Génériques : Identité & accès · Notifications · Audit · Licence | SD12-15 | Generic | comptes utilisateurs ; notifications ; journal d'audit ; licence | `platform` (sécurité, `NotificationService`, `Audit*`, `License*`) |

`bc-migration` (import) : outillage hors contexte, utilise les API d'écriture des contextes (pas d'accès direct aux tables en cible).

## Signaux de frontière (≥ 2 par frontière)

| Frontière | Signaux convergents |
|---|---|
| Séance ∣ Facturation | linguistique (H6 : séance acte ≠ séance facturable, décision Q2) ; invariants distincts (validation infirmière vs prix/TVA/payeur figés) ; données (lecture SQL directe `SeanceFacturationJdbcAdapter` = couplage à supprimer) ; statut trompeur `ABSENT` (H1, Q1) |
| Séance ∣ Planification | cluster C1 (cohésion 0.92) coupe `bc-seance` ; cadence différente (planning hebdo/optimisation vs acte quotidien) ; acteurs différents (admin/secrétaire vs infirmier au poste) |
| Séance ∣ Absences patients | homonyme « absence »/`ABSENT` (H1) ; cycle de vie propre (`A_QUALIFIER → …`) ; déclenchement temporel propre (cron 02:30) ; clôture mensuelle propre (Q5) |
| Planification ∣ Absences patients | « absence d'infirmier » ≠ « absence de patient » (H1, glossaire) ; finalités différentes (couverture d'effectif vs perte financière) |
| Séance ∣ Stock | « séance » = simple motif de sortie côté stock (Q2) ; 4 arêtes seulement, 45 co-changements à résorber par événement ; invariants FEFO/PMP propres au stock |
| Facturation ∣ Comptabilité | « compte » polysémique (H4) ; 0 arête d'import ; intégration déjà événementielle (`FacturationValideeEvent`) ; clôtures distinctes (Q5) |
| Patient & couverture ∣ Séance/Planification | trois modèles du statut patient (H5) ; 4 micro-modules cohérents (C5, C25) ; mouvements administratifs vs place de dialyse |
| Dossier médical ∣ reste | 0 arête medical↔patient ; vocabulaire clinique propre (ordonnance, RCP, KDIGO) ; acteur médecin ; données de santé (cloisonnement d'accès) |
| GMAO ∣ reste | Ca 2 / Ce 1, cohésion 0.8 ; « générateur » (place) ≠ « équipement » (GMAO) ; acteur technicien |

## Canvas

### Séance — Core
- **But** : constater et documenter chaque séance de dialyse réalisée au poste.
- **Rôle** : execution.
- **Langage** : séance, validation de séance, signature médicale, régularisation, dérogation hors planning, consommable déclaré, volet paramédical/médical, forfait surchargé.
- **Règles** : `CREE → VALIDEE → SIGNEE`, sans retour (RG-SEA-001) ; validation le jour même sinon déverrouillage admin (RG-SEA-046, 7 j) ; une séance verrouillée pour facturation est immuable (RG-SEA-002) ; création conditionnée par une PEC valide (requête vers Patient & couverture).
- **Reçoit** : `PatientPlacé`/séances programmées (Planification), `FacturationValidée` (verrouille), `PECValidée/Clôturée` (lecture).
- **Émet** : `SéanceCréée`, `SéanceValidée` (= réalisée), `SéanceSignée`, `ConsommablesDeSéanceDéclarés`, `ForfaitDeSéanceSurchargé`, `SéanceDéverrouilléePourRégularisation`.
- **Hypothèses** : le statut `FACTUREE` devient un **verrou** posé sur réception de `FacturationValidée` ; la séance n'a plus de statut `ABSENT` (ADR 0002).
- **Verdict** : équipe cœur ; premier candidat au DDD tactique complet ; `VoletParamedical` (434 l.) à redécouper.

### Planification — Core
- **But** : placer chaque patient sur une place et garantir l'effectif infirmier.
- **Rôle** : specification (produit le planning) + draft (propositions, optimisation).
- **Langage** : place, placement, créneau, salle, générateur (ressource de place), jours de dialyse, optimisation, proposition, roulement, absence d'infirmier, remplaçant, sous-effectif, capacité théorique.
- **Règles** : refus de placement incohérent (RG-PLN-050), isolement (RG-PLN-053), score 0-100 (RG-PLN-030), replanification nocturne optionnelle (RG-PLN-100), sous-effectif à 14 j (RG-INF-046, ratio 4 à confirmer), libération de place après le dernier jour d'occupation (RG-PAT-033).
- **Reçoit** : `PatientAdmis`, `PatientSorti` (Patient & couverture) ; `ÉquipementMisHorsService`/`RemisEnService` (GMAO) ; salles/créneaux (Organisation).
- **Émet** : `PatientPlacé`, `PlaceLibérée`, `PropositionAppliquée`, `AbsenceInfirmierDéclarée`, `RemplaçantAffecté`, `SousEffectifDétecté`.
- **Hypothèses** : la **place** appartient à Planification et non au Patient (ADR 0005).
- **Verdict** : équipe cœur ; solveur Timefold derrière un port.

### Absences patients — Core (à confirmer)
- **But** : ne perdre aucune séance : détecter, qualifier, rattraper et valoriser chaque absence ; clôturer le mois.
- **Rôle** : execution + analysis.
- **Langage** : absence de patient, détection, qualification, motif justifiant, rattrapage, valorisation, clôture mensuelle des absences.
- **Règles** : détection = jour de dialyse prévu sans séance réalisée (7 j glissants, RG-ABS-040) ; refus si séance réalisée (RG-ABS-012) ou période facturée (RG-ABS-024) ; valorisation figée = forfait PEC HT × (1 + TVA) (RG-ABS-030).
- **Reçoit** : jours prévus (Planification), `SéanceValidée` (Séance), forfait PEC (Patient & couverture), taux TVA (Facturation, PL), `FacturationValidée`/`PériodeDeFacturationClôturée` (Facturation).
- **Émet** : `AbsenceDétectée`, `AbsenceQualifiée`, `AbsenceRattrapée`, `AbsenceAnnulée`, `MoisDAbsencesClôturé`.
- **Verdict** : contexte autonome (ADR 0003) ; peut être porté par l'équipe Séance au début.

### Patient & couverture — Supporting
- **But** : tenir l'identité administrative du patient, ses mouvements et ses droits (assuré, PEC, attestation).
- **Rôle** : specification (source de vérité administrative), OHS pour les autres.
- **Langage** : patient, état du patient, mouvement (admission, transfert, décès, greffe, guérison), assuré principal, prise en charge, période demandée/effective, forfait de PEC, attestation de droits.
- **Règles** : un seul assuré principal (RG-PAT 04 l.140) ; état de sortie exige une date (RG-PAT-031) ; PEC `CREE → VALIDEE → CLOTUREE`.
- **Émet** : `PatientAdmis`, `PatientSorti`, `AssuréPrincipalAffecté`, `PECValidée`, `PECClôturée`, `AttestationEnregistrée`.
- **Hypothèses** : fusion de 4 micro-modules (ADR 0004) ; « place » et « mouvement place libérée » quittent ce contexte.

### Facturation — Supporting
- **But** : transformer les séances réalisées couvertes en factures, encaisser les règlements, clôturer le mois de facturation.
- **Rôle** : execution (émission) + gateway vers la comptabilité.
- **Langage** : séance facturable, **exclusion de facturation** (ex-`ABSENT`), simulation, validation de facturation, facture, ligne, forfait appliqué, payeur (caisse, agence, centre payeur), type de TVA, règlement, situation de facture, clôture mensuelle de facturation.
- **Règles** : signature médecin **non** exigée (RG-FAC-020, confirmée Q9) ; priorité forfait surcharge > PEC effective > PEC demandée (RG-FAC-021) ; validation atomique, échoue si une séance n'est plus éligible (RG-FAC-030) ; numéro `FAC-{YEAR}-{SEQ}`.
- **Reçoit** : `SéanceValidée/Signée/ForfaitSurchargé` (projection locale des séances facturables), PEC/attestation (Patient & couverture).
- **Émet** : `FacturationValidée`, `SéanceExclueDeFacturation`, `RèglementEnregistré`, `PériodeDeFacturationClôturée`.
- **Hypothèses** : Règlement reste dans ce contexte (même agrégat facture/situation) ; fin de la lecture SQL de `seances` (ADR 0002).

### Comptabilité — Supporting
- **But** : traduire les faits économiques (ventes, règlements, mouvements de stock) en écritures SCF et les exporter.
- **Rôle** : analysis + gateway (export vers logiciel comptable).
- **Langage** : écriture, journal (VE, BQ, CA, AC, ST), compte comptable, compte client 411, mapping, extourne, pièce, période comptable, clôture de période, export.
- **Règles** : `BROUILLON → VALIDEE → EXPORTEE` (immuable) ; aucune génération dans une période clôturée (RG-CPT-005) ; stock comptabilisé en différentiel rejouable 35 j (RG-CPT-015).
- **Reçoit** (ACL) : `FacturationValidée`, `RèglementEnregistré`, mouvements de stock valorisés.
- **Émet** : `ÉcrituresExportées`, `PériodeComptableClôturée`.

### Stock — Supporting
- **But** : approvisionner, tracer les lots et valoriser le stock.
- **Rôle** : execution ; OHS pour le **catalogue d'articles**.
- **Langage** : article, lot, FEFO, bon de commande/réception/sortie, sortie de séance (motif), PMP, inventaire, clôture d'inventaire, compte d'article.
- **Règles** : sortie FEFO ; PMP recalculé à la réception (RG-STK-024) ; inventaire gèle puis fige les mouvements antérieurs (RG-STK-052/056) ; sortie de séance non annulable si séance facturée (RG-SEA-002).
- **Reçoit** : `ConsommablesDeSéanceDéclarés`/`SéanceValidée`, `FacturationValidée` (verrou des sorties).
- **Émet** : `RéceptionValidée`, `SortieDeSéanceEnregistrée`, `PMPRecalculé`, `InventaireClôturé`, `LotProcheExpiration`.
- **Hypothèses** : Article intégré au Stock (ADR 0006).

### Dossier médical — Supporting
- **But** : documenter le suivi clinique et le parcours greffe.
- **Rôle** : specification clinique.
- **Langage** : ordonnance, prescription, observance, examen, observation biologique, sérologie, allergie, antécédent, traitement anémie, bilan pré-greffe, décision RCP, donneur vivant, statut greffe.
- **Règles** : ordonnance signée = verrouillée ; transitions du bilan pré-greffe à expliciter (H12).
- **Émet** : `OrdonnanceSignée`, `RetardDObservanceConstaté`, `DécisionRCPRendue`, `GreffeRéalisée` (→ Patient & couverture ? H7).
- **Hypothèses** : un seul contexte, greffe = module interne (ADR 0007).

### GMAO — Supporting
- **But** : maintenir les équipements disponibles. **Émet** `ÉquipementMisHorsService`, `ÉquipementRéformé`, `InterventionTerminée`. Langage : équipement, intervention, plan de maintenance, réforme. Premier candidat d'extraction (isolé).

### Organisation — Supporting
- **But** : structure société → centres → salles/créneaux, transfert de centre, paramètres de centre. OHS + Published Language (identifiant de centre). **Émet** `CentreTransféré`, `SalleOuverte/Fermée`.

### Pilotage direction — Supporting
- **But** : vue société anonymisée multi-centres ; instantané mensuel figé ; alertes. Conformiste en lecture des événements publiés ; aucune écriture dans les autres contextes.

### Génériques
Identité & accès (comptes utilisateurs, MFA), Notifications (abonnées aux événements, catalogue RG-NOT), Audit (asynchrone), Licence (contrôle nocturne). Adoptés derrière des ports ; ne portent aucune règle métier.

## Questions ouvertes
1. Placement : acte explicite ou effet de l'optimisation (ES Q11) ? Hypothèse : acte explicite, l'optimisation produit des propositions.
2. La place (salle·créneau·générateur·jours) appartient-elle bien à la Planification et non au dossier administratif du patient ? Hypothèse : oui (ADR 0005).
3. Le règlement reste-t-il avec la facturation (même équipe, même agrégat) ? Hypothèse : oui.
4. Greffe réalisée ⇒ sortie du patient (H7, ES Q7) ? Hypothèse : non automatique, proposition à la secrétaire.
