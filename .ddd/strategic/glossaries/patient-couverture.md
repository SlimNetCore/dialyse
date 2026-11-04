---
status: validated
date: 2026-10-09
context: Patient & couverture (supporting)
sources: [.ddd/discovery/glossary.md, .ddd/discovery/event-storming.md §2]
---
# Glossaire — Patient & couverture

| Terme métier | Définition | Identifiant cible | Nom legacy | Synonymes à bannir | Règle / exemple | Statut |
|---|---|---|---|---|---|---|
| Patient | Personne suivie par un centre, vue sous l'angle administratif | `Patient` | `patients` | client, user | — | proposed |
| Admission | Entrée du patient dans un centre | `Patient.admettre()` / `PatientAdmis` | `createPatient` | création | mouvement `ADMISSION` | proposed |
| État du patient | Situation administrative (permanent, occasionnel, vacancier, transféré, décédé, greffé, guéri) | `EtatPatient` | `etat`, `PatientType` | statut, type | unification avec `PatientType` à décider (H5) | proposed |
| Mouvement de patient | Événement de vie administrative | `MouvementPatient` | `TypeMouvementPatient` | — | « place libérée » sort vers Planification (ADR 0005) | proposed |
| Sortie | Fin de prise en charge dans le centre (transfert, décès, greffe, guérison) | `Patient.declarerSortie()` / `PatientSorti` | `updatePatient` | — | date d'évènement exigée (RG-PAT-031) | proposed |
| Assuré | Titulaire des droits d'assurance rattaché au patient | `Assure` | `bc-assure` | — | un seul assuré principal à la fois | proposed |
| Prise en charge (PEC) | Accord d'un payeur couvrant une période et un forfait | `PriseEnCharge` | `PriseEnCharge`, `PecStatus` | — | `CREE → VALIDEE → CLOTUREE` ; période demandée vs effective | proposed |
| Forfait de PEC | Prix HT de la séance prévu par la PEC | `ForfaitPec` | `forfaitId` | tarif | — | proposed |
| Attestation de droits | Justificatif de droits du patient | `AttestationDeDroits` | `bc-insurance`, `ATTESTATION_DROITS` | — | absente ⇒ cause de non-facturation | proposed |

Homonymes : *Greffe* (état de sortie, ici) ≠ *Greffe réalisée* (statut de bilan, Dossier médical).

## Termes ajoutés par la reprise PC1 (2026-11-03, statut `proposed`, ADR 0076)

Le code et l'API gardent le vocabulaire du **legacy** ; le glossaire donne le terme métier. Identifiants du code : `EtatDuPatient` (valeurs `PERMANENT`, `OCCASIONNEL`, `VACANCIER_LOCAL`, `VACANCIER_ETRANGER`, `TRANSFERE`, `DECEDE`, `GREFFE`, `GUERRI`), `TypeDeMouvement` (`ADMISSION`, `SEJOUR_TEMPORAIRE`, `REPRISE`, `TRANSFERT`, `DECES`, `GREFFE`, `GUERISON`, `PLACE_LIBEREE`).

| Terme métier | Définition | Identifiant du code | Notes |
|---|---|---|---|
| Fiche du patient | Contenu administratif saisi d'un patient (état civil, coordonnées, suivi, couverture, affectation) | `Fiche` | `Patient.admettre`, `Patient.modifier` |
| Transféré | Patient parti vers un autre centre | `TRANSFERE`, mouvement `TRANSFERT` | état de sortie |
| Guéri | Patient sorti pour guérison | `GUERRI`, mouvement `GUERISON` | état de sortie |
| Séjour limité | Occasionnel ou vacancier : l'évènement est la fin de séjour | `OCCASIONNEL`, `VACANCIER_*` | date de fin obligatoire |
| Reprise | Retour à l'état permanent | `REPRISE` | datée du jour du centre sans date |
| Date d'évènement | Date de sortie ou de fin de séjour | `dateEvenement` (colonne `date_evenement_etat`) | jamais avant l'admission |
| Numéro d'assurance | Identifiant d'assurance du patient, unique par centre, normalisé | `NumeroAssurance` | espaces de bord retirés, majuscules |
| Garde de centre | Toute lecture et toute écriture se font dans le centre du jeton | `SessionCourante` | 403 sinon |
| Jour du centre | « Aujourd'hui » au fuseau du centre | `HorlogeDesCentres` | clé `app.planification.fuseau-par-defaut` |

## Termes ajoutés par la reprise PC2 et PC3 (2026-11-03, statut `proposed`, ADR 0077)

| Terme métier | Définition | Identifiant du code | Notes |
|---|---|---|---|
| Assuré principal | Assuré actif désigné comme principal du patient ; un seul à la fois (R1) | règles `AssurePrincipal` (règles sans état, pas un agrégat), colonne `is_primary` | tenu sous le verrou du centre ; agrégat `AffectationsDuPatient` = cible |
| Affectation d'assuré | Lien daté entre un patient et un assuré | `Affectation`, table `assure_patient` | active tant que sans date de fin |
| Assuré d'un autre centre | Assuré détenu par un autre centre : ne peut pas être capturé | code `ASSURE_D_UN_AUTRE_CENTRE` | 422 |
| Demande de prise en charge | Période et forfait demandés à la création de la PEC | `dateDebutDemande`, `dateFinDemande`, `forfaitDemandeId` | saisie aussi sur la fiche |
| Prise en charge accordée | Période et forfait effectifs fixés à la validation | `dateDebutEffectif`, `dateFinEffectif`, `forfaitEffectifId` | aucun contrôle de période ni de forfait (à décider) |
| Statut de prise en charge | `CREE`, `VALIDEE`, `CLOTUREE` | `StatutPec` | tout autre : refusé |
| Identifiant de PEC déjà utilisé | Demande de la fiche dont l'identifiant de PEC appartient à une autre PEC | code `PEC_IDENTIFIANT_DEJA_UTILISE` | fiche enregistrée, PEC non créée, 409 |
| PEC valide pour une séance | Deux règles aujourd'hui : `session-allowed` (statut + attestation du non-vacancier) et `SeanceBillingEligibilityPort` | `GererLesPec.autoriseUneSeance` | à décider avec les métiers |
